package com.sosound.app

import com.sosound.app.data.catalog.InnerTubeClient
import com.sosound.app.data.importing.Confidence
import com.sosound.app.data.importing.ImportRow
import com.sosound.app.data.importing.PlaylistFile
import com.sosound.app.data.importing.TrackMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFileTest {

    @Test
    fun `legge il CSV di Exportify`() {
        val csv = """
            Track URI,Track Name,Artist Name(s),Album Name,Duration (ms)
            spotify:track:abc,Get Lucky,"Daft Punk, Pharrell Williams",Random Access Memories,369626
            spotify:track:def,Instant Crush,"Daft Punk, Julian Casablancas",Random Access Memories,337560
        """.trimIndent()

        val righe = PlaylistFile.parse(csv)
        assertEquals(2, righe.size)
        assertEquals("Get Lucky", righe[0].title)
        // Piu' artisti nello stesso campo: si tiene il primo.
        assertEquals("Daft Punk", righe[0].artist)
        assertEquals("Random Access Memories", righe[0].album)
        // 369626 ms: la colonna e' in millisecondi, si legge in secondi.
        assertEquals(369, righe[0].durationSeconds)
    }

    @Test
    fun `legge il CSV di TuneMyMusic`() {
        val csv = """
            Track name,Artist name,Album,Playlist name,Type
            Blinding Lights,The Weeknd,After Hours,Preferiti,track
        """.trimIndent()
        val righe = PlaylistFile.parse(csv)
        assertEquals(1, righe.size)
        assertEquals("Blinding Lights", righe[0].title)
        assertEquals("The Weeknd", righe[0].artist)
    }

    @Test
    fun `una virgola dentro il titolo non sposta le colonne`() {
        // È il difetto classico di chi divide su virgola senza guardare
        // le virgolette: tutte le colonne dopo slittano di uno.
        val csv = """
            Track Name,Artist Name,Album Name
            "Hello, Goodbye",The Beatles,Magical Mystery Tour
        """.trimIndent()
        val righe = PlaylistFile.parse(csv)
        assertEquals("Hello, Goodbye", righe[0].title)
        assertEquals("The Beatles", righe[0].artist)
        assertEquals("Magical Mystery Tour", righe[0].album)
    }

    @Test
    fun `le virgolette doppie dentro un campo restano una sola`() {
        // Le stringhe grezze non servono qui: le virgolette annidate le
        // chiuderebbero a meta'.
        val riga = "a,\"lui disse \"\"ciao\"\"\",b"
        val campi = PlaylistFile.splitCsvLine(riga)
        assertEquals(listOf("a", "lui disse \"ciao\"", "b"), campi)
    }

    @Test
    fun `legge la colonna Explicit di Exportify`() {
        val csv = """
            Track URI,Track Name,Artist Name(s),Album Name,Duration (ms),Explicit
            spotify:track:abc,Kill You,Eminem,The Marshall Mathers LP,271000,true
            spotify:track:def,Stan (Clean),Eminem,The Marshall Mathers LP,264000,false
        """.trimIndent()

        val righe = PlaylistFile.parse(csv)
        assertEquals(2, righe.size)
        assertEquals(true, righe[0].explicit)
        assertEquals(false, righe[1].explicit)
    }

    @Test
    fun `senza la colonna Explicit il campo resta sconosciuto`() {
        // TuneMyMusic e i CSV piu' vecchi non hanno questa colonna: non
        // deve rompersi, e non deve inventare un "falso" al posto del
        // "non lo so".
        val csv = """
            Track name,Artist name,Album,Playlist name,Type
            Blinding Lights,The Weeknd,After Hours,Preferiti,track
        """.trimIndent()
        val righe = PlaylistFile.parse(csv)
        assertEquals(null, righe[0].explicit)
    }

    @Test
    fun `legge un elenco incollato a mano`() {
        val testo = """
            1. Daft Punk - Get Lucky
            2) The Weeknd – Blinding Lights
            Bohemian Rhapsody by Queen
            Imagine
        """.trimIndent()
        val righe = PlaylistFile.parse(testo)
        assertEquals(4, righe.size)
        assertEquals(ImportRow("Get Lucky", "Daft Punk"), righe[0])
        assertEquals(ImportRow("Blinding Lights", "The Weeknd"), righe[1])
        // «by» inverte l'ordine rispetto al trattino.
        assertEquals(ImportRow("Bohemian Rhapsody", "Queen"), righe[2])
        // Solo titolo: si cerchera' comunque, con meno confidenza.
        assertEquals("Imagine", righe[3].title)
        assertEquals("", righe[3].artist)
    }
}

