package com.sosound.app

import com.sosound.app.data.importing.SpotifyLink
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Legge le playlist dai link di Spotify senza account.
 *
 * ⚠️ Questo test interroga una pagina web vera, e per questo e' fragile
 * di proposito: se Spotify cambia la struttura della pagina di anteprima
 * deve fallire qui, non sul telefono di chi prova a importare. Il
 * fallimento non e' un difetto del nostro codice — e' il segnale che
 * questa strada si e' chiusa e bisogna ripiegare sul file esportato.
 */
class SpotifyLinkTest {

    @Before fun soloSeLaReteEStataChiesta() = Rete.richiesta()

    private val link = SpotifyLink()

    @Test
    fun `riconosce i link di Spotify`() {
        assertTrue(SpotifyLink.looksLikeLink("https://open.spotify.com/playlist/7KHASfhyJksCaMV8XFFuef?si=306ddd"))
        assertTrue(SpotifyLink.looksLikeLink("https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa"))
        // Spotify infila il codice della lingua nel percorso quando
        // condividi dall'app italiana.
        assertTrue(SpotifyLink.looksLikeLink("https://open.spotify.com/intl-it/album/4m2880jivSbbyEGAKfITCa"))

        assertFalse(SpotifyLink.looksLikeLink("Daft Punk - Get Lucky"))
        // Un brano singolo non e' una playlist: va rifiutato invece di
        // farlo passare e non trovare niente.
        assertFalse(SpotifyLink.looksLikeLink("https://open.spotify.com/track/4D7u5KF7SP8"))
    }

    @Test
    fun `legge una playlist pubblica`() = runBlocking {
        val esito = link.fetch("https://open.spotify.com/playlist/7KHASfhyJksCaMV8XFFuef?si=306ddd6c465e48a1")
        val lista = esito.getOrNull()
        assertTrue("lettura fallita: ${esito.exceptionOrNull()?.message}", lista != null)
        lista!!

        println("--- «${lista.name}»: ${lista.rows.size} brani ---")
        lista.rows.take(5).forEach { println("   ${it.artist} — ${it.title}") }

        assertTrue("nessun brano letto", lista.rows.size >= 10)
        assertTrue("nome vuoto", lista.name.isNotBlank())
        // Titolo e artista devono esserci entrambi: senza artista
        // l'abbinamento peggiora molto.
        val senzaArtista = lista.rows.count { it.artist.isBlank() }
        println("--- righe senza artista: $senzaArtista su ${lista.rows.size} ---")
        assertTrue("troppe righe senza artista", senzaArtista <= lista.rows.size / 10)
    }

    @Test
    fun `legge anche un album`() = runBlocking {
        val lista = link.fetch("https://open.spotify.com/album/4m2880jivSbbyEGAKfITCa").getOrNull()
        assertTrue("album non letto", lista != null)
        println("--- album «${lista!!.name}»: ${lista.rows.size} brani ---")
        assertTrue(lista.rows.size >= 5)
    }

    @Test
    fun `un link sbagliato da un errore leggibile`() = runBlocking {
        val esito = link.fetch("https://esempio.it/una-cosa")
        assertTrue("doveva fallire", esito.isFailure)
        val msg = esito.exceptionOrNull()?.message.orEmpty()
        println("--- errore: $msg ---")
        // Il messaggio finisce davanti all'utente: deve dire cosa fare.
        assertTrue("messaggio inutile: «$msg»", msg.contains("Spotify", true))
    }
}
