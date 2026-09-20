package com.sosound.app.data.stream

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest

/**
 * Trova l'indirizzo da cui un brano si puo' ascoltare, senza scaricarlo.
 *
 * ## La differenza con lo scaricare
 *
 * yt-dlp fa due cose distinte: capire **dove** sta l'audio, e portarselo
 * via. La prima costa un paio di secondi, la seconda quanto pesa il
 * file. Fermandosi alla prima si ottiene un indirizzo che si puo' dare
 * al lettore: l'audio arriva mentre suona, e quello che arriva si tiene.
 *
 * ## Perche' l'indirizzo non si puo' salvare
 *
 * Scade. YouTube lo firma per qualche ora e poi smette di rispondere,
 * quindi non e' una cosa da scrivere nell'indice o nel database: un
 * brano «in streaming» non e' un brano che si possiede, e' un
 * riferimento da risolvere ogni volta che serve.
 *
 * Qui si tiene a mente per un po' — abbastanza da non rifare il lavoro
 * saltando avanti e indietro nella stessa coda, molto meno di quanto
 * dura la firma.
 */
object Flusso {

    private const val TAG = "Flusso"

    /** Quanto ci fidiamo di un indirizzo gia' trovato. */
    private const val VALIDITA_MS = 30 * 60 * 1000L

    private data class Nota(val url: String, val quando: Long)

    private val ricordati = HashMap<String, Nota>()

    /**
     * L'indirizzo del flusso audio di [videoId], o null se non si trova.
     *
     * Bloccante: va chiamata da un thread di lavoro. Il lettore la chiama
     * dal proprio thread di caricamento, che e' il posto giusto — cosi'
     * l'attesa non ferma l'interfaccia e il brano parte quando e' pronto.
     */
    fun indirizzo(videoId: String): String? {
        val adesso = System.currentTimeMillis()
        synchronized(ricordati) {
            ricordati[videoId]?.let {
                if (adesso - it.quando < VALIDITA_MS) return it.url
                ricordati.remove(videoId)
            }
        }

        val url = runCatching {
            val richiesta = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId").apply {
                // Un solo flusso audio, gia' riproducibile: le stesse
                // preferenze dello scaricamento, cosi' quello che si
                // ascolta e quello che si tiene sono lo stesso file.
                addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio")
                addOption("--no-playlist")
                // Niente di cio' che serve a scrivere su disco: qui si
                // chiede solo dove guardare.
                addOption("--skip-download")
            }
            YoutubeDL.getInstance().getInfo(richiesta).url
        }.getOrElse {
            Log.w(TAG, "niente indirizzo per $videoId: ${it.message}")
            null
        }

        if (url.isNullOrBlank()) return null
        synchronized(ricordati) { ricordati[videoId] = Nota(url, adesso) }
        return url
    }

    /** Dimentica quello che sapeva: dopo un errore di rete conviene. */
    fun dimentica() = synchronized(ricordati) { ricordati.clear() }

    // ------------------------------------------------- lo schema interno

    /** Lo schema con cui un brano senza file viaggia dentro il lettore. */
    const val SCHEMA = "sosound"

    /** L'indirizzo finto di un brano da ascoltare in streaming. */
    fun uriDi(videoId: String) = "$SCHEMA://brano/$videoId"

    /** Il videoId dentro un indirizzo finto, o null se non e' uno dei nostri. */
    fun videoIdDi(uri: android.net.Uri): String? =
        if (uri.scheme == SCHEMA) uri.lastPathSegment else null
}
