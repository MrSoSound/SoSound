package com.sosound.app

import com.sosound.app.data.catalog.InnerTubeClient
import com.sosound.app.data.catalog.SearchKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ricerca per tipo e pagine di album e artista, contro l'API vera.
 *
 * Come per gli altri test sul catalogo: la struttura di queste risposte
 * cambia senza preavviso, e un test su una risposta registrata
 * continuerebbe a passare mentre l'app non trova piu' niente.
 */
class CatalogBrowseTest {

    private val client = InnerTubeClient()

    @Test
    fun `cerca album e ne apre uno`() = runBlocking {
        val esito = client.search("daft punk random access memories", SearchKind.ALBUM, limit = 5)
        println("--- album trovati: ${esito.albums.size} ---")
        esito.albums.take(3).forEach { println("   ${it.browseId} | ${it.title} — ${it.subtitle}") }
        assertFalse("nessun album", esito.albums.isEmpty())

        // Il browseId di un album comincia per MPREb: se prendessimo
        // quello sbagliato sarebbe un UC..., cioe' l'artista.
        val primo = esito.albums.first()
        assertTrue("browseId sospetto: ${primo.browseId}", primo.browseId.startsWith("MPRE"))

        val pagina = client.album(primo.browseId)
        assertNotNull("pagina album non letta", pagina)
        pagina!!
        println("--- «${pagina.title}» di ${pagina.artist} (${pagina.year}) · ${pagina.info} ---")
        pagina.tracks.take(4).forEach { println("   ${it.videoId} | ${it.title} | ${it.durationText}") }

        assertTrue("album senza tracce", pagina.tracks.size >= 5)
        assertTrue("artista mancante", pagina.artist.isNotBlank() && pagina.artist != "Sconosciuto")
        // Dentro un album l'artista non c'e' nella riga: va ereditato,
        // altrimenti ogni traccia risulterebbe «Sconosciuto».
        assertTrue(
            "artista non ereditato nelle tracce",
            pagina.tracks.all { it.artist == pagina.artist },
        )
        assertTrue(
            "album non ereditato nelle tracce",
            pagina.tracks.all { it.album == pagina.title },
        )
        val conDurata = pagina.tracks.count { it.durationSeconds != null }
        println("--- tracce con durata: $conDurata su ${pagina.tracks.size} ---")
        assertTrue("quasi nessuna durata", conDurata > pagina.tracks.size / 2)
    }

    @Test
    fun `cerca artisti e ne apre uno`() = runBlocking {
        val esito = client.search("daft punk", SearchKind.ARTISTI, limit = 5)
        println("--- artisti trovati: ${esito.artists.size} ---")
        esito.artists.take(3).forEach { println("   ${it.browseId} | ${it.name} — ${it.subtitle}") }
        assertFalse("nessun artista", esito.artists.isEmpty())

        val primo = esito.artists.first()
        assertTrue("browseId sospetto: ${primo.browseId}", primo.browseId.startsWith("UC"))

        val pagina = client.artist(primo.browseId)
        assertNotNull("pagina artista non letta", pagina)
        pagina!!
        println("--- ${pagina.name}: ${pagina.topSongs.size} brani, ${pagina.albums.size} album, ${pagina.singles.size} singoli ---")
        pagina.topSongs.take(3).forEach { println("   brano: ${it.title}") }
        pagina.albums.take(3).forEach { println("   album: ${it.title} (${it.year})") }

        assertTrue("nessun brano principale", pagina.topSongs.isNotEmpty())
        assertTrue("nessun album", pagina.albums.isNotEmpty())
        assertTrue(
            "browseId degli album sbagliato",
            pagina.albums.all { it.browseId.startsWith("MPRE") },
        )
    }

    @Test
    fun `cerca episodi di podcast`() = runBlocking {
        val esito = client.search("il post podcast", SearchKind.PODCAST, limit = 8)
        println("--- episodi trovati: ${esito.tracks.size} ---")
        esito.tracks.take(4).forEach { println("   ${it.videoId} | ${it.title} — ${it.artist}") }

        assertFalse("nessun episodio", esito.tracks.isEmpty())
        // Un episodio deve avere un videoId scaricabile come una canzone:
        // e' tutto il motivo per cui si cercano gli episodi e non i
        // programmi.
        assertTrue(
            "videoId malformato",
            esito.tracks.all { it.videoId.length == 11 },
        )
    }

    @Test
    fun `la ricerca di brani continua a funzionare`() = runBlocking {
        // La firma vecchia e' ancora usata dall'importazione: se si
        // rompesse, l'import smetterebbe di trovare qualunque cosa.
        val brani = client.search("blinding lights", limit = 5)
        println("--- brani: ${brani.size} ---")
        assertFalse(brani.isEmpty())
        assertTrue(brani.all { it.videoId.length == 11 })
    }
}
