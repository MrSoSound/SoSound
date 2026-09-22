package com.sosound.app.data.download

import android.content.Context
import android.content.Intent
import android.os.Build
import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.library.TrackDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.sosound.app.data.library.TrackEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/** Come sta andando un brano messo in coda. */
data class QueueItem(
    val track: CatalogTrack,
    val progress: Float = 0f,
    val state: State = State.IN_ATTESA,
    val error: String? = null,
    /** Messaggio di servizio mostrato sotto la barra, tipo
     *  «aggiorno yt-dlp e riprovo». Sparisce quando cambia stato. */
    val note: String? = null,
    /**
     * Se il brano, una volta arrivato, entra in libreria per sempre o
     * resta solo in cache.
     *
     * Vero per un download chiesto apposta (il tasto «+», «Scarica e
     * basta»): è una scelta esplicita di tenerlo. Falso per un brano
     * messo in coda di riproduzione ma non ancora tuo — lì lo si scarica
     * solo per non far aspettare chi ascolta quando arriva il suo turno,
     * non perché qualcuno abbia chiesto di conservarlo: deve restare in
     * cache, sfollabile come qualunque altro brano solo ascoltato.
     */
    val salvato: Boolean = true,
) {
    enum class State { IN_ATTESA, IN_CORSO, AGGIORNO, FATTO, ERRORE }
}

/**
 * La coda dei download, una sola per tutta l'app.
 *
 * Sequenziale: un brano per volta. Non e' una limitazione tecnica, e'
 * la stessa scelta che avevo fatto lato server - e' il ritmo di una
 * persona che aggiunge canzoni, non di un raccoglitore.
 */