/**
 * Misura quanto e' affidabile l'abbinamento, contro il catalogo vero.
 *
 * È la domanda aperta di tutta la funzione: senza un numero non si puo'
 * decidere se l'import si possa fidare da solo o debba far rivedere tutto
 * all'utente. Qui si prende un campione di brani scritti come li
 * scriverebbe un file esportato — con «Remastered», «feat.», accenti — e
 * si guarda quanti vengono ritrovati.
 */
class MatchQualityTest {

    private val client = InnerTubeClient()

    /** Righe come compaiono davvero in un export, con l'esito atteso. */
    private val campione = listOf(
        ImportRow("Get Lucky", "Daft Punk") to "Get Lucky",
        ImportRow("Bohemian Rhapsody - Remastered 2011", "Queen") to "Bohemian Rhapsody",
        ImportRow("Blinding Lights", "The Weeknd") to "Blinding Lights",
        ImportRow("Smells Like Teen Spirit", "Nirvana") to "Smells Like Teen Spirit",
        ImportRow("Bella Ciao", "Modena City Ramblers") to "Bella Ciao",
        ImportRow("La Solitudine", "Laura Pausini") to "Solitudine",
        ImportRow("Uptown Funk (feat. Bruno Mars)", "Mark Ronson") to "Uptown Funk",
        ImportRow("Another Brick in the Wall, Pt. 2", "Pink Floyd") to "Another Brick",
        ImportRow("Nel Blu Dipinto Di Blu", "Domenico Modugno") to "Blu",
        ImportRow("Thunderstruck", "AC/DC") to "Thunderstruck",
    )

    @Test
    fun `quanti brani di un export vengono ritrovati`() = runBlocking {
        var sicuri = 0
        var incerti = 0
        var persi = 0
        var giusti = 0

        println("--- soglie: sicuro ≥ ${TrackMatcher.SOGLIA_SICURO}, incerto ≥ ${TrackMatcher.SOGLIA_INCERTO} ---")
        for ((row, atteso) in campione) {
            val risultati = runCatching {
                client.search(TrackMatcher.query(row), limit = 8)
            }.getOrDefault(emptyList())

            val m = TrackMatcher.match(row, risultati)
            val corretto = m.track?.title?.contains(atteso, ignoreCase = true) == true

            when (m.confidence) {
                Confidence.SICURO -> sicuri++
                Confidence.INCERTO -> incerti++
                Confidence.NON_TROVATO -> persi++
            }
            if (corretto) giusti++

            println(
                "  %-42s %-10s %.2f  %s".format(
                    "${row.artist} — ${row.title}".take(42),
                    m.confidence.name.lowercase(),
                    m.score,
                    if (corretto) "✓ ${m.track?.title?.take(38)}"
                    else "✗ ${m.track?.title?.take(38) ?: "niente"}",
                )
            )
        }

        val totale = campione.size
        println("--- sicuri $sicuri, incerti $incerti, non trovati $persi ---")
        println("--- abbinamento corretto: $giusti su $totale ---")

        // Se scende sotto questa soglia, l'import non si puo' piu'
        // fidare da solo e va ripensato: o il catalogo e' cambiato, o il
        // punteggio va ritarato.
        assertTrue(
            "solo $giusti abbinamenti giusti su $totale",
            giusti >= totale * 8 / 10,
        )
        assertTrue("troppi brani persi: $persi", persi <= 1)
    }

    @Test
    fun `il rumore nei titoli non fa sbagliare`() {
        val row = ImportRow("Bohemian Rhapsody - Remastered 2011", "Queen")
        // Stesso brano scritto in tre modi: devono somigliarsi tutti.
        val varianti = listOf(
            "Bohemian Rhapsody",
            "Bohemian Rhapsody (Official Video Remastered)",
            "Bohemian Rhapsody - 2011 Mix",
        )
        for (v in varianti) {
            val fake = com.sosound.app.data.catalog.CatalogTrack(
                videoId = "x".repeat(11), title = v, artist = "Queen",
            )
            val s = TrackMatcher.score(row, fake)
            println("  «$v» -> %.2f".format(s))
            assertTrue("«$v» a %.2f".format(s), s >= TrackMatcher.SOGLIA_SICURO)
        }
    }

