package com.sosound.app.data.catalog

/** Cosa si sta cercando. */
enum class SearchKind(val label: String, val filter: String) {
    // I codici sono quelli dell'API interna: prefisso + codice del tipo
    // + coda. Li usa anche il sito, e non sono documentati da nessuna
    // parte — se cambiano, la ricerca di quel tipo smette di funzionare
    // e il test lo dice.
    BRANI("Brani", "II"),
    ALBUM("Album", "IY"),
    ARTISTI("Artisti", "Ig"),

    /**
     * Il filtro «podcasts» restituisce i programmi, i cui identificativi
     * puntano a un canale e non a un elenco di puntate. Qui si cercano
     * direttamente gli **episodi**: hanno un videoId, quindi si scaricano
     * e si ascoltano esattamente come una canzone — che e' tutto quello
     * che serve, visto che dei podcast vogliamo solo l'audio.
     */
    PODCAST("Podcast", "JI");

    val params: String get() = "EgWKAQ" + filter + "AWoMEA4QChADEAQQCRAF"

    /** Il filtro dei programmi, che per i podcast si usa insieme a quello
     *  delle puntate: sono due elenchi diversi e servono entrambi. */
    val filtroProgrammi: String get() = "EgWKAQ" + "JQ" + "AWoMEA4QChADEAQQCRAF"
}

/** Un album trovato nella ricerca: abbastanza per mostrarlo in lista. */
data class AlbumRef(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val thumbnail: String? = null,
) {
    val subtitle: String get() = listOfNotNull(artist, year).joinToString(" · ")
}

/** Un programma podcast trovato nella ricerca. */
data class ShowRef(
    val browseId: String,
    val title: String,
    val publisher: String? = null,
    val thumbnail: String? = null,
)

data class ArtistRef(
    val browseId: String,
    val name: String,
    val subtitle: String? = null,
    val thumbnail: String? = null,
)

/** La pagina di un album, con la sua scaletta. */
data class AlbumPage(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: String? = null,
    val thumbnail: String? = null,
    val info: String? = null,
    val tracks: List<CatalogTrack> = emptyList(),
)

/** La pagina di un artista. */
data class ArtistPage(
    val browseId: String,
    val name: String,
    val thumbnail: String? = null,
    val topSongs: List<CatalogTrack> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val singles: List<AlbumRef> = emptyList(),
)

/** L'esito di una ricerca, che cambia forma secondo il tipo cercato. */
data class SearchResults(
    val kind: SearchKind,
    val tracks: List<CatalogTrack> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val artists: List<ArtistRef> = emptyList(),
    val shows: List<ShowRef> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && shows.isEmpty()
    val size: Int get() = tracks.size + albums.size + artists.size + shows.size
}

/** La pagina di un podcast, con le sue puntate. */
data class PodcastPage(
    val browseId: String,
    val title: String,
    val publisher: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val episodes: List<CatalogTrack> = emptyList(),
)

/** Cosa propone YouTube Music adesso, senza che tu abbia cercato niente. */
data class DiscoverFeed(
    val trending: List<CatalogTrack> = emptyList(),
    val topArtists: List<ArtistRef> = emptyList(),
    val newAlbums: List<AlbumRef> = emptyList(),
) {
    val isEmpty: Boolean
        get() = trending.isEmpty() && topArtists.isEmpty() && newAlbums.isEmpty()
}
