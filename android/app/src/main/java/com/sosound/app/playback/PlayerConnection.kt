package com.sosound.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sosound.app.data.library.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Quel che serve all'interfaccia per disegnare il player. */
data class PlayerState(
    val current: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** Posizione del brano in ascolto dentro la coda. */
    val index: Int = 0,
    val shuffle: Boolean = false,
    /** 0 = niente, 1 = ripeti il brano, 2 = ripeti la coda. */
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Il ponte fra l'interfaccia e il servizio di riproduzione.
 *
 * L'interfaccia non tocca mai ExoPlayer: parla con un MediaController, che
 * e' un telecomando verso il servizio. E' questo che permette alla musica
 * di continuare quando l'Activity viene distrutta - il player non vive
 * dentro la schermata.
 *
 * La coda vive in due posti che vanno tenuti allineati: dentro il player
 * (che ne ha bisogno per suonare) e in [queue] (che serve all'interfaccia
 * per disegnarla). Ogni modifica va applicata a entrambi nello stesso
 * ordine, ed e' il motivo per cui passano tutte da qui.
 */
@UnstableApi
class PlayerConnection(private val context: Context) {

    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _queue = MutableStateFlow<List<TrackEntity>>(emptyList())
    /** La coda, nell'ordine in cui verra' suonata. */
    val queue: StateFlow<List<TrackEntity>> = _queue

    private val _errore = MutableStateFlow<String?>(null)
    /**
     * L'ultimo guaio di riproduzione, in parole.
     *
     * Finche' i file erano tutti sul telefono un errore era quasi
     * impossibile e nessuno lo guardava. Da quando un brano puo' arrivare
     * dalla rete il fallimento e' normale — in metropolitana, senza
     * campo, con un indirizzo scaduto — e quello che si vedeva era la
     * musica che si fermava e basta.
     */
    val errore: StateFlow<String?> = _errore

    fun scartaErrore() { _errore.value = null }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        override fun onPlayerError(error: PlaybackException) {
            _errore.value = spiega(error)
        }
    }

    /**
     * Da un codice di errore a una frase che dice cosa fare.
     *
     * I codici di Media3 sono precisi e inutili da mostrare: quello che
     * serve sapere e' se dipende dalla rete, dal file o da YouTube,
     * perche' sono tre cose che si risolvono in tre modi diversi.
     */
    private fun spiega(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Senza rete non riesco a sentire questo brano. Tienilo anche " +
                "senza rete e resta sul telefono."

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Il file di questo brano non c'è più. Riscaricalo dalla sua scheda."

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
            "YouTube ha rifiutato la richiesta. Di solito basta riprovare; " +
                "se continua, aggiorna yt-dlp dalle impostazioni."

        PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
            "Non riesco a raggiungere questo brano. Se sei senza rete è " +
                "normale: i brani tenuti offline funzionano lo stesso."

        else -> "Questo brano non parte (${e.errorCodeName})."
    }

    fun connect(onReady: () -> Unit = {}) {
        if (controller != null) return onReady()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()?.apply { addListener(listener) }
            refresh()
            onReady()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun refresh() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        _state.value = PlayerState(
            current = _queue.value.getOrNull(index),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            index = index,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
        )
    }

    /**
     * Rimette in fila un brano che nel frattempo e' cambiato.
     *
     * Serve alla copertina di un brano ascoltato dalla ricerca:
     * l'immagine arriva dalla rete qualche istante dopo che la musica e'
     * partita, e la coda era gia' stata costruita senza. Senza questo,
     * il player restava con il riquadro vuoto fino al brano successivo.
     */
    fun aggiorna(t: TrackEntity) {
        val i = _queue.value.indexOfFirst { it.videoId == t.videoId }
        if (i < 0) return
        _queue.value = _queue.value.toMutableList().apply { set(i, t) }
        controller?.replaceMediaItem(i, t.toMediaItem())
        refresh()
    }

    /**
     * Rimette la coda dov'era, in pausa.
     *
     * `prepare` e non `play`: ritrovare la coda di stamattina e' comodo,
     * ritrovarsi la musica che parte da sola aprendo l'app non lo e'.
     */
    fun ripristina(tracks: List<TrackEntity>, indice: Int, posizioneMs: Long) {
        val c = controller ?: return
        if (tracks.isEmpty() || c.mediaItemCount > 0) return
        _queue.value = tracks
        c.setMediaItems(
            tracks.map { it.toMediaItem() },
            indice.coerceIn(0, tracks.size - 1),
            posizioneMs.coerceAtLeast(0),
        )
        c.prepare()
        refresh()
    }

    /** Fa avanzare la barra di posizione mentre suona. */
    fun tick() = refresh()

    // ------------------------------------------------------------ la coda

    /**
     * Un brano puo' stare nello spazio privato (percorso) oppure in una
     * cartella scelta dall'utente (content://). ExoPlayer legge
     * entrambi, ma vanno costruiti in modo diverso.
     */
    private fun locationToUri(location: String): Uri =
        if (location.startsWith("content://")) Uri.parse(location)
        else File(location).toUri()

    /**
     * L'indirizzo da cui suonare un brano.
     *
     * Un brano senza file non e' un errore: e' un brano che si ascolta
     * dalla rete. Gli si da' un indirizzo nostro, e chi legge lo
     * risolvera' al momento del bisogno — non prima, e non
     * sull'interfaccia.
     */
    private fun TrackEntity.indirizzo(): Uri =
        if (path.isBlank()) Uri.parse(com.sosound.app.data.stream.Flusso.uriDi(videoId))
        else locationToUri(path)

    private fun TrackEntity.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(videoId)
            .setUri(indirizzo())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    // La copertina finisce sulla schermata di blocco.
                    .setArtworkUri(coverPath?.let { locationToUri(it) })
                    .build()
            )
            .build()

    /**
     * Sostituisce la coda e fa partire dal brano scelto.
     *
     * Carica tutta la lista e non solo il brano toccato: cosi'
     * «successivo» funziona, e il tasto degli auricolari salta al brano
     * dopo invece di non fare niente.
     */
    fun play(tracks: List<TrackEntity>, startIndex: Int) {
        val c = controller ?: return
        _queue.value = tracks
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    /**
     * Mette un brano subito dopo quello in ascolto.
     *
     * Se non sta suonando niente equivale a farlo partire: accodare a una
     * coda vuota e non sentire nulla sarebbe una risposta muta a un gesto
     * esplicito.
     */
    fun playNext(track: TrackEntity) {
        val c = controller ?: return
        if (_queue.value.isEmpty()) return play(listOf(track), 0)

        val at = (c.currentMediaItemIndex + 1).coerceIn(0, _queue.value.size)
        _queue.value = _queue.value.toMutableList().apply { add(at, track) }
        c.addMediaItem(at, track.toMediaItem())
        refresh()
    }

    /** Accoda in fondo. */
    fun addToQueue(track: TrackEntity) {
        val c = controller ?: return
        if (_queue.value.isEmpty()) return play(listOf(track), 0)

        _queue.value = _queue.value + track
        c.addMediaItem(track.toMediaItem())
        refresh()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return

        // Togliere l'ultimo rimasto vuol dire fermare tutto: lasciare il
        // player con zero elementi lo manda in uno stato senza senso.
        if (_queue.value.size == 1) return clearQueue()

        _queue.value = _queue.value.toMutableList().apply { removeAt(index) }
        c.removeMediaItem(index)
        refresh()
    }

    /**
     * Sposta un brano dentro la coda.
     *
     * L'indice del brano in ascolto si aggiusta da solo: ci pensa
     * ExoPlayer, che sa quale elemento sta suonando e lo segue anche se
     * cambia posto.
     *
     * ⚠️ `add(to, removeAt(from))` deve dare lo stesso risultato di
     * `moveMediaItem(from, to)`, altrimenti la lista che si vede e la
     * coda che suona divergono e parte la canzone sbagliata. Verificato
     * sul bytecode di ExoPlayerImpl: `newIndex` viene limitato a
     * `size - count`, cioe' e' un indice nella lista *dopo* la rimozione
     * — esattamente la semantica di removeAt seguito da add.
     */
    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        val lista = _queue.value
        if (from !in lista.indices || to !in lista.indices || from == to) return

        _queue.value = lista.toMutableList().apply { add(to, removeAt(from)) }
        c.moveMediaItem(from, to)
        refresh()
    }

    /** Salta a un brano preciso della coda. */
    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun clearQueue() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        _queue.value = emptyList()
        refresh()
    }

    // --------------------------------------------------------- i comandi

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() {
        val c = controller ?: return
        // Comportamento atteso da qualunque player: il primo tocco torna
        // all'inizio del brano, il secondo va davvero a quello prima.
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) = controller?.seekTo(ms)

    /**
     * Riproduzione casuale.
     *
     * Si usa quella di ExoPlayer invece di mescolare la lista: cosi'
     * spegnendola si torna all'ordine vero, che mescolando davvero
     * sarebbe perso per sempre. La coda che si vede resta nell'ordine
     * originale, ed e' giusto — e' l'ordine, non la sequenza di ascolto.
     */
    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        refresh()
    }

    /** Gira fra: niente → ripeti la coda → ripeti il brano → niente. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        refresh()
    }

    /**
     * Fa partire una lista in ordine casuale.
     *
     * Il primo brano si sceglie a caso e si accende il mescolamento: far
     * partire sempre dal primo e poi mescolare il resto vorrebbe dire
     * sentire la stessa canzone d'apertura ogni volta.
     */
    fun shufflePlay(tracks: List<TrackEntity>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.shuffleModeEnabled = true
        play(tracks, tracks.indices.random())
    }

    /** Toglie dalla coda un brano cancellato dalla libreria. */
    fun forget(videoId: String) {
        val index = _queue.value.indexOfFirst { it.videoId == videoId }
        if (index >= 0) removeFromQueue(index)
    }
}
