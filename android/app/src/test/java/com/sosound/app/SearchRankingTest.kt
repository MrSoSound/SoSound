package com.sosound.app

import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.catalog.prioritizeExplicitDuplicates
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica che [prioritizeExplicitDuplicates] sposti l'esplicito sopra il
 * suo duplicato "pulito", senza toccare l'ordine dei brani diversi fra
 * loro — e' un test puro, senza rete: la stessa ragione per cui esiste
 * la funzione, testabile senza passare da InnerTube.
 */
class SearchRankingTest {

    private fun t(videoId: String, title: String, artist: String, explicit: Boolean? = null) =
        CatalogTrack(videoId = videoId, title = title, artist = artist, explicit = explicit)

    @Test
    fun `il duplicato esplicito sale sopra quello pulito`() {
        val pulito = t("a".repeat(11), "Kill You", "Eminem", explicit = false)
        val esplicito = t("b".repeat(11), "Kill You", "Eminem", explicit = true)
        val risultato = prioritizeExplicitDuplicates(listOf(pulito, esplicito))
        assertEquals(listOf(esplicito, pulito), risultato)
    }

    @Test
    fun `se e' gia' l'esplicito a stare sopra non cambia niente`() {
        val esplicito = t("a".repeat(11), "Kill You", "Eminem", explicit = true)
        val pulito = t("b".repeat(11), "Kill You", "Eminem", explicit = false)
        val risultato = prioritizeExplicitDuplicates(listOf(esplicito, pulito))
        assertEquals(listOf(esplicito, pulito), risultato)
    }

    @Test
    fun `brani diversi non vengono toccati`() {
        // Nessun duplicato qui: l'ordine deve restare quello di ingresso,
        // che di solito e' gia' l'ordine di rilevanza di YouTube Music.
        val a = t("a".repeat(11), "Kill You", "Eminem", explicit = true)
        val b = t("b".repeat(11), "Lose Yourself", "Eminem", explicit = false)
        val c = t("c".repeat(11), "Without Me", "Eminem", explicit = true)
        val risultato = prioritizeExplicitDuplicates(listOf(a, b, c))
        assertEquals(listOf(a, b, c), risultato)
    }

    @Test
    fun `un riordino e' mirato e non sposta chi non e' duplicato`() {
        // Il duplicato in fondo alla lista sale solo al posto del suo
        // gemello: gli altri due brani, che non sono duplicati di
        // nessuno, restano esattamente dove stavano.
        val altro1 = t("a".repeat(11), "Lose Yourself", "Eminem")
        val pulito = t("b".repeat(11), "Kill You", "Eminem", explicit = false)
        val altro2 = t("c".repeat(11), "Without Me", "Eminem")
        val esplicito = t("d".repeat(11), "Kill You", "Eminem", explicit = true)

        val risultato = prioritizeExplicitDuplicates(listOf(altro1, pulito, altro2, esplicito))

        // Il duplicato esplicito prende il posto piu' alto fra i due
        // (quello di "pulito"); "pulito" scende alla sua posizione.
        assertEquals(listOf(altro1, esplicito, altro2, pulito), risultato)
    }

    @Test
    fun `senza uno stato esplicito misto non si tocca niente`() {
        // Stesso brano due volte, ma entrambi "non lo sappiamo" (null):
        // non c'e' niente da decidere, e non e' un mix di true/false.
        val a = t("a".repeat(11), "Kill You", "Eminem")
        val b = t("b".repeat(11), "Kill You", "Eminem")
        val risultato = prioritizeExplicitDuplicates(listOf(a, b))
        assertEquals(listOf(a, b), risultato)
    }

    @Test
    fun `il rumore nel titolo non impedisce di riconoscere il duplicato`() {
        // Stessa regola di TrackMatcher: "Remastered", "Explicit" e simili
        // sono etichette di versione, non identita' del brano.
        val pulito = t("a".repeat(11), "Kill You (Clean Version)", "Eminem", explicit = false)
        val esplicito = t("b".repeat(11), "Kill You (Explicit)", "Eminem", explicit = true)
        val risultato = prioritizeExplicitDuplicates(listOf(pulito, esplicito))
        assertEquals(listOf(esplicito, pulito), risultato)
    }
}
