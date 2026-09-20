package com.sosound.app.data.importing

/** Una riga letta dal file esportato: quello che sappiamo del brano. */
data class ImportRow(
    val title: String,
    val artist: String,
    val album: String? = null,
)

/**
 * Legge le playlist esportate dagli altri servizi.
 *
 * Nessuna autenticazione, nessuna API: si parte dal file che gli
 * strumenti di esportazione producono gia'. I due piu' usati per Spotify
 * sono Exportify e TuneMyMusic, che scrivono CSV con intestazioni
 * diverse — quindi le colonne si cercano per nome invece di fidarsi
 * della posizione.
 *
 * In mancanza di un CSV si accetta anche testo semplice, una riga per
 * brano nella forma «Artista - Titolo»: e' il formato in cui la gente
 * incolla le playlist nei messaggi.
 */
object PlaylistFile {

    /** Come si chiama la colonna del titolo nei vari esportatori. */
    private val TITOLO = listOf("track name", "title", "titolo", "song", "name", "brano", "canzone")
    private val ARTISTA = listOf("artist name(s)", "artist name", "artist", "artista", "artists", "interprete")
    private val ALBUM = listOf("album name", "album")

    fun parse(text: String): List<ImportRow> {
        val righe = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (righe.isEmpty()) return emptyList()

        val comeCsv = parseCsv(righe)
        // Se il CSV non ha dato niente di utile e' probabile che non sia
        // un CSV: si riprova leggendolo come «Artista - Titolo».
        return comeCsv.ifEmpty { parsePlainText(righe) }
    }

    // ------------------------------------------------------------------ csv

    private fun parseCsv(righe: List<String>): List<ImportRow> {
        val intestazione = splitCsvLine(righe.first()).map { it.trim().lowercase() }
        if (intestazione.size < 2) return emptyList()

        val iTitolo = trovaColonna(intestazione, TITOLO) ?: return emptyList()
        val iArtista = trovaColonna(intestazione, ARTISTA) ?: return emptyList()
        val iAlbum = trovaColonna(intestazione, ALBUM)

        return righe.drop(1).mapNotNull { riga ->
            val campi = splitCsvLine(riga)
            val titolo = campi.getOrNull(iTitolo)?.trim().orEmpty()
            val artista = campi.getOrNull(iArtista)?.trim().orEmpty()
            if (titolo.isBlank()) return@mapNotNull null
            ImportRow(
                title = titolo,
                // Exportify separa piu' artisti con una virgola dentro il
                // campo: teniamo il primo, e' quello che conta per cercare.
                artist = artista.split(",").first().trim(),
                album = iAlbum?.let { campi.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun trovaColonna(intestazione: List<String>, nomi: List<String>): Int? {
        // Prima la corrispondenza esatta, poi quella parziale: cosi'
        // «album name» non viene catturato da chi cerca «name».
        nomi.forEach { n ->
            val i = intestazione.indexOf(n)
            if (i >= 0) return i
        }
        nomi.forEach { n ->
            val i = intestazione.indexOfFirst { it.contains(n) }
            if (i >= 0) return i
        }
        return null
    }

    /**
     * Divide una riga CSV rispettando le virgolette.
     *
     * Serve davvero: i titoli contengono virgole («Hello, Goodbye») e uno
     * split ingenuo su virgola spezzerebbe il campo a meta', spostando
     * tutte le colonne successive.
     */
    fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val corrente = StringBuilder()
        var dentroVirgolette = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && dentroVirgolette && i + 1 < line.length && line[i + 1] == '"' -> {
                    // Due virgolette di fila dentro un campo sono una
                    // virgoletta vera, non la fine del campo.
                    corrente.append('"'); i++
                }
                c == '"' -> dentroVirgolette = !dentroVirgolette
                c == ',' && !dentroVirgolette -> {
                    out += corrente.toString(); corrente.clear()
                }
                else -> corrente.append(c)
            }
            i++
        }
        out += corrente.toString()
        return out
    }

    // ---------------------------------------------------------- testo libero

    private val SEPARATORI = listOf(" - ", " – ", " — ", " · ", " by ")

    private fun parsePlainText(righe: List<String>): List<ImportRow> =
        righe.mapNotNull { riga ->
            // Via la numerazione iniziale, che c'e' quasi sempre quando
            // qualcuno incolla una classifica.
            val pulita = riga.replace(Regex("""^\s*\d+[.)\-]\s+"""), "")
            val sep = SEPARATORI.firstOrNull { pulita.contains(it, ignoreCase = true) }
            if (sep != null) {
                val parti = pulita.split(sep, ignoreCase = true, limit = 2)
                val a = parti[0].trim()
                val b = parti.getOrNull(1)?.trim().orEmpty()
                if (a.isBlank() || b.isBlank()) null
                // «Artista - Titolo» e' l'ordine piu' comune quando si
                // incolla; «by» inverte i ruoli.
                else if (sep.equals(" by ", true)) ImportRow(title = a, artist = b)
                else ImportRow(title = b, artist = a)
            } else {
                // Nessun separatore: e' solo un titolo. Si cerchera' senza
                // artista, con una confidenza piu' bassa.
                pulita.takeIf { it.length in 2..120 }?.let { ImportRow(title = it, artist = "") }
            }
        }
}