class DownloadQueue(
    private val context: Context,
    private val downloader: Downloader,
    private val dao: TrackDao,
    private val policy: EngineUpdatePolicy = EngineUpdatePolicy(context),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<CatalogTrack>(Channel.UNLIMITED)

    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items

    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady

    /**
     * Un brano appena scaricato che l'utente aveva toccato per
     * ascoltarlo. Chi ascolta questo flusso lo fa partire.
     */
    private val _readyToPlay = MutableSharedFlow<TrackEntity>(extraBufferCapacity = 4)
    val readyToPlay: SharedFlow<TrackEntity> = _readyToPlay

    /**
     * Ogni brano che finisce di scaricare, a prescindere dal motivo.
     * Serve all'import, che deve infilarlo nella playlist solo dopo che
     * esiste davvero in libreria — prima la chiave esterna lo rifiuta.
     */
    private val _completed = MutableSharedFlow<TrackEntity>(extraBufferCapacity = 64)
    val completed: SharedFlow<TrackEntity> = _completed

    /**
     * Quale brano deve partire da solo quando finisce.
     *
     * Uno solo, l'ultimo toccato: se tocchi tre canzoni di fila ti
     * aspetti di sentire quella che hai toccato per ultima, non un
     * concerto casuale di tutte e tre man mano che arrivano.
     */
    @Volatile private var autoPlayId: String? = null

    init {
        scope.launch {
            // L'inizializzazione di Python e' la prima cosa: finche' non
            // e' finita, un download fallirebbe con un errore oscuro.
            downloader.initialize().onSuccess { _engineReady.value = true }

            // Poi si aggiorna yt-dlp, una volta al giorno. Dopo aver
            // dichiarato «pronto», non prima: l'aggiornamento richiede
            // rete e qualche secondo, e bloccare l'app all'avvio per
            // questo sarebbe una tassa su ogni apertura.
            if (_engineReady.value && policy.shouldUpdateOnStart()) {
                policy.markStartUpdate()
                downloader.updateEngine()
            }

            for (track in channel) process(track)
        }
    }

    /**
     * Mette un brano in coda.
     *
     * Con [playWhenReady] il brano parte da solo appena finito di
     * scaricare: e' quello che succede toccando un risultato di ricerca.
     */
    fun enqueue(track: CatalogTrack, playWhenReady: Boolean = false, salvato: Boolean = true) {
        if (playWhenReady) autoPlayId = track.videoId

        val existing = _items.value.firstOrNull { it.track.videoId == track.videoId }
        // Già in coda o in corso: non si accoda due volte. Se invece era
        // fallito, riaccodarlo è esattamente quello che si vuole.
        //
        // Un'eccezione: se era in coda solo per la cache e ora si chiede
        // di tenerlo per davvero, la richiesta esplicita vince — non ha
        // senso restare in silenzio su un salvataggio appena chiesto solo
        // perché il download era già partito per un altro motivo.
        if (existing != null && existing.state != QueueItem.State.ERRORE) {
            if (salvato && !existing.salvato) {
                _items.value = _items.value.map {
                    if (it.track.videoId == track.videoId) it.copy(salvato = true) else it
                }
            }
            return
        }

        _items.value = _items.value.filterNot { it.track.videoId == track.videoId } +
            QueueItem(track, salvato = salvato)
        startService()
        scope.launch { channel.send(track) }
    }

    /**
     * Scarica un brano, e se fallisce per un motore disallineato prova
     * una volta ad aggiornare yt-dlp e riparte.
     *
     * `allowRetry` e' il terzo freno contro il ciclo: la chiamata
     * ricorsiva lo mette a false, quindi il ritentativo e' uno solo per
     * brano, qualunque cosa succeda.
     */
    private suspend fun process(track: CatalogTrack, allowRetry: Boolean = true) {
        update(track.videoId) {
            it.copy(state = QueueItem.State.IN_CORSO, error = null, note = null)
        }
        try {
            val entity = downloader.download(track) { p ->
                update(track.videoId) { it.copy(progress = p) }
            }
            // Il salvataggio vero si legge adesso, non a inizio funzione:
            // nel frattempo qualcuno potrebbe aver chiesto di tenerlo per
            // davvero (vedi enqueue) mentre il download era gia' in corso.
            val salvato = _items.value.firstOrNull { it.track.videoId == track.videoId }
                ?.salvato ?: true
            // La data di aggiunta e' quella di quando il brano e'
            // entrato in libreria, non di quando ne e' arrivato il file:
            // riscaricandolo risaliva in cima a «Brani» come se fosse
            // nuovo.
            val prima = dao.byId(track.videoId)
            dao.upsert(
                entity.copy(
                    addedAt = if (prima != null && prima.addedAt > 0) prima.addedAt else entity.addedAt,
                    // Un riferimento gia' salvato non torna cache solo
                    // perche' qualcosa lo riscarica: si sale a "salvato",
                    // non si scende.
                    salvato = salvato || prima?.salvato == true,
                )
            )
            update(track.videoId) { it.copy(state = QueueItem.State.FATTO, progress = 1f) }

            _completed.tryEmit(entity)
            if (autoPlayId == track.videoId) {
                autoPlayId = null
                _readyToPlay.tryEmit(entity)
            }
            // Le voci finite spariscono dopo qualche secondo: la coda mostra
            // cosa sta succedendo adesso, non un registro storico.
            scope.launch {
                kotlinx.coroutines.delay(4000)
                _items.value = _items.value.filterNot {
                    it.track.videoId == track.videoId && it.state == QueueItem.State.FATTO
                }
                stopServiceIfIdle()
            }
            return
        } catch (e: DownloadError) {
            if (allowRetry && e.worthUpdating && policy.shouldTryUpdate()) {
                if (autoUpdateAndRetry(track)) return
            }
            fail(track.videoId, e.message ?: "Non riuscito")
        } catch (e: Exception) {
            fail(track.videoId, e.message ?: "Non riuscito")
        }
        stopServiceIfIdle()
    }

    /**
     * Aggiorna yt-dlp e riprova il brano. Torna true se ci ha provato
     * davvero (a prescindere dall'esito del secondo tentativo).
     */
    private suspend fun autoUpdateAndRetry(track: CatalogTrack): Boolean {
        policy.markAttempt()   // prima del tentativo: se fallisce, non si ripete
        update(track.videoId) {
            it.copy(
                state = QueueItem.State.AGGIORNO,
                progress = 0f,
                note = "Aggiorno yt-dlp e riprovo…",
            )
        }

        val updated = downloader.updateEngine()
        if (updated.isFailure) {
            fail(
                track.videoId,
                "Download non riuscito, e nemmeno l'aggiornamento di yt-dlp. " +
                    "Controlla la connessione.",
            )
            return true
        }

        update(track.videoId) { it.copy(note = updated.getOrNull()) }
        process(track, allowRetry = false)
        return true
    }

    private fun fail(videoId: String, message: String) {
        update(videoId) {
            it.copy(state = QueueItem.State.ERRORE, error = message, note = null)
        }
    }

    fun dismiss(videoId: String) {
        if (autoPlayId == videoId) autoPlayId = null
        _items.value = _items.value.filterNot { it.track.videoId == videoId }
        stopServiceIfIdle()
    }

    /**
     * Riprova un brano fallito.
     *
     * Rimette in coda da capo, compreso il giro sui client alternativi:
     * un 403 e' spesso transitorio, e al secondo tentativo passa.
     */
    fun retry(videoId: String) {
        val voce = _items.value.firstOrNull { it.track.videoId == videoId } ?: return
        _items.value = _items.value.map {
            if (it.track.videoId == videoId)
                it.copy(state = QueueItem.State.IN_ATTESA, error = null, note = null, progress = 0f)
            else it
        }
        startService()
        scope.launch { channel.send(voce.track) }
    }

    /** Se un brano e' in coda proprio per essere ascoltato. */
    fun isWaitingToPlay(videoId: String) = autoPlayId == videoId

    private fun update(videoId: String, block: (QueueItem) -> QueueItem) {
        _items.value = _items.value.map {
            if (it.track.videoId == videoId) block(it) else it
        }
    }

    /** Quando l'utente aggiorna a mano, il freno automatico si azzera. */
    fun onManualUpdate() = policy.reset()

    private val hasWork: Boolean
        get() = _items.value.any {
            it.state == QueueItem.State.IN_ATTESA ||
                it.state == QueueItem.State.IN_CORSO ||
                it.state == QueueItem.State.AGGIORNO
        }

    private fun startService() {
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopServiceIfIdle() {
        if (!hasWork) context.stopService(Intent(context, DownloadService::class.java))
    }
}
