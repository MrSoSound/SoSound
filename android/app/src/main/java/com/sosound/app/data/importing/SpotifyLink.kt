package com.sosound.app.data.importing

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Cosa abbiamo tirato fuori da un link. */
data class LinkedPlaylist(
    val name: String,
    val rows: List<ImportRow>,
    /**
     * Vero se la pagina ne ha dati esattamente quanti ne da' al massimo.
     *
     * La pagina di anteprima si ferma a cento brani e **non dichiara
     * quanti ne ha in tutto**: da una playlist di centocinquanta ne
     * arrivano cento e nessuno dice che ne mancano cinquanta. Non
     * possiamo prenderli, ma possiamo dirlo — importarne cento in
     * silenzio e' peggio.
     */
    val forsePiuDiCosi: Boolean = false,
)

/**
 * Legge una playlist o un album da un link di Spotify, senza account.
 *
 * Non usa l'API di Spotify, che vorrebbe comunque la registrazione di
 * un'applicazione. Usa la **pagina di anteprima** — quella che si
 * incorpora nei siti — che e' pubblica e porta con se' l'elenco dei
 * brani dentro un blocco JSON.
 *
 * ⚠️ È una pagina web, non un contratto: se Spotify ne cambia la
 * struttura questa lettura smette di funzionare. Per questo il fallimento
 * dice di ripiegare sul file esportato, che invece non dipende da come e'
 * fatta una pagina.
 */
class SpotifyLink {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) { expectSuccess = false }

    suspend fun fetch(url: String): Result<LinkedPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val (tipo, id) = parseUrl(url)
                ?: throw IllegalArgumentException(
                    "Non è un link di Spotify a una playlist o a un album."
                )

            val html = http.get("https://open.spotify.com/embed/$tipo/$id") {
                header("User-Agent", USER_AGENT)
                header("Accept-Language", "it,en;q=0.8")
            }.bodyAsText()

            val blocco = NEXT_DATA.find(html)?.groupValues?.get(1)
                ?: throw IllegalStateException(
                    "Spotify ha risposto in un modo che non riconosco. " +
                        "Prova con il file esportato."
                )

            val entity = findEntity(json.parseToJsonElement(blocco))
                ?: throw IllegalStateException("Nessun brano trovato in questo link.")

            val nome = entity["name"]?.stringOrNull()
                ?: entity["title"]?.stringOrNull()
                ?: "Importata da Spotify"

            val brani = entity["trackList"]?.jsonArray.orEmpty().mapNotNull { it.toRow() }
            if (brani.isEmpty()) throw IllegalStateException("Nessun brano trovato in questo link.")

            LinkedPlaylist(nome, brani, forsePiuDiCosi = brani.size >= TETTO_ANTEPRIMA)
        }
    }

    /** Quanti ne consegna al massimo la pagina di anteprima. Misurato. */
    private val TETTO_ANTEPRIMA = 100

    // ------------------------------------------------------------ dettagli

    private fun parseUrl(url: String): Pair<String, String>? {
        val m = LINK.find(url.trim()) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    /**
     * Cerca il nodo che contiene l'elenco: la struttura della pagina
     * cambia nel tempo, seguire un percorso fisso sarebbe piu' fragile
     * che cercare la chiave dove capita.
     */
    private fun findEntity(node: JsonElement): JsonObject? {
        when (node) {
            is JsonObject -> {
                if (node["trackList"] is JsonArray) return node
                node.values.forEach { v -> findEntity(v)?.let { return it } }
            }
            is JsonArray -> node.forEach { v -> findEntity(v)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun JsonElement.toRow(): ImportRow? {
        val o = this as? JsonObject ?: return null
        val titolo = o["title"]?.stringOrNull()?.trim().orEmpty()
        if (titolo.isBlank()) return null
        // «subtitle» e' l'artista; con piu' artisti sono separati da
        // virgola, e per cercare basta il primo.
        val artista = o["subtitle"]?.stringOrNull()?.split(",")?.first()?.trim().orEmpty()
        return ImportRow(title = titolo, artist = artista)
    }

    private fun JsonElement.stringOrNull(): String? =
        (this as? JsonPrimitive)?.let { if (it.isString) it.content else null }

    fun close() = http.close()

    companion object {
        /** Accetta i link con parametri (`?si=...`) e quelli accorciati. */
        private val LINK = Regex(
            """(?:open\.)?spotify\.com/(?:intl-\w+/)?(playlist|album)/([A-Za-z0-9]+)"""
        )
        private val NEXT_DATA = Regex(
            """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

        /** Se una stringa somiglia a un link di Spotify. */
        fun looksLikeLink(text: String) = LINK.containsMatchIn(text)
    }
}
