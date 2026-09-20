package com.sosound.app.data.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parla con l'API interna di YouTube Music.
 *
 * Nessuna libreria di mezzo: due endpoint, `search` e `browse`, e la
 * risposta si attraversa cercando le chiavi dove capitano. E' brutto ma
 * necessario: la struttura di queste risposte cambia senza preavviso, e
 * seguire un percorso fisso si romperebbe molto piu' spesso.
 */
class InnerTubeClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = HttpClient(OkHttp) { expectSuccess = false }

    /**
     * La versione del client e' la data di oggi. E' quello che fa il sito
     * vero, ed e' anche il motivo per cui non va messa una costante: una
     * versione vecchia di mesi e' un segnale che conviene non dare.
     */
    private fun clientVersion(): String {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return "1.${fmt.format(Date())}.01.00"
    }

    private suspend fun call(endpoint: String, body: JsonObject): JsonElement =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", clientVersion())
                        put("hl", "it")
                        put("gl", "IT")
                    })
                })
                body.forEach { (k, v) -> put(k, v) }
            }
            val text = http.post("$BASE/$endpoint?alt=json") {
                contentType(ContentType.Application.Json)
                header("User-Agent", USER_AGENT)
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(payload.toString())
            }.bodyAsText()
            json.parseToJsonElement(text)
        }

    // ------------------------------------------------------------ ricerca

    /** Compatibilita': la ricerca di brani, che e' quella piu' usata. */
    suspend fun search(query: String, limit: Int = 25): List<CatalogTrack> =
        search(query, SearchKind.BRANI, limit).tracks

    suspend fun search(query: String, kind: SearchKind, limit: Int = 25): SearchResults {
        // Per i podcast servono due interrogazioni: il filtro delle
        // puntate non restituisce i programmi, e cercando «supernova» si
        // vogliono vedere entrambi — il programma sopra, le sue puntate
        // sotto.
        if (kind == SearchKind.PODCAST) return searchPodcasts(query, limit)

        val root = call("search", buildJsonObject {
            put("query", query)
            put("params", kind.params)
        })

        return when (kind) {
            SearchKind.BRANI, SearchKind.PODCAST -> SearchResults(
                kind = kind,
                tracks = findAll(root, "musicResponsiveListItemRenderer")
                    .mapNotNull { parseTrack(it) }
                    .distinctBy { it.videoId }
                    .take(limit),
            )

            SearchKind.ALBUM -> SearchResults(
                kind = kind,
                albums = findAll(root, "musicResponsiveListItemRenderer")
                    .mapNotNull { parseAlbumRef(it) }
                    .distinctBy { it.browseId }
                    .take(limit),
            )

            SearchKind.ARTISTI -> SearchResults(
                kind = kind,
                artists = findAll(root, "musicResponsiveListItemRenderer")
                    .mapNotNull { parseArtistRef(it) }
                    .distinctBy { it.browseId }
                    .take(limit),
            )
        }
    }

    private suspend fun searchPodcasts(query: String, limit: Int): SearchResults {
        val programmi = runCatching {
            val r = call("search", buildJsonObject {
                put("query", query)
                put("params", SearchKind.PODCAST.filtroProgrammi)
            })
            findAll(r, "musicResponsiveListItemRenderer")
                .mapNotNull { parseShowRef(it) }
                .distinctBy { it.browseId }
                .take(6)
        }.getOrDefault(emptyList())

        val puntate = runCatching {
            val r = call("search", buildJsonObject {
                put("query", query)
                put("params", SearchKind.PODCAST.params)
            })
            findAll(r, "musicResponsiveListItemRenderer")
                .mapNotNull { parseEpisodeResult(it) }
                .distinctBy { it.videoId }
                .take(limit)
        }.getOrDefault(emptyList())

        return SearchResults(kind = SearchKind.PODCAST, tracks = puntate, shows = programmi)
    }

    private fun parseShowRef(item: JsonObject): ShowRef? {
        // Si preferisce l'elenco puntate al canale: apre direttamente
        // cento puntate invece di dieci.
        val browseId = browseIdOf(item, "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")
            ?: browseIdOf(item, "MUSIC_PAGE_TYPE_USER_CHANNEL")
            ?: return null
        val colonne = item["flexColumns"]?.jsonArray ?: return null
        val testi = colonne.map { runsText(it.jsonObject.values.firstOrNull()?.jsonObject?.get("text")) }
        val titolo = testi.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return ShowRef(
            browseId = browseId,
            title = pulisciNomeProgramma(titolo),
            publisher = testi.getOrNull(1)?.takeIf { it.isNotBlank() && it != titolo }
                ?.let { pulisciNomeProgramma(it) },
            thumbnail = bestThumb(item),
        )
    }

    /**
     * Una puntata trovata cercando.
     *
     * Il sottotitolo e' «data • Programma», non «artista • album»:
     * leggerlo con il parser dei brani metteva la data al posto
     * dell'artista e il programma al posto dell'album — ed e' cosi' che
     * nella scheda di una puntata il nome del podcast compariva sotto
     * l'etichetta «Album».
     */
    private fun parseEpisodeResult(item: JsonObject): CatalogTrack? {
        val videoId = firstWatchVideoId(item) ?: return null
        val colonne = item["flexColumns"]?.jsonArray ?: return null
        val testi = colonne.map { runsText(it.jsonObject.values.firstOrNull()?.jsonObject?.get("text")) }
        val titolo = testi.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null

        val parti = testi.getOrNull(1).orEmpty().split(" • ").map { it.trim() }.filter { it.isNotEmpty() }
        val data = parti.firstOrNull()
        val programma = parti.getOrNull(1)?.let { pulisciNomeProgramma(it) }

        return CatalogTrack(
            videoId = videoId,
            title = titolo,
            artist = programma ?: "Podcast",
            album = null,
            durationText = data,
            thumbnail = bestThumb(item),
            showId = browseIdOf(item, "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")
                ?: browseIdOf(item, "MUSIC_PAGE_TYPE_USER_CHANNEL"),
        )
    }

    /** «SUPERNOVA - Tutti gli episodi» e' il nome dell'elenco, non del programma. */
    private fun pulisciNomeProgramma(nome: String) = nome
        .substringBefore(" - Tutti gli episodi")
        .substringBefore(" - All episodes")
        .trim()

    // ------------------------------------------------------------- sfoglia

    suspend fun album(browseId: String): AlbumPage? {
        val root = call("browse", buildJsonObject { put("browseId", browseId) })
        val header = findAll(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: findAll(root, "musicDetailHeaderRenderer").firstOrNull()
            ?: return null

        val titolo = runsText(header["title"]).ifBlank { return null }
        // L'artista sta in «straplineTextOne»: il sottotitolo dice solo
        // «Album • 2013», senza il nome di chi l'ha fatto.
        val artista = runsText(header["straplineTextOne"])
        val sottotitolo = runsText(header["subtitle"])
        val anno = ANNO.find(sottotitolo)?.value

        val tracce = findAll(root, "musicResponsiveListItemRenderer")
            .mapNotNull { parseAlbumTrack(it, artista, titolo) }

        return AlbumPage(
            browseId = browseId,
            title = titolo,
            artist = artista.ifBlank { "Sconosciuto" },
            year = anno,
            thumbnail = bestThumb(header),
            info = runsText(header["secondSubtitle"]).takeIf { it.isNotBlank() },
            tracks = tracce,
        )
    }

    suspend fun artist(browseId: String): ArtistPage? {
        val root = call("browse", buildJsonObject { put("browseId", browseId) })
        val header = findAll(root, "musicImmersiveHeaderRenderer").firstOrNull()
            ?: findAll(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: return null

        val nome = runsText(header["title"]).ifBlank { return null }

        // Le sezioni si riconoscono dal titolo, che e' localizzato: si
        // guarda una parola chiave invece del testo intero, cosi' regge
        // sia «Brani piu' ascoltati» che «Top songs».
        val sezioni = findAll(root, "musicShelfRenderer") +
            findAll(root, "musicCarouselShelfRenderer")

        val brani = mutableListOf<CatalogTrack>()
        val album = mutableListOf<AlbumRef>()
        val singoli = mutableListOf<AlbumRef>()

        for (s in sezioni) {
            val titolo = (runsText(s["title"]).ifBlank {
                runsText(
                    s["header"]?.jsonObject
                        ?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject
                        ?.get("title")
                )
            }).lowercase()

            when {
                titolo.contains("brani") || titolo.contains("songs") ->
                    brani += findAll(s, "musicResponsiveListItemRenderer")
                        .mapNotNull { parseTrack(it) }

                titolo.contains("singoli") || titolo.contains("singles") ->
                    singoli += findAll(s, "musicTwoRowItemRenderer")
                        .mapNotNull { parseTwoRowAlbum(it, nome) }

                titolo.contains("album") ->
                    album += findAll(s, "musicTwoRowItemRenderer")
                        .mapNotNull { parseTwoRowAlbum(it, nome) }
            }
        }

        return ArtistPage(
            browseId = browseId,
            name = nome,
            thumbnail = bestThumb(header),
            topSongs = brani.distinctBy { it.videoId },
            albums = album.distinctBy { it.browseId },
            singles = singoli.distinctBy { it.browseId },
        )
    }

    /**
     * La pagina di un podcast.
     *
     * Accetta sia l'identificativo del canale (`UC…`, quello che porta
     * una puntata) sia quello dell'elenco puntate (`MPSPP…`). Dal canale
     * le puntate arrivano a dieci per volta; l'elenco ne da' cento, ed e'
     * quello che si vuole — quindi se lo si trova, si segue.
     */
    suspend fun podcast(browseId: String): PodcastPage? {
        var root = call("browse", buildJsonObject { put("browseId", browseId) })

        if (!browseId.startsWith("MPSPP")) {
            val completo = findAll(root, "musicTwoRowItemRenderer")
                .firstNotNullOfOrNull { browseIdOf(it, "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE") }
            if (completo != null) {
                root = call("browse", buildJsonObject { put("browseId", completo) })
            }
        }

        val header = findAll(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: findAll(root, "musicImmersiveHeaderRenderer").firstOrNull()

        val titolo = runsText(header?.get("title"))
            .ifBlank { return null }
            // Il titolo dell'elenco e' «Nome - Tutti gli episodi»: la coda
            // e' rumore, il nome del programma e' quello prima.
            .let { pulisciNomeProgramma(it) }

        val puntate = findAll(root, "musicMultiRowListItemRenderer")
            .mapNotNull { parseEpisode(it, titolo) }

        return PodcastPage(
            browseId = browseId,
            title = titolo,
            publisher = runsText(header?.get("straplineTextOne")).takeIf { it.isNotBlank() },
            // La descrizione vera sta in un riquadro a parte: quella
            // nell'intestazione e' solo il titolo ripetuto.
            description = findAll(root, "musicDescriptionShelfRenderer")
                .firstNotNullOfOrNull { runsText(it["description"]).takeIf { d -> d.isNotBlank() } },
            thumbnail = header?.let { bestThumb(it) },
            episodes = puntate,
        )
    }

    private fun parseEpisode(item: JsonObject, programma: String): CatalogTrack? {
        val videoId = firstWatchVideoId(item) ?: return null
        val titolo = runsText(item["title"]).ifBlank { return null }
        return CatalogTrack(
            videoId = videoId,
            title = titolo,
            artist = programma,
            album = null,
            // Il sottotitolo di una puntata sono visualizzazioni e data,
            // non una durata: si mostra com'e'.
            durationText = runsText(item["subtitle"]).takeIf { it.isNotBlank() },
            thumbnail = bestThumb(item),
            description = runsText(item["description"]).takeIf { it.isNotBlank() },
        )
    }

    /**
     * Quello che YouTube Music propone adesso.
     *
     * Tre richieste invece di una perche' le sezioni stanno su pagine
     * diverse: le tendenze e le novita' su «esplora», gli artisti in
     * classifica su «classifiche». Se una fallisce le altre restano.
     */
    suspend fun discover(): DiscoverFeed {
        val esplora = runCatching {
            call("browse", buildJsonObject { put("browseId", "FEmusic_explore") })
        }.getOrNull()
        val classifiche = runCatching {
            call("browse", buildJsonObject { put("browseId", "FEmusic_charts") })
        }.getOrNull()

        return DiscoverFeed(
            trending = esplora?.let { sezione(it, "tendenz", "trending") }
                ?.let { s -> findAll(s, "musicResponsiveListItemRenderer").mapNotNull { parseTrack(it) } }
                .orEmpty().distinctBy { it.videoId },
            topArtists = classifiche?.let { sezione(it, "artist") }
                ?.let { s -> findAll(s, "musicResponsiveListItemRenderer").mapNotNull { parseArtistRef(it) } }
                .orEmpty().distinctBy { it.browseId },
            newAlbums = esplora?.let { sezione(it, "nuovi album", "new album") }
                ?.let { s -> findAll(s, "musicTwoRowItemRenderer").mapNotNull { parseTwoRowAlbum(it, "") } }
                .orEmpty().distinctBy { it.browseId },
        )
    }

    /** Trova una sezione dal titolo, che e' localizzato. */
    private fun sezione(root: JsonElement, vararg chiavi: String): JsonObject? {
        val sezioni = findAll(root, "musicCarouselShelfRenderer") +
            findAll(root, "musicShelfRenderer")
        return sezioni.firstOrNull { s ->
            val t = (runsText(s["title"]).ifBlank {
                runsText(
                    s["header"]?.jsonObject
                        ?.get("musicCarouselShelfBasicHeaderRenderer")?.jsonObject
                        ?.get("title")
                )
            }).lowercase()
            chiavi.any { t.contains(it) }
        }
    }

    // ------------------------------------------------------------ parsing

    private fun parseTrack(item: JsonObject): CatalogTrack? {
        val videoId = firstWatchVideoId(item) ?: firstString(item, "videoId") ?: return null
        val colonne = item["flexColumns"]?.jsonArray ?: return null
        val testi = colonne.map { runsText(it.jsonObject.values.firstOrNull()?.jsonObject?.get("text")) }

        val titolo = testi.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val parti = testi.getOrNull(1).orEmpty()
            .split(" • ")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals("Brano", true) && !it.equals("Song", true) }

        val durata = parti.lastOrNull()?.takeIf { DURATA.matches(it) }
        val resto = if (durata != null) parti.dropLast(1) else parti

        return CatalogTrack(
            videoId = videoId,
            title = titolo,
            artist = (resto.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Sconosciuto")
                .replace(" e ", ", "),
            album = if (resto.size >= 2) resto[1] else null,
            durationText = durata,
            durationSeconds = durata?.let(::parseDuration),
            thumbnail = bestThumb(item),
            // I riferimenti stanno nei singoli pezzi di testo del
            // sottotitolo: «Daft Punk» porta all'artista, «Random Access
            // Memories» all'album. Raccoglierli qui evita di dover
            // ricercare l'artista per nome quando l'utente lo tocca.
            artistId = browseIdOf(item, "MUSIC_PAGE_TYPE_ARTIST"),
            albumId = browseIdOf(item, "MUSIC_PAGE_TYPE_ALBUM"),
            // Una puntata rimanda al programma in due modi diversi a
            // seconda del risultato: a volte l'elenco delle puntate
            // (MPSPP…), a volte il canale (UC…). Si accettano entrambi, e
            // si preferisce il primo perche' ne restituisce cento invece
            // di dieci.
            showId = browseIdOf(item, "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")
                ?: browseIdOf(item, "MUSIC_PAGE_TYPE_USER_CHANNEL"),
        )
    }

    /** Dentro un album manca la colonna dell'artista: si eredita. */
    private fun parseAlbumTrack(item: JsonObject, artista: String, album: String): CatalogTrack? {
        val videoId = firstWatchVideoId(item) ?: return null
        val colonne = item["flexColumns"]?.jsonArray ?: return null
        val titolo = runsText(colonne.firstOrNull()?.jsonObject?.values?.firstOrNull()?.jsonObject?.get("text"))
            .ifBlank { return null }

        // La durata sta in una colonna a larghezza fissa, non fra le altre.
        val durata = item["fixedColumns"]?.jsonArray
            ?.firstNotNullOfOrNull { c ->
                runsText(c.jsonObject.values.firstOrNull()?.jsonObject?.get("text"))
                    .takeIf { DURATA.matches(it) }
            }

        return CatalogTrack(
            videoId = videoId,
            title = titolo,
            artist = artista.ifBlank { "Sconosciuto" },
            album = album,
            durationText = durata,
            durationSeconds = durata?.let(::parseDuration),
            thumbnail = null,   // la mette la pagina dell'album
        )
    }

    private fun parseAlbumRef(item: JsonObject): AlbumRef? {
        val browseId = browseIdOf(item, "MUSIC_PAGE_TYPE_ALBUM") ?: return null
        val colonne = item["flexColumns"]?.jsonArray ?: return null
        val testi = colonne.map { runsText(it.jsonObject.values.firstOrNull()?.jsonObject?.get("text")) }
        val titolo = testi.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null

        // «Album • Daft Punk • 2013»
        val parti = testi.getOrNull(1).orEmpty().split(" • ").map { it.trim() }
        return AlbumRef(
            browseId = browseId,
            title = titolo,
            artist = parti.getOrNull(1).orEmpty().ifBlank { "Sconosciuto" },
            year = parti.lastOrNull()?.takeIf { ANNO.matches(it) },
            thumbnail = bestThumb(item),
        )
    }

    private fun parseArtistRef(item: JsonObject): ArtistRef? {
        val browseId = browseIdOf(item, "MUSIC_PAGE_TYPE_ARTIST") ?: return null
        val colonne = item["flexColumns"]?.jsonArray ?: return null
        val testi = colonne.map { runsText(it.jsonObject.values.firstOrNull()?.jsonObject?.get("text")) }
        val nome = testi.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return ArtistRef(
            browseId = browseId,
            name = nome,
            subtitle = testi.getOrNull(1)?.takeIf { it.isNotBlank() },
            thumbnail = bestThumb(item),
        )
    }

    /** Gli album nella pagina di un artista usano un'altra forma. */
    private fun parseTwoRowAlbum(item: JsonObject, artista: String): AlbumRef? {
        val browseId = browseIdOf(item, "MUSIC_PAGE_TYPE_ALBUM") ?: return null
        val titolo = runsText(item["title"]).ifBlank { return null }
        val sottotitolo = runsText(item["subtitle"])
        return AlbumRef(
            browseId = browseId,
            title = titolo,
            // Nella pagina di un artista l'artista lo sappiamo gia'; fra
            // le novita' no, e sta dopo il tipo: «Singolo • Ultimo».
            artist = artista.ifBlank { sottotitolo.substringAfter(" • ", "").trim() },
            year = ANNO.find(sottotitolo)?.value,
            thumbnail = bestThumb(item),
        )
    }

    // ------------------------------------------------------------- utilita'

    /**
     * Il browseId giusto e' quello al livello dell'elemento, non il primo
     * che si incontra: dentro la riga di un album c'e' anche quello
     * dell'artista, e prendendo il primo si finisce sempre sull'artista.
     * Per questo si filtra sul pageType.
     */
    private fun browseIdOf(item: JsonObject, pageType: String): String? {
        findAll(item, "browseEndpoint").forEach { be ->
            val pt = be["browseEndpointContextSupportedConfigs"]?.jsonObject
                ?.get("browseEndpointContextMusicConfig")?.jsonObject
                ?.get("pageType")?.let { (it as? JsonPrimitive)?.contentOrNullSafe() }
            if (pt == pageType) {
                (be["browseId"] as? JsonPrimitive)?.contentOrNullSafe()?.let { return it }
            }
        }
        return null
    }

    private fun firstWatchVideoId(item: JsonObject): String? {
        findAll(item, "watchEndpoint").forEach { we ->
            (we["videoId"] as? JsonPrimitive)?.contentOrNullSafe()?.let { return it }
        }
        return null
    }

    private fun findAll(node: JsonElement, key: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        when (node) {
            is JsonObject -> node.forEach { (k, v) ->
                if (k == key && v is JsonObject) out += v
                out += findAll(v, key)
            }
            is JsonArray -> node.forEach { out += findAll(it, key) }
            else -> Unit
        }
        return out
    }

    private fun firstString(node: JsonElement, key: String): String? {
        when (node) {
            is JsonObject -> {
                (node[key] as? JsonPrimitive)?.contentOrNullSafe()?.let { return it }
                node.forEach { (_, v) -> firstString(v, key)?.let { return it } }
            }
            is JsonArray -> node.forEach { firstString(it, key)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { if (isString) content else null }.getOrNull()

    /** Concatena i "runs", che e' come InnerTube rappresenta il testo. */
    private fun runsText(node: JsonElement?): String {
        val runs = (node as? JsonObject)?.get("runs")?.jsonArray ?: return ""
        return runs.joinToString("") {
            (it.jsonObject["text"] as? JsonPrimitive)?.contentOrNullSafe() ?: ""
        }
    }

    private fun bestThumb(node: JsonElement): String? =
        findAll(node, "thumbnail")
            .asSequence()
            .mapNotNull { it["thumbnails"]?.jsonArray }
            .flatMap { it.asSequence() }
            .mapNotNull { t ->
                val o = t.jsonObject
                val url = (o["url"] as? JsonPrimitive)?.contentOrNullSafe() ?: return@mapNotNull null
                val w = (o["width"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                w to url
            }
            .maxByOrNull { it.first }?.second

    private fun parseDuration(text: String): Int? {
        val parti = text.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parti.size) {
            2 -> parti[0] * 60 + parti[1]
            3 -> parti[0] * 3600 + parti[1] * 60 + parti[2]
            else -> null
        }
    }

    fun close() = http.close()

    companion object {
        private const val BASE = "https://music.youtube.com/youtubei/v1"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:88.0) Gecko/20100101 Firefox/88.0"
        private val DURATA = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
        private val ANNO = Regex("""(19|20)\d{2}""")
    }
}
