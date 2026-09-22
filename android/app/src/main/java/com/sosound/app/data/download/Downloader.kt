package com.sosound.app.data.download

import android.content.Context
import android.util.Log
import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.library.TrackEntity
import com.sosound.app.data.storage.MusicStorage
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Scarica l'audio di un brano usando yt-dlp, che gira dentro l'app.
 *
 * Nessuna ricodifica: si prende il flusso audio che YouTube serve gia'
 * pronto (opus in un contenitore webm, oppure AAC in m4a) e lo si salva
 * com'e'. ExoPlayer li riproduce entrambi nativamente.
 *
 * E' per questo che il modulo ffmpeg non e' incluso: pesa 132 MB e
 * servirebbe solo a convertire in un formato che non ci serve. Il prezzo
 * e' che non possiamo scrivere i tag dentro il file - ma i metadati
 * stanno nel database, quindi non e' un prezzo.
 */
class Downloader(
    private val context: Context,
    private val storage: MusicStorage,
) {

    /**
     * Dove yt-dlp scrive mentre scarica.
     *
     * Sempre e solo qui, anche quando la destinazione finale e' una
     * cartella scelta dall'utente: yt-dlp vuole un percorso vero sul
     * filesystem, e un `content://` non lo e'. A scaricamento finito il
     * file viene consegnato a MusicStorage, che lo porta dove deve.
     */
    private val workDir: File
        get() = File(context.cacheDir, "in-arrivo").apply { mkdirs() }

    /**
     * Inizializza il runtime Python. Va fatto una volta per avvio, fuori
     * dal thread principale: la prima volta estrae l'interprete su disco e
     * ci mette qualche secondo.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            YoutubeDL.getInstance().init(context)
        }.onFailure { Log.e(TAG, "init di yt-dlp fallito", it) }
    }

    /**
     * Aggiorna yt-dlp scaricando la versione nuova.
     *
     * È la cosa piu' importante di tutta l'app. Quando YouTube cambia
     * qualcosa i download smettono di funzionare, e la correzione esce
     * come nuova versione di yt-dlp nel giro di giorni. Senza questo,
     * ogni rottura richiederebbe un APK nuovo.
     */
    suspend fun updateEngine(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val status = YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Aggiornato a ${version() ?: "una versione nuova"}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Già aggiornato (${version() ?: "?"})"
                else -> "Esito sconosciuto"
            }
        }.onFailure { Log.e(TAG, "aggiornamento fallito", it) }
    }

    fun version(): String? = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()

    /**
     * Scarica un brano. [onProgress] riceve valori fra 0 e 1.
     *
     * Torna la riga di libreria da salvare, oppure un errore leggibile.
     */
    suspend fun download(
        track: CatalogTrack,
        onProgress: (Float) -> Unit = {},
    ): TrackEntity = withContext(Dispatchers.IO) {
        val dest = File(workDir, track.videoId).apply { mkdirs() }
        // Ripuliamo eventuali resti di un tentativo precedente: un file
        // parziale rimasto li' farebbe fallire la selezione piu' sotto.
        dest.listFiles()?.forEach { it.delete() }

        // L'identificativo finisce dentro un indirizzo: se non ha il
        // formato di YouTube, quell'indirizzo punta altrove e yt-dlp
        // scaricherebbe obbedientemente qualcos'altro. Arriva dal nostro
        // catalogo e quindi non dovrebbe mai succedere — ed e' il motivo
        // per cui il controllo costa una riga e si tiene.
        if (!com.sosound.app.cast.LocalMediaServer.videoIdValido(track.videoId)) {
            throw IllegalArgumentException("identificativo non valido: ${track.videoId}")
        }

        var ultimo: Exception? = null
        for (client in CLIENTS) {
            val request = YoutubeDLRequest("https://www.youtube.com/watch?v=${track.videoId}").apply {
                // Un solo flusso audio, gia' in un contenitore riproducibile.
                // Niente "bestaudio+bestvideo" e niente unione: servirebbe ffmpeg.
                addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio")
                addOption("-o", "${dest.absolutePath}/%(id)s.%(ext)s")
                addOption("--no-playlist")
                addOption("--no-mtime")
                addOption("--retries", "3")
                addOption("--socket-timeout", "30")
                // YouTube espone liste di formati diverse a seconda del
                // client che chiede. Su certi brani quella di serie
                // restituisce indirizzi che poi rispondono 403: chiedere
                // la lista a un altro client la aggira, ed e' una
                // possibilita' che yt-dlp offre apposta.
                if (client != null) {
                    addOption("--extractor-args", "youtube:player_client=$client")
                }
            }

            try {
                YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                    // yt-dlp riporta 0-100; sopra il 99 ci mettiamo noi la
                    // copertina, quindi lasciamo un margine.
                    onProgress((progress / 100f).coerceIn(0f, 0.95f))
                }
                ultimo = null
                break
            } catch (e: Exception) {
                ultimo = e
                dest.listFiles()?.forEach { it.delete() }
                // Se il brano non c'e' o e' privato, cambiare client non
                // serve a niente: si smette subito invece di provarli
                // tutti e far aspettare l'utente per nulla.
                if (classify(e.message, e).kind == FailureKind.CONTENUTO) break
                Log.w(TAG, "client ${client ?: "di serie"} fallito: ${e.message?.take(120)}")
            }
        }

        if (ultimo != null) {
            dest.deleteRecursively()
            throw classify(ultimo.message, ultimo)
        }

        val audio = dest.listFiles()?.maxByOrNull { it.length() }
            ?: run {
                dest.deleteRecursively()
                throw DownloadError(
                    "yt-dlp non ha prodotto nessun file",
                    FailureKind.MOTORE_DISALLINEATO,
                )
            }

        val cover = track.thumbnail?.let { fetchCover(it, dest) }
        val size = audio.length()

        // La consegna avviene qui: da questo punto il file non e' piu'
        // nostro e puo' stare su una scheda SD o dentro una cartella
        // sincronizzata.
        val location = storage.publish(track.videoId, track.artist, track.title, audio)
        val coverLocation = cover?.let { storage.publishCover(track.videoId, it) }
        onProgress(1f)

        TrackEntity(
            videoId = track.videoId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds,
            path = location,
            coverPath = coverLocation,
            sizeBytes = size,
            addedAt = System.currentTimeMillis(),
            showId = track.showId,
        )
    }

    /** La copertina e' un di piu': se non arriva, il brano vale lo stesso. */
    private fun fetchCover(url: String, dir: File): File? = runCatching {
        val file = File(dir, "cover.jpg")
        URL(url).openStream().use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        file.takeIf { it.length() > 0 }
    }.getOrNull()

    suspend fun remove(track: TrackEntity) = storage.remove(track)

    /**
     * Traduce l'errore di yt-dlp in un messaggio leggibile e, soprattutto,
     * in un tipo: e' il tipo che decide se vale la pena aggiornare il
     * motore e ritentare, o se sarebbe solo girare a vuoto.
     *
     * La verifica della rete viene per prima e scavalca tutto: senza
     * connessione, yt-dlp produce errori che somigliano a un motore
     * disallineato, e si finirebbe per scaricare aggiornamenti che non
     * possono arrivare.
     *
     * Il resto — i pattern sul testo — non dipende dal telefono, ed e'
     * per questo che vive fuori, in [classificaMessaggioYtDlp]: e' la
     * parte che si puo' verificare con un test normale, senza un Context.
     */
    private fun classify(raw: String?, cause: Throwable?): DownloadError {
        if (!context.hasNetwork()) {
            return DownloadError(
                "Nessuna connessione. Il download riprende quando torna la rete.",
                FailureKind.RETE_ASSENTE, cause,
            )
        }
        // Il testo vero resta nel log e non sullo schermo: e' li' che si
        // riconosce un caso nuovo, per aggiungerlo a
        // classificaMessaggioYtDlp() la prossima volta.
        Log.w(TAG, "download fallito: ${raw.orEmpty().take(300)}")
        return classificaMessaggioYtDlp(raw, cause)
    }

    private companion object {
        /**
         * I client da provare, in ordine.
         *
         * `null` e' quello di serie di yt-dlp. Gli altri si provano solo
         * se il primo fallisce: ognuno costa una richiesta, e sui brani
         * che funzionano al primo colpo — la stragrande maggioranza —
         * non se ne paga nessuna.
         */
        val CLIENTS = listOf(null, "tv", "ios", "web_safari")

        const val TAG = "Downloader"
    }
}