    @Test
    fun `a parita' di titolo la durata separa la versione giusta dall'altra`() {
        // «Get Lucky» esiste in piu' incisioni con lo stesso titolo e lo
        // stesso artista: un radio edit e la versione da album. Senza la
        // durata sono indistinguibili; con la durata dichiarata da
        // Spotify (dalla pagina o dal CSV) una delle due si allontana.
        val row = ImportRow("Get Lucky", "Daft Punk", durationSeconds = 369)
        val versioneGiusta = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "a".repeat(11), title = "Get Lucky", artist = "Daft Punk",
            durationSeconds = 369,
        )
        val radioEdit = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "b".repeat(11), title = "Get Lucky", artist = "Daft Punk",
            durationSeconds = 248,
        )
        val giusta = TrackMatcher.score(row, versioneGiusta)
        val edit = TrackMatcher.score(row, radioEdit)
        println("  giusta %.2f, radio edit %.2f".format(giusta, edit))
        assertTrue("la versione con la durata giusta dovrebbe vincere", giusta > edit)
    }

    @Test
    fun `senza durata dichiarata il punteggio non cambia`() {
        // Un elenco incollato a mano non ha mai una durata: deve
        // continuare a funzionare come prima, con l'aggiustamento a zero.
        val row = ImportRow("Get Lucky", "Daft Punk")
        val candidato = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "c".repeat(11), title = "Get Lucky", artist = "Daft Punk",
            durationSeconds = 999,
        )
        assertEquals(0.0, TrackMatcher.durationAdjustment(row, candidato), 0.0)
    }

    @Test
    fun `a parita' di tutto lo stato esplicito uguale vince`() {
        // Due candidati identici per titolo, artista e durata: l'unica
        // differenza e' se corrispondono allo stato esplicito dichiarato
        // dalla riga importata. Deve bastare a separarli, senza dover
        // toccare titolo o artista.
        val row = ImportRow("Kill You", "Eminem", explicit = true)
        val giusto = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "d".repeat(11), title = "Kill You", artist = "Eminem", explicit = true,
        )
        val pulito = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "e".repeat(11), title = "Kill You", artist = "Eminem", explicit = false,
        )
        val sGiusto = TrackMatcher.score(row, giusto)
        val sPulito = TrackMatcher.score(row, pulito)
        println("  esplicito %.3f, pulito %.3f".format(sGiusto, sPulito))
        assertTrue("il candidato con lo stesso stato esplicito dovrebbe vincere", sGiusto > sPulito)
    }

    @Test
    fun `l'aggiustamento esplicito e' leggero e non ribalta un abbinamento chiaro`() {
        val row = ImportRow("Get Lucky", "Daft Punk", explicit = false)
        // Un brano chiaramente diverso, anche se "combacia" sullo stato
        // esplicito: da solo il bonus non deve bastare a farlo passare
        // per un abbinamento buono.
        val altro = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "f".repeat(11), title = "Tutt'altra canzone", artist = "Tutt'altro artista",
            explicit = false,
        )
        assertEquals(0.05, TrackMatcher.explicitAdjustment(row, altro), 0.0001)
        assertTrue(
            "l'aggiustamento da solo non deve rendere sicuro un abbinamento sbagliato",
            TrackMatcher.score(row, altro) < TrackMatcher.SOGLIA_INCERTO,
        )
    }

    @Test
    fun `senza sapere lo stato esplicito da una delle due parti l'aggiustamento e' zero`() {
        val notoEsplicito = ImportRow("Kill You", "Eminem", explicit = true)
        val sconosciuto = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "g".repeat(11), title = "Kill You", artist = "Eminem",
        )
        assertEquals(0.0, TrackMatcher.explicitAdjustment(notoEsplicito, sconosciuto), 0.0)

        val righeSenzaDato = ImportRow("Kill You", "Eminem")
        val candidatoNoto = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "h".repeat(11), title = "Kill You", artist = "Eminem", explicit = true,
        )
        assertEquals(0.0, TrackMatcher.explicitAdjustment(righeSenzaDato, candidatoNoto), 0.0)
    }

    @Test
    fun `un brano diverso con parole simili non passa per sicuro`() {
        val row = ImportRow("Imagine", "John Lennon")
        val esca = com.sosound.app.data.catalog.CatalogTrack(
            videoId = "y".repeat(11),
            title = "Imagine Dragons - Believer",
            artist = "Imagine Dragons",
        )
        val s = TrackMatcher.score(row, esca)
        println("  esca «Imagine Dragons» -> %.2f".format(s))
        assertTrue("l'esca passa a %.2f".format(s), s < TrackMatcher.SOGLIA_SICURO)
    }
}
