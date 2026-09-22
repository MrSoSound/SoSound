package com.sosound.app

import com.sosound.app.data.importing.YtMusicLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Legge le playlist dai link di YouTube Music senza account.
 *
 * ⚠️ Questo test interroga l'API vera di YouTube Music, e per questo e'
 * fragile di proposito: se la forma della risposta cambia deve fallire
 * qui, non sul telefono di chi prova a importare.
 */
class YtMusicLinkTest {

    private val link = YtMusicLink()

    /** Il link di prova segnalato nel README, senza il tracciante `si=`. */
    private val PLAYLIST_VERA =
        "https://music.youtube.com/playlist?list=PL5LVtetyrWYsFRgihbkWhaV62Qj1H2sDZ"

    @Test
    fun `riconosce i link di YouTube Music`() {
        assertTrue(YtMusicLink.looksLikeLink(PLAYLIST_VERA))
        assertTrue(YtMusicLink.looksLikeLink(
            "https://music.youtube.com/playlist?list=PL5LVtetyrWYsFRgihbkWhaV62Qj1H2sDZ&si=abc123"
        ))
        // Senza il sottodominio «music»: la stessa playlist, un altro link.
        assertTrue(YtMusicLink.looksLikeLink(
            "https://www.youtube.com/playlist?list=PL5LVtetyrWYsFRgihbkWhaV62Qj1H2sDZ"
        ))

        assertFalse(YtMusicLink.looksLikeLink("Daft Punk - Get Lucky"))
        // Un video singolo non e' una playlist, anche se porta un list=
        // nella coda di un link diverso — qui non c'e' proprio «playlist».
        assertFalse(YtMusicLink.looksLikeLink("https://music.youtube.com/watch?v=5NPBIwQyPWE"))
        assertFalse(YtMusicLink.looksLikeLink("https://open.spotify.com/playlist/7KHASfhyJksCaMV8XFFuef"))
    }

    /**
     * Il link di prova del README ha dato 71 brani quando è stato provato.
     * Rifacendo la prova oggi la playlist esiste ancora ma è stata
     * svuotata dal suo proprietario: si chiama «MOBU UMAMI» e ne ha 9 —
     * verificato sia con questa chiamata sia con la pagina normale di
     * YouTube. Non è un difetto del lettore: è la playlist di qualcun
     * altro, che può cambiare in ogni momento. Qui si controlla comunque
     * che quello che arriva sia ben formato; la prova che il lettore
     * regge una playlist grande sta nel test sotto, su una playlist
     * ufficiale che non ci si aspetta venga svuotata.
     */
    @Test
    fun `legge la playlist di prova del README`() = runBlocking {
        val esito = link.fetch(PLAYLIST_VERA)
        val lista = esito.getOrNull()
        assertTrue("lettura fallita: ${esito.exceptionOrNull()?.message}", lista != null)
        lista!!

        println("--- «${lista.name}»: ${lista.tracks.size} brani ---")
        lista.tracks.forEach { println("   ${it.artist} — ${it.title}") }

        assertTrue("nessun brano letto", lista.tracks.isNotEmpty())
        assertTrue("nome vuoto", lista.name.isNotBlank())
        lista.tracks.forEach { t ->
            assertTrue("videoId malformato: '${t.videoId}'", t.videoId.length == 11)
            assertTrue("titolo vuoto", t.title.isNotBlank())
            assertTrue("artista vuoto", t.artist.isNotBlank())
        }
    }

    /**
     * Una playlist ufficiale e grande, per verificare che il lettore
     * regge un numero di brani vicino a quello che dava la playlist del
     * README quando è stata provata la prima volta.
     */
    @Test
    fun `legge una playlist pubblica grande`() = runBlocking {
        val esito = link.fetch("https://music.youtube.com/playlist?list=PL0jp-uZ7a4g9FQWW5R_u0pz4yzV4RiOXu")
        val lista = esito.getOrNull()
        assertTrue("lettura fallita: ${esito.exceptionOrNull()?.message}", lista != null)
        lista!!

        println("--- «${lista.name}»: ${lista.tracks.size} brani ---")
        lista.tracks.take(5).forEach { println("   ${it.artist} — ${it.title}") }

        assertTrue("pochi brani letti: ${lista.tracks.size}", lista.tracks.size > 50)
        assertTrue("nome vuoto", lista.name.isNotBlank())

        lista.tracks.forEach { t ->
            assertTrue("videoId malformato: '${t.videoId}'", t.videoId.length == 11)
            assertTrue("titolo vuoto", t.title.isNotBlank())
            assertTrue("artista vuoto", t.artist.isNotBlank())
        }
    }

    @Test
    fun `un link sbagliato da un errore leggibile`() = runBlocking {
        val esito = link.fetch("https://esempio.it/una-cosa")
        assertTrue("doveva fallire", esito.isFailure)
        val msg = esito.exceptionOrNull()?.message.orEmpty()
        println("--- errore: $msg ---")
        assertTrue("messaggio inutile: «$msg»", msg.contains("YouTube", true))
    }
}
