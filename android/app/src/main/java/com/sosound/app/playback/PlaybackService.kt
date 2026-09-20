package com.sosound.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.common.util.concurrent.Futures
import com.sosound.app.SoSoundApp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.sosound.app.cast.CastManager
import com.sosound.app.cast.LocalMediaServer
import com.sosound.app.cast.dlna.DlnaNetwork
import com.sosound.app.cast.dlna.DlnaPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.sosound.app.MainActivity

/**
 * Il servizio che tiene in vita la riproduzione.
 *
 * E' il motivo per cui questa e' un'app nativa e non una pagina web: un
 * MediaSessionService gira in primo piano con una notifica, e Android non
 * lo sospende quando spegni lo schermo o cambi applicazione. La sessione
 * media porta in dote i controlli sulla schermata di blocco e i tasti
 * degli auricolari Bluetooth, senza una riga di codice in piu'.
 *
 * Adesso che i file stanno sul telefono, il player legge da disco: niente
 * sorgenti HTTP, niente cache, niente da configurare. Il che vuol dire
 * anche che la riproduzione non puo' fallire per colpa della rete.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var focus: AudioFocusHandler? = null

    private var locale: ExoPlayer? = null
    private var cast: CastPlayer? = null
    private var server: LocalMediaServer? = null
    private var dlna: DlnaPlayer? = null
    private lateinit var castManager: CastManager
    private val reteDlna by lazy { DlnaNetwork(this) }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            // Da dove leggere: il disco per quello che si ha, la rete
            // con la cache davanti per quello che si ascolta e basta.
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    SorgenteAudio.Factory(this)
                )
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // false: il fuoco audio ce lo gestiamo noi in
                // AudioFocusHandler. Lasciandolo a ExoPlayer, Android
                // abbassava il volume da solo sotto le telefonate invece
                // di farci mettere in pausa — e senza nemmeno avvisarci.
                false,
            )
            // Se stacchi gli auricolari la musica si ferma invece di
            // continuare a tutto volume dall'altoparlante.
            .setHandleAudioBecomingNoisy(true)
            // Non e' piu' tutto locale: un brano in streaming arriva
            // dalla rete anche a schermo spento, e con WAKE_MODE_LOCAL
            // il Wi-Fi si addormenta a meta' canzone.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        // Sveglia yt-dlp anche quando parte solo il servizio.
        //
        // L'inizializzazione di Python avviene dentro la coda degli
        // scaricamenti, che nasce quando la interroga l'interfaccia. Ma
        // il servizio puo' ripartire da solo — tasto degli auricolari,
        // notifica, ripresa in macchina — e in quel caso nessuno l'aveva
        // svegliata: un brano da ascoltare dalla rete non sarebbe
        // partito, e il motivo non si sarebbe visto da nessuna parte.
        (application as SoSoundApp).downloadQueue
        seguiGliScaricamenti()

        // La posizione si scrive ogni dieci secondi mentre suona.
        //
        // Solo ai cambi di brano non basterebbe: chi ascolta un'ora di
        // podcast e chiude l'app si ritroverebbe all'inizio della
        // puntata. E dieci secondi e' la peggior perdita possibile, che
        // non si nota.
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                if (session?.player?.isPlaying == true) salvaCoda()
            }
        }

        val focusHandler = AudioFocusHandler(this, player)
        focus = focusHandler

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                preparaIlProssimo()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    // Se il sistema nega il fuoco — succede durante una
                    // chiamata in corso — non si parte in silenzio sotto
                    // la voce di qualcun altro.
                    if (!focusHandler.acquire()) player.pause()
                } else {
                    focusHandler.onUserPaused()
                }
            }
        })

        locale = player

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            // Gli elementi della coda arrivano dall'interfaccia con
            // l'indirizzo locale del file. Se si sta trasmettendo vanno
            // riscritti: il ricevitore non puo' leggere il disco del
            // telefono. È il punto giusto per farlo — l'interfaccia non
            // deve sapere dove sta suonando.
            .setCallback(object : MediaSession.Callback {

                /**
                 * Il tasto «play» degli auricolari quando non sta suonando niente.
                 *
                 * ## Cosa decide Android, e cosa decidiamo noi
                 *
                 * A chi mandare quel tasto lo sceglie il sistema, e
                 * sceglie **l'ultima app che ha suonato**. Quella
                 * memoria non e' nostra e non si puo' rivendicare: si
                 * diventa l'ultima app che ha suonato suonando.
                 *
                 * Quello che dipendeva da noi e' un'altra cosa, e
                 * mancava: per essere un'app che il sistema puo'
                 * **riprendere** da ferma bisogna saper rispondere alla
                 * domanda «cosa suoneresti adesso?». Senza questa
                 * risposta SoSound non era nemmeno candidabile, e il
                 * tasto finiva per forza a qualcun altro.
                 *
                 * La risposta e' la coda di ieri, che ormai sappiamo
                 * dov'era.
                 */
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val futuro = com.google.common.util.concurrent.SettableFuture
                        .create<MediaSession.MediaItemsWithStartPosition>()
                    scope.launch {
                        val esito = runCatching { codaDiIeri() }.getOrNull()
                        if (esito == null) futuro.setException(
                            IllegalStateException("nessuna coda da riprendere")
                        ) else futuro.set(esito)
                    }
                    return futuro
                }

                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long,
                ) = Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        mediaItems.map { perDestinazione(it) }, startIndex, startPositionMs,
                    )
                )

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ) = Futures.immediateFuture(mediaItems.map { perDestinazione(it) }.toMutableList())
            })
            .build()

        avviaCast()
    }

    /**
     * Riscrive un elemento della coda secondo dove sta suonando.
     *
     * In locale resta com'e'. Trasmettendo, l'indirizzo diventa quello
     * del server a bordo, e va dichiarato anche il tipo: il ricevitore
     * decide da quello se sa riprodurlo, e senza rifiuta tutto.
     */
    private fun perDestinazione(item: MediaItem): MediaItem {
        if (session?.player !is CastPlayer) return item
        // Un brano senza file non si puo' trasmettere: il ricevitore
        // scarica da un indirizzo servito dal telefono, e di quel brano
        // il telefono non ha niente da servire. Viene lasciato com'e' e
        // la coda lo salta — l'avviso lo da' chi accende la
        // trasmissione, che e' il momento in cui si puo' fare qualcosa.
        val posizione = posizioneDi(item.mediaId)?.takeIf { it.isNotBlank() } ?: return item
        val url = server?.urlDi(item.mediaId) ?: return item
        return item.buildUpon()
            .setUri(url)
            // Il tipo si ricava dal file vero e non dall'URL: l'indirizzo
            // del server non ha estensione, e senza tipo il ricevitore
            // rifiuta tutto.
            .setMimeType(LocalMediaServer.mimeDi(posizione))
            .build()
    }


    // ------------------------------------------ trasmettere tutta la coda

    /**
     * Prepara il brano dopo mentre suona quello prima.
     *
     * ## Perche' non basta togliere i brani senza file
     *
     * Toglierli e' onesto ma rinunciatario: una coda di venti canzoni
     * ascoltate al volo diventa una coda vuota, e trasmettere non serve
     * piu' a niente. L'obiettivo e' mandare **tutta** la coda, e il
     * tempo per farlo c'e': mentre suona un brano di tre minuti, il
     * successivo si scarica in pochi secondi.
     *
     * ## Cosa succede se non ci riesce
     *
     * Il brano esce dalla coda e si passa a quello dopo. Un indirizzo
     * che non risponde fermerebbe la trasmissione li', senza
     * spiegazioni: meglio una canzone in meno che il silenzio.
     */
    /**
     * Sta addosso al lettore che comanda adesso.
     *
     * Il cambio di brano va seguito su CHI sta suonando: durante una
     * trasmissione il lettore locale e' fermo, e un ascoltatore attaccato
     * solo a lui non sentirebbe mai passare una canzone — che e'
     * esattamente quando serve preparare la successiva.
     */
    private val cambioBrano = object : Player.Listener {
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            preparaIlProssimo()
            salvaCoda()
        }
    }

    /**
     * Scrive dov'eri arrivato.
     *
     * Lo fa il servizio e non l'interfaccia perche' e' lui a restare in
     * piedi: la musica va avanti a schermo spento, e il punto giusto e'
     * quello dell'ultimo secondo suonato, non dell'ultima volta che
     * qualcuno ha guardato.
     */
    /**
     * La coda salvata, pronta da riprendere.
     *
     * I brani si rileggono dal database: quello che nel frattempo e'
     * stato cancellato semplicemente non c'e' piu', e la coda si
     * accorcia invece di puntare al vuoto.
     */
    private suspend fun codaDiIeri(): MediaSession.MediaItemsWithStartPosition? {
        val salvata = CodaSalvata.leggi(this) ?: return null
        val dao = (application as SoSoundApp).database.tracks()
        val brani = salvata.brani.mapNotNull { dao.byId(it) }
        if (brani.isEmpty()) return null
        val indice = CodaSalvata.indiceDopoLaPotatura(
            salvati = salvata.brani,
            sopravvissuti = brani.map { it.videoId }.toSet(),
            indice = salvata.indice,
        )
        return MediaSession.MediaItemsWithStartPosition(
            brani.map { perIlTelefono(mediaItemDi(it)) },
            indice,
            salvata.posizioneMs,
        )
    }

    /** Da un brano del database all'elemento che il lettore capisce. */
    private fun mediaItemDi(t: com.sosound.app.data.library.TrackEntity): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.videoId)
            .setUri(
                if (t.haFile) {
                    if (t.path.startsWith("content://")) android.net.Uri.parse(t.path)
                    else android.net.Uri.fromFile(java.io.File(t.path))
                } else android.net.Uri.parse(com.sosound.app.data.stream.Flusso.uriDi(t.videoId))
            )
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.coverPath?.let { android.net.Uri.fromFile(java.io.File(it)) })
                    .build()
            )
            .build()

    private fun salvaCoda() {
        val p = session?.player ?: return
        val brani = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
        CodaSalvata.salva(this, brani, p.currentMediaItemIndex, p.currentPosition)
    }

    private fun preparaIlProssimo() {
        if (!staTrasmettendo()) return
        val p = session?.player ?: return
        val prossimo = (p.currentMediaItemIndex + 1)
            .takeIf { it < p.mediaItemCount }
            ?.let { p.getMediaItemAt(it) } ?: return
        if (posizioneDi(prossimo.mediaId)?.isNotBlank() == true) return
        scarica(prossimo.mediaId)
    }

    private fun staTrasmettendo(): Boolean {
        val p = session?.player
        return p is CastPlayer || p is DlnaPlayer
    }

    /** Mette in coda lo scaricamento di un brano che ne e' sprovvisto. */
    private fun scarica(videoId: String) {
        if (videoId in inArrivo) return
        val app = application as SoSoundApp
        scope.launch {
            val t = app.database.tracks().byId(videoId) ?: return@launch
            if (t.haFile) return@launch
            inArrivo += videoId
            app.downloadQueue.enqueue(
                com.sosound.app.data.catalog.CatalogTrack(
                    videoId = t.videoId,
                    title = t.title,
                    artist = t.artist,
                    album = t.album,
                    durationSeconds = t.durationSeconds,
                    showId = t.showId,
                )
            )
        }
    }

    /**
     * Rimette in piedi l'elemento appena scaricato, o lo toglie.
     *
     * L'indirizzo di un brano nella coda della TV viene deciso quando ci
     * si entra: se allora il file non c'era, li' dentro e' rimasto un
     * indirizzo che non risponde, e va sostituito adesso che il file
     * c'e'.
     */
    private fun aggiorna(videoId: String, riuscito: Boolean) {
        inArrivo -= videoId
        if (!staTrasmettendo()) return
        val p = session?.player ?: return
        val posto = (0 until p.mediaItemCount)
            .firstOrNull { p.getMediaItemAt(it).mediaId == videoId } ?: return

        // Il brano in riproduzione non si tocca: sostituirlo lo farebbe
        // ripartire da capo sull'apparecchio.
        if (posto == p.currentMediaItemIndex) return

        if (riuscito) {
            val nuovo = perDestinazione(p.getMediaItemAt(posto))
            p.replaceMediaItem(posto, nuovo)
        } else {
            p.removeMediaItem(posto)
            avvisa("Un brano non si e' potuto scaricare ed e' uscito dalla coda.")
        }
    }

    private val inArrivo = mutableSetOf<String>()

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )

    /**
     * Segue gli scaricamenti chiesti per la trasmissione.
     *
     * Due segnali: quello che arriva in fondo e quello che si arrende.
     * Il secondo si legge dallo stato della coda, perche' un
     * fallimento non ha un canale suo.
     */
    private fun seguiGliScaricamenti() {
        val app = application as SoSoundApp
        scope.launch {
            app.downloadQueue.completed.collect { aggiorna(it.videoId, riuscito = true) }
        }
        scope.launch {
            app.downloadQueue.items.collect { elementi ->
                elementi.filter {
                    it.state == com.sosound.app.data.download.QueueItem.State.ERRORE &&
                        it.track.videoId in inArrivo
                }.forEach { aggiorna(it.track.videoId, riuscito = false) }
            }
        }
    }

    /**
     * Un avviso dal servizio all'interfaccia.
     *
     * Il servizio non ha uno schermo: certe cose le sa solo lui — quanti
     * brani non si possono trasmettere, per esempio — e senza un canale
     * resterebbero un comportamento inspiegato.
     */
    private fun avvisa(testo: String) {
        avvisi.tryEmit(testo)
    }

    /** Dove sta davvero il file di un brano, secondo la libreria. */
    private fun posizioneDi(videoId: String): String? = runCatching {
        kotlinx.coroutines.runBlocking {
            (application as SoSoundApp).database.tracks().byId(videoId)?.path
        }
    }.getOrNull()

    /** Riporta un elemento all'indirizzo locale del suo file. */
    private fun perIlTelefono(item: MediaItem): MediaItem {
        val posizione = posizioneDi(item.mediaId) ?: return item
        val uri = if (posizione.startsWith("content://")) android.net.Uri.parse(posizione)
        else java.io.File(posizione).let { android.net.Uri.fromFile(it) }
        return item.buildUpon().setUri(uri).setMimeType(null).build()
    }

    private fun avviaCast() {
        castManager = CastManager(this)
        castManager.onSessione = { sessione ->
            if (sessione != null) passaACast() else tornaAlTelefono()
        }
        castManager.inizializza()
    }

    /**
     * Sposta la riproduzione sull'apparecchio, dal punto in cui era.
     *
     * La coda e la posizione si portano dietro: trasmettere e ritrovarsi
     * il brano daccapo, o peggio la coda vuota, non e' «trasmettere», e'
     * «ricominciare altrove».
     */
    private fun passaACast() {
        val ctx = castManager.castContext ?: return
        val vecchio = session?.player ?: return
        if (vecchio is CastPlayer) return

        // La coda si porta INTERA, anche i brani senza file.
        //
        // Quelli si scaricano strada facendo, uno con un brano di
        // anticipo: c'e' tutto il tempo, e una coda di venti canzoni
        // ascoltate al volo non deve diventare una coda vuota. Se uno
        // non ce la fa, esce da solo quando tocca a lui.
        val coda = (0 until vecchio.mediaItemCount).map { vecchio.getMediaItemAt(it) }
        val senzaFile = coda.count { posizioneDi(it.mediaId)?.isNotBlank() != true }
        if (senzaFile > 0) avvisa(
            "$senzaFile brani si scaricano mentre suona la coda, per poterli " +
                "mandare all'apparecchio."
        )
        val indice = vecchio.currentMediaItemIndex.coerceIn(0, maxOf(0, coda.size - 1))
        val posizione = vecchio.currentPosition
        // Il primo da preparare e' quello che tocchera' per primo.
        coda.getOrNull(indice)?.let { if (posizioneDi(it.mediaId)?.isNotBlank() != true) scarica(it.mediaId) }
        coda.getOrNull(indice + 1)?.let { if (posizioneDi(it.mediaId)?.isNotBlank() != true) scarica(it.mediaId) }

        // Se ce n'era gia' uno acceso va spento, se no il nuovo trova
        // la porta occupata e non parte — e senza sorgente il ricevitore
        // riceve una coda di indirizzi che non rispondono.
        server?.ferma()
        server = LocalMediaServer(this, (application as SoSoundApp).database.tracks())
        if (server?.avvia() != true) { server = null; return }

        val nuovo = CastPlayer(ctx).also { cast = it; it.addListener(cambioBrano) }
        vecchio.pause()
        session?.player = nuovo
        if (coda.isNotEmpty()) {
            nuovo.setMediaItems(coda.map { perDestinazione(it) }, indice, posizione)
            nuovo.prepare()
            nuovo.play()
        }
    }

    /**
     * Passa a un apparecchio DLNA — TV Samsung o LG, impianti, ricevitori.
     *
     * Serve lo stesso server a bordo del Cast: anche un renderer DLNA
     * scarica da un indirizzo invece di leggere il telefono. È il motivo
     * per cui aggiungere DLNA dopo Cast e' costato poco.
     */
    fun passaADlna(device: com.sosound.app.cast.dlna.DlnaDevice) {
        val vecchio = session?.player ?: return

        // Come per Cast: la coda si porta intera e i brani senza file si
        // scaricano strada facendo.
        val coda = (0 until vecchio.mediaItemCount).map { vecchio.getMediaItemAt(it) }
        val daPrendere = coda.count { posizioneDi(it.mediaId)?.isNotBlank() != true }
        if (daPrendere > 0) avvisa(
            "$daPrendere brani si scaricano mentre suona la coda."
        )
        val indice = vecchio.currentMediaItemIndex
        val posizione = vecchio.currentPosition

        if (server == null) {
            server = LocalMediaServer(this, (application as SoSoundApp).database.tracks())
            if (server?.avvia() != true) { server = null; return }
        }

        val nuovo = DlnaPlayer(
            rete = reteDlna,
            device = device,
            urlDi = { id -> server?.urlDi(id) },
            mimeDi = { id -> LocalMediaServer.mimeDi(posizioneDi(id).orEmpty()) },
        ).also { dlna = it; it.addListener(cambioBrano) }

        vecchio.pause()
        if (vecchio is CastPlayer) { vecchio.release(); cast = null }
        session?.player = nuovo
        if (coda.isNotEmpty()) {
            nuovo.setMediaItems(coda, indice, posizione)
            nuovo.prepare()
            nuovo.play()
        }
    }

    /** Il ritorno al telefono chiesto dall'interfaccia. */
    fun tornaAlTelefonoDaFuori() = tornaAlTelefono()

    private fun tornaAlTelefono() {
        val vecchio = session?.player
        val locale = locale ?: return
        if (vecchio === locale) return

        val coda = vecchio?.let { p -> (0 until p.mediaItemCount).map { p.getMediaItemAt(it) } }
            .orEmpty()
        val indice = vecchio?.currentMediaItemIndex ?: 0
        val posizione = vecchio?.currentPosition ?: 0L

        session?.player = locale
        if (coda.isNotEmpty()) {
            // Gli elementi tornano all'indirizzo del file sul telefono:
            // quello del server a bordo smette di valere appena lo si
            // spegne, e lasciarglielo — o peggio azzerarlo — darebbe una
            // coda muta.
            locale.setMediaItems(coda.map { perIlTelefono(it) }, indice, posizione)
            locale.prepare()
        }
        cast?.release()
        cast = null
        dlna?.release()
        dlna = null
        server?.ferma()
        server = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Un riferimento statico al servizio vivo.
     *
     * La sessione media non ha un canale per «passa a questo apparecchio
     * DLNA»: e' un comando nostro, fuori dal protocollo. Un riferimento
     * debole evita di tenere vivo il servizio quando Android lo chiude.
     */
    init { istanza = java.lang.ref.WeakReference(this) }

    companion object {
        /** Gli avvisi del servizio, che l'interfaccia mostra. */
        val avvisi = kotlinx.coroutines.flow.MutableSharedFlow<String>(
            extraBufferCapacity = 4,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )

        private var istanza: java.lang.ref.WeakReference<PlaybackService>? = null
        fun attivo(): PlaybackService? = istanza?.get()
    }

    /**
     * Cosa succede quando l'utente scarta l'app dai recenti.
     *
     * Se la musica sta suonando il servizio deve restare in piedi: togliere
     * l'app dai recenti non vuol dire volersi fermare. Se invece e' in pausa
     * non ha senso tenere un servizio e una notifica per niente.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        salvaCoda()
        dlna?.release()
        dlna = null
        reteDlna.chiudi()
        castManager.rilascia()
        cast?.release()
        cast = null
        server?.ferma()
        server = null
        focus?.release()
        focus = null
        CacheAudio.chiudi()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
