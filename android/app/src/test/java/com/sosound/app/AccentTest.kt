package com.sosound.app

import com.sosound.app.ui.accent.AccentExtractor
import com.sosound.app.ui.accent.TrackAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il colore dei controlli viene da una copertina qualsiasi, e una
 * copertina qualsiasi puo' essere di qualunque colore — compresi quelli
 * che sul fondo scuro non si vedrebbero, o che renderebbero illeggibile
 * l'icona sopra.
 *
 * Questi test provano che qualunque tinta entri, ne esca una usabile:
 * la barra stacca dal fondo, e l'icona dentro il tondo si legge.
 */
class AccentTest {

    private fun cover(vararg rgb: Triple<Int, Int, Int>): IntArray =
        IntArray(576) { i ->
            val (r, g, b) = rgb[i % rgb.size]
            (r shl 16) or (g shl 8) or b
        }

    private fun rgbOf(c: androidx.compose.ui.graphics.Color) =
        AccentExtractor.Rgb(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble())

    private fun report(name: String, a: TrackAccent): Pair<Double, Double> {
        val onGround = AccentExtractor.contrast(rgbOf(a.color), AccentExtractor.ground())
        val onAccent = AccentExtractor.contrast(rgbOf(a.onColor), rgbOf(a.color))
        println(
            "  %-20s tinta #%06X  sul fondo %.2f:1  icona %.2f:1"
                .format(name, a.color.value.shr(32).toInt() and 0xFFFFFF, onGround, onAccent)
        )
        return onGround to onAccent
    }

    @Test
    fun `qualunque copertina produce controlli leggibili`() {
        println("--- soglia 4.5:1 su entrambi i fronti ---")
        val casi = listOf(
            "rossa satura" to Triple(200, 30, 40),
            "marrone cupo" to Triple(60, 40, 25),
            "blu notte" to Triple(18, 22, 58),
            "gialla accesa" to Triple(255, 214, 10),
            "verde bosco" to Triple(24, 70, 40),
            "viola profondo" to Triple(48, 20, 80),
            "arancio" to Triple(235, 120, 30),
            "ciano" to Triple(0, 229, 255),
            "rosa pastello" to Triple(248, 200, 220),
            "quasi nera" to Triple(14, 12, 16),
        )

        val guai = mutableListOf<String>()
        for ((nome, c) in casi) {
            val accent = AccentExtractor.from(cover(c))
            val (onGround, onAccent) = report(nome, accent)
            if (onGround < AccentExtractor.MIN_CONTRAST_ON_GROUND) {
                guai += "$nome: barra a %.2f:1 sul fondo".format(onGround)
            }
            if (onAccent < AccentExtractor.MIN_CONTRAST_ON_ACCENT) {
                guai += "$nome: icona a %.2f:1 sulla tinta".format(onAccent)
            }
        }
        assertTrue(guai.joinToString("; "), guai.isEmpty())
    }

    @Test
    fun `la tinta scelta somiglia alla copertina`() {
        // Una copertina rossa non deve produrre un accento blu: la
        // correzione schiarisce, non cambia colore.
        println("--- la tonalita' si conserva ---")
        for ((nome, c) in listOf(
            "rossa" to Triple(200, 30, 40),
            "verde" to Triple(24, 120, 60),
            "blu" to Triple(30, 60, 200),
        )) {
            val out = AccentExtractor.from(cover(c))
            val r = out.color.red; val g = out.color.green; val b = out.color.blue
            val dominante = when (maxOf(r, g, b)) { r -> "rosso"; g -> "verde"; else -> "blu" }
            println("  $nome -> canale dominante: $dominante")
            assertTrue("$nome e' diventata $dominante", nome.startsWith(dominante.take(3)))
        }
    }

    @Test
    fun `una copertina grigia usa l'accento di serie`() {
        // Senza tinta non c'e' niente da estrarre, e inventarne una
        // sarebbe peggio che usare quella dell'app.
        for (grigio in listOf(Triple(0, 0, 0), Triple(128, 128, 128), Triple(255, 255, 255))) {
            val out = AccentExtractor.from(cover(grigio))
            assertEquals("grigio $grigio", TrackAccent.Default.color, out.color)
        }
    }

    @Test
    fun `una copertina illeggibile non fa esplodere niente`() {
        assertEquals(TrackAccent.Default, AccentExtractor.from(IntArray(0)))
    }
}
