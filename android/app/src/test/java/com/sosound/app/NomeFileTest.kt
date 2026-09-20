package com.sosound.app

import com.sosound.app.data.storage.MusicStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rileggere il nome di un file di brano.
 *
 * E' la strada che rimette in piedi una libreria quando l'indice non
 * c'e'. Se sbaglia, il brano entra con l'artista sbagliato o non entra
 * affatto — e chi sta reinstallando non ha modo di accorgersene se non
 * guardando l'elenco brano per brano.
 */
class NomeFileTest {

    @Test
    fun `un nome normale si divide nei suoi pezzi`() {
        val n = MusicStorage.leggiNome("Daft Punk - Around the World [dQw4w9WgXcQ].m4a")
        assertNotNull(n); n!!
        println("--- ${n.artista} / ${n.titolo} / ${n.videoId} ---")
        assertEquals("Daft Punk", n.artista)
        assertEquals("Around the World", n.titolo)
        assertEquals("dQw4w9WgXcQ", n.videoId)
        assertEquals("m4a", n.estensione)
    }

    @Test
    fun `un trattino nel titolo non sposta il taglio`() {
        // Il separatore giusto e' il primo: tagliare sull'ultimo darebbe
        // "Prince - 1999" come artista e "Live" come titolo.
        val n = MusicStorage.leggiNome("Prince - 1999 - Live [abcdefghijk].webm")
        assertNotNull(n); n!!
        assertEquals("Prince", n.artista)
        assertEquals("1999 - Live", n.titolo)
    }

    @Test
    fun `le parentesi quadre dentro il titolo non confondono`() {
        // Si legge l'ULTIMA parentesi aperta: e' quella che contiene
        // l'identificativo, non quella che fa parte del titolo.
        val n = MusicStorage.leggiNome("Tizio - Brano [Remix] [_-Ab12cd34E].opus")
        assertNotNull(n); n!!
        assertEquals("Brano [Remix]", n.titolo)
        assertEquals("_-Ab12cd34E", n.videoId)
    }

    @Test
    fun `senza artista il brano entra comunque`() {
        val n = MusicStorage.leggiNome("Solo un titolo [dQw4w9WgXcQ].mp3")
        assertNotNull(n); n!!
        assertEquals("Artista sconosciuto", n.artista)
        assertEquals("Solo un titolo", n.titolo)
    }

    @Test
    fun `un file che non e' nostro viene lasciato dov'e'`() {
        // Un mp3 copiato dentro a mano non ha identificativo, e
        // inventarlo vorrebbe dire mettere in libreria un brano che
        // l'app non sapra' mai ne' riscaricare ne' riconoscere.
        for (estraneo in listOf(
            "canzone.mp3",
            "Tizio - Brano.mp3",
            "Tizio - Brano [troppo-corto].mp3",
            "Tizio - Brano [dQw4w9WgXcQ]",          // senza estensione
            "Tizio - Brano [dQw4w9WgXcQ.mp3",       // parentesi non chiusa
            "[dQw4w9WgXcQ].mp3",                    // solo l'identificativo
            "sosound.json",
            "",
        )) assertNull("accettato: $estraneo", MusicStorage.leggiNome(estraneo))
    }

    @Test
    fun `quello che si scrive si rilegge`() {
        // La prova che conta: il nome lo produce l'app, e deve tornare
        // indietro uguale.
        for ((artista, titolo) in listOf(
            "Rosalía" to "MALAMENTE",
            "AC/DC" to "Back in Black",
            "Ludovico Einaudi" to "Nuvole bianche",
            "Sigur Rós" to "Hoppípolla",
        )) {
            val nome = MusicStorage.nomeFileDiProva(artista, titolo, "dQw4w9WgXcQ", "m4a")
            val riletto = MusicStorage.leggiNome(nome)
            assertNotNull("non rileggibile: $nome", riletto)
            println("--- $nome -> ${riletto!!.artista} / ${riletto.titolo} ---")
            assertEquals(titolo, riletto.titolo)
            assertEquals("dQw4w9WgXcQ", riletto.videoId)
        }
    }
}
