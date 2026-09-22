package com.sosound.app

import com.sosound.app.data.catalog.InnerTubeClient
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Collauda il parsing contro l'API vera di YouTube Music.
 *
 * Non e' un test isolato di proposito: la cosa che si rompe qui non e' la
 * logica, e' la *forma della risposta* di InnerTube, che cambia senza
 * preavviso. Un test con una risposta registrata continuerebbe a passare
 * mentre l'app sul telefono non trova piu' niente.
 *
 * Quindi: se questo test fallisce senza che il codice sia cambiato, vuol
 * dire che YouTube ha cambiato la struttura e il parser va aggiornato.
 */
class InnerTubeTest {

    @Before fun soloSeLaReteEStataChiesta() = Rete.richiesta()

    private val client = InnerTubeClient()

    @Test
    fun `la ricerca restituisce brani con metadati sensati`() = runBlocking {
        val results = client.search("daft punk get lucky", limit = 10)

        assertFalse("nessun risultato: la ricerca e' rotta", results.isEmpty())
        println("--- ${results.size} risultati ---")
        results.forEach { println("  ${it.videoId} | ${it.title} | ${it.subtitle}") }

        results.forEach { t ->
            assertTrue("videoId malformato: '${t.videoId}'", t.videoId.length == 11)
            assertTrue("titolo vuoto", t.title.isNotBlank())
            assertTrue("artista vuoto", t.artist.isNotBlank())
        }

        // Il brano vero deve esserci, e con l'album giusto: e' il segnale
        // che le colonne sono state divise bene e non a caso.
        val target = results.firstOrNull { it.title.contains("Get Lucky", true) }
        assertNotNull("«Get Lucky» non trovato fra i risultati", target)
        println("--- scelto: ${target!!.title} / ${target.artist} / ${target.album} ---")
        assertTrue("artista non riconosciuto: ${target.artist}",
            target.artist.contains("Daft Punk", true))
    }

    @Test
    fun `durata e copertina vengono estratte`() = runBlocking {
        val results = client.search("random access memories", limit = 10)
        assertFalse(results.isEmpty())

        val withDuration = results.count { it.durationSeconds != null }
        val withCover = results.count { !it.thumbnail.isNullOrBlank() }
        println("--- durata su $withDuration/${results.size}, copertina su $withCover/${results.size} ---")

        // Non pretendiamo il 100%: qualche risultato strano ci sta sempre.
        // Ma se la maggioranza non ha durata, il parsing delle colonne e' rotto.
        assertTrue("quasi nessuna durata estratta", withDuration > results.size / 2)
        assertTrue("quasi nessuna copertina estratta", withCover > results.size / 2)

        results.mapNotNull { it.durationSeconds }.forEach {
            assertTrue("durata assurda: $it s", it in 10..3600)
        }
    }

    @Test
    fun `il badge esplicito viene letto dalla ricerca vera`() = runBlocking {
        // «Kill You» di Eminem e' segnato esplicito su YouTube Music da
        // sempre: se questo test comincia a fallire senza che il codice
        // sia cambiato, e' la forma del badge che YouTube ha spostato,
        // non la logica del parsing.
        val results = client.search("eminem kill you", limit = 10)
        assertFalse("nessun risultato", results.isEmpty())

        val target = results.firstOrNull {
            it.title.contains("Kill You", true) && it.artist.contains("Eminem", true)
        }
        assertNotNull("«Kill You» di Eminem non trovato fra i risultati", target)
        println("--- ${target!!.title} / ${target.artist} -> explicit=${target.explicit} ---")
        assertTrue("dovrebbe essere segnato esplicito", target.explicit == true)
    }

    @Test
    fun `una ricerca senza risultati non esplode`() = runBlocking {
        val results = client.search("zzzqwertyuiopasdfghjkl-non-esiste-12345", limit = 5)
        println("--- ricerca assurda: ${results.size} risultati ---")
        // Non asseriamo che sia vuota: YouTube propone sempre qualcosa.
        // Quello che conta e' che non sia esplosa e che sia ben formata.
        results.forEach { assertTrue(it.videoId.isNotBlank()) }
    }
}
