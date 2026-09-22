package com.sosound.app

import com.sosound.app.data.importing.Confidence
import com.sosound.app.data.importing.ImportRow
import com.sosound.app.ui.ImportEntry
import com.sosound.app.ui.ordinataPerRevisione
import com.sosound.app.ui.priorita
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ordine con cui le righe di un import compaiono nella revisione: le
 * righe da controllare (non trovate, poi incerte) prima di quelle gia'
 * sicure, senza mescolare l'ordine originale dentro ogni gruppo.
 */
class ImportOrderTest {

    private fun entry(nome: String, confidence: Confidence) = ImportEntry(
        row = ImportRow(title = nome, artist = "Artista"),
        match = null,
        confidence = confidence,
        score = 0.0,
        alternatives = emptyList(),
        selected = false,
    )

    @Test
    fun `la priorita mette non trovato prima di incerto prima di sicuro`() {
        assertTrue(Confidence.NON_TROVATO.priorita() < Confidence.INCERTO.priorita())
        assertTrue(Confidence.INCERTO.priorita() < Confidence.SICURO.priorita())
    }

    @Test
    fun `le righe problematiche vengono prima`() {
        val righe = listOf(
            entry("1 sicura", Confidence.SICURO),
            entry("2 non trovata", Confidence.NON_TROVATO),
            entry("3 incerta", Confidence.INCERTO),
            entry("4 sicura", Confidence.SICURO),
            entry("5 non trovata", Confidence.NON_TROVATO),
        )

        val ordinate = righe.ordinataPerRevisione()

        assertEquals(
            listOf(
                "2 non trovata", "5 non trovata",
                "3 incerta",
                "1 sicura", "4 sicura",
            ),
            ordinate.map { it.row.title },
        )
    }

    @Test
    fun `dentro ogni gruppo l'ordine originale non cambia`() {
        // Stesso gruppo (INCERTO) in un ordine arbitrario: deve restare
        // esattamente quello, perche' sortedBy e' stabile.
        val righe = listOf(
            entry("prima", Confidence.INCERTO),
            entry("seconda", Confidence.INCERTO),
            entry("terza", Confidence.INCERTO),
        )

        val ordinate = righe.ordinataPerRevisione()

        assertEquals(listOf("prima", "seconda", "terza"), ordinate.map { it.row.title })
    }

    @Test
    fun `una lista gia' a posto o vuota non da' problemi`() {
        assertEquals(emptyList<ImportEntry>(), emptyList<ImportEntry>().ordinataPerRevisione())

        val unaSola = listOf(entry("sola", Confidence.SICURO))
        assertEquals(unaSola, unaSola.ordinataPerRevisione())
    }
}
