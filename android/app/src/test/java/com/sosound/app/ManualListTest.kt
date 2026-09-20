package com.sosound.app

import com.sosound.app.data.importing.PlaylistFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la frase che l'app dice all'utente: «funziona anche con un
 * file esportato, o con un elenco scritto a mano».
 *
 * La seconda meta' e' una promessa, e una promessa nell'interfaccia va
 * verificata come il codice: il tasto «scegli il file» e il campo
 * «incolla» passano dallo stesso lettore, quindi se regge un elenco
 * battuto a mano regge in entrambi i casi.
 */
class ManualListTest {

    private fun mostra(nome: String, testo: String) {
        val righe = PlaylistFile.parse(testo)
        println("--- $nome: ${righe.size} righe ---")
        righe.forEach { println("   «${it.artist}» / «${it.title}»") }
    }

    @Test
    fun `un elenco battuto a mano, nelle forme in cui la gente lo scrive`() {
        val testo = """
            Daft Punk - Get Lucky
            The Weeknd – Blinding Lights
            Queen — Bohemian Rhapsody
            Imagine by John Lennon
            1. Nirvana - Smells Like Teen Spirit
            2) Pink Floyd - Wish You Were Here
        """.trimIndent()

        mostra("trattini, numerazione, «by»", testo)
        val righe = PlaylistFile.parse(testo)
        assertEquals(6, righe.size)
        assertEquals("Daft Punk", righe[0].artist)
        assertEquals("Get Lucky", righe[0].title)
        // Trattino lungo e lunghissimo: chi copia da una pagina web se li
        // ritrova senza accorgersene.
        assertEquals("The Weeknd", righe[1].artist)
        assertEquals("Queen", righe[2].artist)
        assertEquals("John Lennon", righe[3].artist)
        // La numerazione va tolta, se no «1. Nirvana» diventa l'artista.
        assertEquals("Nirvana", righe[4].artist)
        assertEquals("Pink Floyd", righe[5].artist)
    }

    @Test
    fun `solo titoli, senza artista`() {
        val testo = "Bohemian Rhapsody\nImagine\nYesterday"
        mostra("solo titoli", testo)
        val righe = PlaylistFile.parse(testo)
        assertEquals(3, righe.size)
        assertTrue("l'artista dovrebbe restare vuoto", righe.all { it.artist.isBlank() })
    }

    @Test
    fun `una virgola dentro una riga non la fa scambiare per un CSV`() {
        val testo = """
            Mark Ronson - Uptown Funk, feat. Bruno Mars
            Queen - Bohemian Rhapsody
        """.trimIndent()
        mostra("con virgola", testo)
        val righe = PlaylistFile.parse(testo)
        assertEquals(2, righe.size)
        assertEquals("Mark Ronson", righe[0].artist)
    }

    @Test
    fun `una riga che somiglia a un'intestazione non deve ingannare`() {
        // Caso limite trovato ragionando sul lettore: se la prima riga
        // contiene per caso le parole che cerchiamo nelle intestazioni
        // CSV, il file verrebbe letto come tabella e la prima riga
        // buttata via come intestazione.
        val testo = """
            Titolo, Artista
            Get Lucky, Daft Punk
            Blinding Lights, The Weeknd
        """.trimIndent()
        mostra("intestazione in italiano", testo)
        val righe = PlaylistFile.parse(testo)
        // Qui e' GIUSTO leggerlo come tabella: l'utente ha scritto
        // un'intestazione, quindi intendeva proprio quello.
        assertEquals(2, righe.size)
        assertEquals("Get Lucky", righe[0].title)
        assertEquals("Daft Punk", righe[0].artist)
    }

    @Test
    fun `un elenco vuoto o di rumore non produce niente`() {
        assertTrue(PlaylistFile.parse("").isEmpty())
        assertTrue(PlaylistFile.parse("   \n\n  \n").isEmpty())
    }
}
