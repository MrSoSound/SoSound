package com.sosound.app

import com.sosound.app.playback.Formato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Riconoscere il formato di un file audio dai primi byte.
 *
 * Serve a promuovere un brano dalla cache a file vero senza
 * riscaricarlo: la cache lo conosce per identificativo e non per nome,
 * quindi l'estensione va dedotta. **Dedurla male vuol dire scrivere un
 * file che nessun lettore aprira' piu'** — e l'errore si scoprirebbe
 * giorni dopo, provando ad ascoltarlo in aereo.
 */
class FormatoTest {

    private fun byte(vararg valori: Int, lunghezza: Int = 16) =
        ByteArray(lunghezza) { i -> if (i < valori.size) valori[i].toByte() else 0 }

    @Test
    fun `m4a si riconosce dal riquadro ftyp, non dal primo byte`() {
        // I primi quattro byte sono la LUNGHEZZA del riquadro e cambiano
        // da file a file: guardare l'inizio non direbbe niente.
        val conLunghezza20 = byte(0, 0, 0, 0x20, 0x66, 0x74, 0x79, 0x70)
        val conLunghezza24 = byte(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70)
        assertEquals("m4a", Formato.daiPrimiByte(conLunghezza20))
        assertEquals("m4a", Formato.daiPrimiByte(conLunghezza24))
    }

    @Test
    fun `webm si riconosce dalla firma EBML`() {
        assertEquals("webm", Formato.daiPrimiByte(byte(0x1A, 0x45, 0xDF, 0xA3)))
    }

    @Test
    fun `opus sta dentro un contenitore Ogg`() {
        assertEquals("opus", Formato.daiPrimiByte(byte(0x4F, 0x67, 0x67, 0x53)))
    }

    @Test
    fun `mp3 sia con l'etichetta ID3 sia senza`() {
        assertEquals("mp3", Formato.daiPrimiByte(byte(0x49, 0x44, 0x33, 0x04)))
        // Un fotogramma nudo: undici bit a uno.
        assertEquals("mp3", Formato.daiPrimiByte(byte(0xFF, 0xFB, 0x90, 0x00)))
        assertEquals("mp3", Formato.daiPrimiByte(byte(0xFF, 0xF3, 0x00, 0x00)))
    }

    @Test
    fun `quello che non si riconosce resta senza nome`() {
        // Meglio rinunciare che inventare: con un'estensione sbagliata il
        // file finisce in libreria e non si apre.
        assertNull(Formato.daiPrimiByte(byte(0x00, 0x01, 0x02, 0x03)))
        assertNull(Formato.daiPrimiByte("non sono audio".toByteArray()))
        // Troppo corto per dire qualcosa.
        assertNull(Formato.daiPrimiByte(byte(0x1A, 0x45, lunghezza = 4)))
        assertNull(Formato.daiPrimiByte(ByteArray(0)))
    }

    @Test
    fun `un file che comincia per ftyp ma e' troppo corto non passa`() {
        assertNull(Formato.daiPrimiByte(byte(0, 0, 0, 0x20, 0x66, 0x74, lunghezza = 8)))
    }
}
