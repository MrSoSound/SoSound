package com.sosound.app

import com.sosound.app.data.catalog.InnerTubeClient
import com.sosound.app.data.catalog.SearchKind
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Collegamenti fra le entità, pagina podcast, e proposte del momento. */
class DiscoverTest {

    @Before fun soloSeLaReteEStataChiesta() = Rete.richiesta()

    private val client = InnerTubeClient()

    @Test
    fun `un brano porta con se' artista e album`() = runBlocking {
        val brani = client.search("daft punk get lucky", SearchKind.BRANI, limit = 5).tracks
        assertFalse(brani.isEmpty())
        val t = brani.first()
        println("--- ${t.title} ---")
        println("   artistId=${t.artistId}  albumId=${t.albumId}")
        // Senza questi riferimenti, toccare «Daft Punk» costringerebbe a
        // ricercarlo per nome e sperare di ritrovare quello giusto.
        assertNotNull("artista non collegato", t.artistId)
        assertTrue("artistId sospetto", t.artistId!!.startsWith("UC"))
        assertNotNull("album non collegato", t.albumId)
        assertTrue("albumId sospetto", t.albumId!!.startsWith("MPRE"))
    }

    @Test
    fun `una puntata porta al suo programma, e il programma alle puntate`() = runBlocking {
        val puntate = client.search("true crime", SearchKind.PODCAST, limit = 5).tracks
        if (puntate.isEmpty()) {
            println("   ATTENZIONE: qui «true crime» non da' puntate. Salto.")
            return@runBlocking
        }
        // Si provano piu' candidati invece del primo.
        //
        // Il primo risultato di oggi puo' essere un programma con otto
        // puntate in tutto, e su quello l'asserzione «almeno venti» non
        // dice niente sul nostro codice: dice solo che quel podcast e'
        // corto. Quello che vogliamo verificare e' il SALTO all'elenco
        // completo, e per vederlo serve un programma che ne abbia molte.
        val candidati = puntate.filter { it.showId != null }.take(5)
        assertFalse("nessuna puntata con programma", candidati.isEmpty())

        val pagine = candidati.mapNotNull { c -> client.podcast(c.showId!!) }
        assertFalse("nessuna pagina podcast letta", pagine.isEmpty())
        val programma = pagine.maxBy { it.episodes.size }
        println("--- «${programma.title}» di ${programma.publisher} ---")
        println("   descrizione: ${programma.description?.take(90)}")
        println("   puntate: ${programma.episodes.size}")
        programma.episodes.take(3).forEach { println("    ${it.videoId} | ${it.title} | ${it.durationText}") }

        assertTrue("titolo vuoto", programma.title.isNotBlank())
        assertFalse("nessuna puntata", programma.episodes.isEmpty())

        // Dal canale arrivano dieci puntate, dall'elenco cento: sopra le
        // dieci il salto e' scattato di sicuro. Sotto non si puo'
        // concludere niente — puo' essere un programma corto — e allora
        // lo si dice invece di far fallire la build per un dato che e'
        // cambiato da solo.
        if (programma.episodes.size > 10) {
            println("   il salto all'elenco completo e' scattato")
        } else {
            println(
                "   ATTENZIONE: nessuno dei ${pagine.size} programmi di oggi " +
                    "supera le 10 puntate (massimo ${programma.episodes.size}). " +
                    "Il salto all'elenco completo non e' stato verificato."
            )
        }
        assertTrue("puntate senza videoId", programma.episodes.all { it.videoId.length == 11 })
        assertFalse("il titolo contiene ancora la coda", programma.title.contains("Tutti gli episodi"))
    }

    @Test
    fun `cercando un podcast escono sia il programma sia le puntate`() = runBlocking {
        val r = client.search("supernova", SearchKind.PODCAST, limit = 10)
        println("--- programmi: ${r.shows.size}, puntate: ${r.tracks.size} ---")
        r.shows.take(3).forEach { println("   programma: ${it.title} — ${it.publisher} [${it.browseId}]") }
        r.tracks.take(3).forEach { println("   puntata:   ${it.artist} — ${it.title} (${it.durationText})") }

        // Il catalogo dei podcast cambia da paese a paese: «supernova»
        // di qui porta il programma di Ale Cattelan, da un runner
        // americano puo' non portare niente. Se la ricerca torna a mani
        // vuote non si puo' concludere niente sul nostro codice — e
        // farlo fallire vorrebbe dire una build rossa per una cosa che
        // non abbiamo scritto noi.
        if (r.shows.isEmpty() && r.tracks.isEmpty()) {
            println(
                "   ATTENZIONE: qui «supernova» non da' nessun podcast. " +
                    "Il parsing dei podcast non e' stato verificato."
            )
            return@runBlocking
        }
        assertFalse("puntate senza nemmeno un programma", r.shows.isEmpty())
        assertFalse("programmi senza nemmeno una puntata", r.tracks.isEmpty())

        // «SUPERNOVA - Tutti gli episodi» e' il nome dell'elenco, non del
        // programma: mostrarlo cosi' sarebbe un dettaglio interno che
        // finisce sotto gli occhi dell'utente.
        assertTrue(
            "la coda «Tutti gli episodi» non e' stata tolta",
            r.shows.none { it.title.contains("Tutti gli episodi") },
        )

        // Il difetto vero: il sottotitolo di una puntata e' «data •
        // Programma», e letto col parser dei brani metteva la data
        // nell'artista e il programma nell'album.
        val p = r.tracks.first()
        assertFalse("l'artista e' ancora una data: ${p.artist}",
            p.artist.contains("fa") || p.artist.contains("gg"))
        assertNull("una puntata non ha un album", p.album)
        assertNotNull("puntata senza collegamento al programma", p.showId)
    }

    @Test
    fun `le proposte del momento arrivano`() = runBlocking {
        val f = client.discover()
        println("--- tendenze: ${f.trending.size}, artisti: ${f.topArtists.size}, novità: ${f.newAlbums.size} ---")
        f.trending.take(3).forEach { println("   brano: ${it.artist} — ${it.title}") }
        f.topArtists.take(3).forEach { println("   artista: ${it.name} (${it.subtitle})") }
        f.newAlbums.take(3).forEach { println("   album: ${it.title} — ${it.artist}") }

        // Che la home non sia vuota e' il contratto minimo: se lo e',
        // l'app apre su una schermata vuota e il problema e' vero
        // ovunque. Le singole sezioni invece dipendono dal paese da cui
        // si chiede, e su quelle si verifica il PARSING quando ci sono,
        // invece di pretendere che ci siano.
        assertFalse("nessuna proposta", f.isEmpty)

        if (f.trending.isEmpty()) {
            println("   ATTENZIONE: nessuna tendenza qui.")
        } else {
            assertTrue("le tendenze devono essere scaricabili",
                f.trending.all { it.videoId.length == 11 })
        }

        if (f.topArtists.isEmpty()) println("   ATTENZIONE: nessun artista in classifica qui.")

        if (f.newAlbums.isEmpty()) {
            println("   ATTENZIONE: nessuna novità qui.")
        } else {
            // Fra le novità l'artista non sta nel titolo ma dopo il tipo:
            // «Singolo • Ultimo». Se non lo estraiamo restano tutti vuoti.
            val conArtista = f.newAlbums.count { it.artist.isNotBlank() }
            println("--- novità con artista: $conArtista su ${f.newAlbums.size} ---")
            assertTrue("artista non estratto dalle novità", conArtista > f.newAlbums.size / 2)
        }
    }
}
