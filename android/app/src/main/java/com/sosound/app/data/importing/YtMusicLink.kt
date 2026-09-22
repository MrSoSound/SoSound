package com.sosound.app.data.importing

import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.catalog.InnerTubeClient

/** Cosa abbiamo tirato fuori da un link di YouTube Music. */
data class YtMusicPlaylist(
    val name: String,
    val tracks: List<CatalogTrack>,
    /** Vero se la playlist continuava oltre il tetto che si segue
     *  davvero — vedi [InnerTubeClient.playlist]. */
    val troncata: Boolean = false,
)

/**
 * Legge una playlist pubblica di YouTube Music da un link, senza account.
 *
 * A differenza di [SpotifyLink] qui non serve una pagina di anteprima da
 * interpretare: un link con `list=` porta gia' l'identificativo della
 * playlist, e con il prefisso `VL` diventa il `browseId` che l'API interna
 * usa per sfogliarla — lo stesso meccanismo di [InnerTubeClient.album] e
 * [InnerTubeClient.artist]. I brani arrivano quindi con il loro videoId
 * vero, letti dallo stesso parser della ricerca: nessuna ricerca per
 * titolo, nessun brano «non trovato».
 */
class YtMusicLink(private val client: InnerTubeClient = InnerTubeClient()) {

    suspend fun fetch(url: String): Result<YtMusicPlaylist> = runCatching {
        val id = parseUrl(url)
            ?: throw IllegalArgumentException(
                "Non è un link di YouTube Music a una playlist."
            )

        val pagina = client.playlist("VL$id")
        if (pagina.tracks.isEmpty()) {
            throw IllegalStateException("Nessun brano trovato in questo link.")
        }

        YtMusicPlaylist(name = pagina.title, tracks = pagina.tracks, troncata = pagina.troncata)
    }

    private fun parseUrl(url: String): String? = LINK.find(url.trim())?.groupValues?.get(1)

    fun close() = client.close()

    companion object {
        /**
         * Accetta sia `music.youtube.com` sia `youtube.com` (con o senza
         * `www.`/`m.`): l'identificativo dopo `list=` e' lo stesso, e il
         * `browseId` che ne deriva funziona in entrambi i casi.
         */
        private val LINK = Regex(
            """(?:music\.|www\.|m\.)?youtube\.com/playlist\?\S*list=([A-Za-z0-9_-]+)"""
        )

        /** Se una stringa somiglia a un link di YouTube Music. */
        fun looksLikeLink(text: String) = LINK.containsMatchIn(text)
    }
}