/**
 * Riconosce il testo che yt-dlp scrive quando un download fallisce, e lo
 * trasforma in un [DownloadError] con un messaggio in italiano.
 *
 * Vive fuori da [Downloader] apposta: non tocca la rete ne' il telefono,
 * e si puo' chiamare da un test normale della JVM.
 *
 * ⚠️ Il ramo finale non deve mai restituire il testo grezzo di yt-dlp:
 * e' gergo per chi sviluppa, non per chi ascolta. «Requested format is
 * not available» e' arrivato cosi' fino a una persona vera, in un
 * elenco pensato per dirle cosa fare — non cosa e' andato storto dentro
 * yt-dlp. Un caso nuovo, non ancora riconosciuto, resta nel log: si
 * aggiunge qui appena si vede, ma nel frattempo non spaventa nessuno.
 */
internal fun classificaMessaggioYtDlp(raw: String?, cause: Throwable? = null): DownloadError {
    val m = raw.orEmpty()

    return when {
        m.contains("Sign in to confirm", true) || m.contains("not a bot", true) ->
            DownloadError(
                "YouTube chiede una conferma anti-bot. Riprova più tardi, o da un'altra rete.",
                FailureKind.ANTIBOT, cause,
            )

        // Il 403 arriva dopo che il brano e' stato trovato: non e'
        // «non esiste», e' «questo indirizzo e' stato rifiutato».
        m.contains("403") || m.contains("Forbidden", true) ->
            DownloadError(
                "YouTube ha rifiutato lo scaricamento di questo brano. " +
                    "Riprova: a volte basta, se no aggiorna yt-dlp.",
                FailureKind.MOTORE_DISALLINEATO, cause,
            )

        m.contains("Video unavailable", true) ->
            DownloadError("Questo brano non è disponibile", FailureKind.CONTENUTO, cause)

        m.contains("Private video", true) ->
            DownloadError("Brano privato", FailureKind.CONTENUTO, cause)

        m.contains("age", true) && m.contains("restrict", true) ->
            DownloadError(
                "Brano con restrizione di età: serve un accesso",
                FailureKind.CONTENUTO, cause,
            )

        // La lista dei formati che YouTube espone dipende dal client che
        // la chiede: uno di serie puo' non avere un audio scaricabile
        // dove «tv» o «ios» ce l'hanno. Resta MOTORE_DISALLINEATO e non
        // CONTENUTO apposta — cosi' il giro sui client alternativi (vedi
        // Downloader.CLIENTS) continua invece di fermarsi al primo.
        m.contains("Requested format is not available", true) ->
            DownloadError(
                "YouTube non offre un formato audio scaricabile per questo " +
                    "brano con il client provato. Riprovo con un altro; se " +
                    "persiste, aggiorna yt-dlp.",
                FailureKind.MOTORE_DISALLINEATO, cause,
            )

        // Tutto il resto — errori HTTP non ancora visti, estrazione
        // fallita, firma non risolta — e' il quadro tipico di uno yt-dlp
        // rimasto indietro rispetto a YouTube. E' anche il caso al primo
        // avvio: la versione impacchettata risale al rilascio della
        // libreria. Il testo di yt-dlp non arriva mai qui sullo schermo
        // (chi chiama, [Downloader.classify], lo scrive nel log prima di
        // arrivare a questo ramo): e' un indizio per chi sviluppa, non
        // un messaggio per chi ascolta.
        else -> DownloadError(
            "Download non riuscito per un motivo che non riconosco. " +
                "Riprova, o aggiorna yt-dlp dalle impostazioni.",
            FailureKind.MOTORE_DISALLINEATO, cause,
        )
    }
}
