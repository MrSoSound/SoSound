package com.sosound.app.data.catalog

/** Un brano trovato sul catalogo, non ancora scaricato. */
data class CatalogTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationText: String? = null,
    val durationSeconds: Int? = null,
    val thumbnail: String? = null,
    /**
     * A cosa e' collegato questo brano. Sono i riferimenti che
     * permettono di passare dal brano all'artista o all'album senza
     * cercarli di nuovo — e per una puntata di podcast, al programma.
     */
    val artistId: String? = null,
    val albumId: String? = null,
    val showId: String? = null,
    /** La descrizione, quando c'e': le puntate dei podcast ce l'hanno. */
    val description: String? = null,
) {
    val subtitle: String
        get() = listOfNotNull(artist, album, durationText).joinToString(" · ")
}
