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
    /**
     * Se YouTube Music lo segna come esplicito.
     *
     * Nullable perche' non e' sempre disponibile: un brano posseduto,
     * scaricato prima di questo campo, non lo sa e non deve fingere di
     * saperlo. Per un risultato appena letto dall'API invece si sa
     * sempre — o c'e' il badge o non c'e', non esiste un "forse".
     */
    val explicit: Boolean? = null,
    /**
     * Come YouTube Music classifica la fonte: `MUSIC_VIDEO_TYPE_ATV` per
     * un'incisione solo audio, `OMV`/`UGC` quando dietro c'e' un video
     * vero (musicale o caricato da un utente). Null quando non lo sappiamo
     * — un brano posseduto scaricato prima di questo campo, per esempio.
     */
    val musicVideoType: String? = null,
) {
    val subtitle: String
        get() = listOfNotNull(artist, album, durationText).joinToString(" · ")

    /** Vero se questo risultato e' l'audio preso da un video, non un'incisione pensata per l'ascolto. */
    val audioDiVideo: Boolean
        get() = musicVideoType == "MUSIC_VIDEO_TYPE_OMV" || musicVideoType == "MUSIC_VIDEO_TYPE_UGC"
}
