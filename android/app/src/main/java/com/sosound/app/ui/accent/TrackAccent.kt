package com.sosound.app.ui.accent

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Il colore che i controlli del player prendono dalla copertina.
 *
 * Due colori e non uno: [color] e' la tinta (barra di avanzamento, tondo
 * del play), [onColor] e' quello che ci va sopra — l'icona dentro il
 * tondo. Deciderli separatamente e' l'unico modo per far funzionare una
 * tinta gialla tanto quanto una blu scura.
 */
@Immutable
data class TrackAccent(
    val color: Color,
    val onColor: Color,
) {
    companion object {
        /** Quando non c'e' copertina, o non se ne cava niente di usabile. */
        val Default = TrackAccent(
            color = Color(0xFFA594FF),
            onColor = Color(0xFF121619),
        )
    }
}

/**
 * Ricava un colore utilizzabile da una copertina.
 *
 * Il punto delicato non e' trovare la tinta dominante — e' che una tinta
 * presa cosi' com'e' spesso non si puo' usare. Il marrone di una
 * copertina cupa sparisce sul fondo scuro; un giallo acceso funziona da
 * tinta ma rende l'icona bianca illeggibile. Quindi si estrae, poi si
 * corregge finche' i contrasti tornano.
 */
object AccentExtractor {

    /**
     * Il fondo fisso dell'app: e' contro questo che la tinta deve
     * staccare. Deve restare allineato a `Vetro.Ground`.
     */
    private val GROUND = Rgb(0x0E / 255.0, 0x11 / 255.0, 0x16 / 255.0)

    /** Soglia per gli elementi grafici piccoli: barra sottile, icone. */
    const val MIN_CONTRAST_ON_GROUND = 4.5

    /** L'icona dentro il tondo pieno. */
    const val MIN_CONTRAST_ON_ACCENT = 4.5

    data class Rgb(val r: Double, val g: Double, val b: Double)

    /**
     * Sceglie la tinta e la corregge.
     *
     * [pixels] sono i pixel della copertina ridotta, in formato ARGB.
     */
    fun from(pixels: IntArray): TrackAccent {
        val picked = pickVibrant(pixels) ?: return TrackAccent.Default
        val usable = makeUsable(picked)
        return TrackAccent(
            color = usable.toColor(),
            onColor = bestInk(usable),
        )
    }

    /**
     * La tinta piu' viva, non la piu' frequente.
     *
     * Le copertine sono per lo piu' scure o desaturate, e il colore
     * dominante di solito e' un grigio: userebbe sempre quello. Qui si
     * premia la saturazione e la luminosita' media, che e' dove stanno i
     * colori che una persona chiamerebbe "il colore della copertina".
     */
    private fun pickVibrant(pixels: IntArray): Hsl? {
        if (pixels.isEmpty()) return null

        var best: Hsl? = null
        var bestScore = -1.0

        for (p in pixels) {
            val hsl = Rgb(
                ((p shr 16) and 0xFF) / 255.0,
                ((p shr 8) and 0xFF) / 255.0,
                (p and 0xFF) / 255.0,
            ).toHsl()

            // Penalizza gli estremi di luminosita': il nero e il bianco
            // non hanno tinta, e qualunque correzione li trasformerebbe in
            // un colore inventato.
            val midness = 1.0 - abs(hsl.l - 0.5) * 2.0
            val score = hsl.s * hsl.s * midness

            if (score > bestScore) {
                bestScore = score
                best = hsl
            }
        }

        // Copertina in scala di grigi: meglio l'accento di serie che un
        // colore tirato fuori dal nulla.
        return best?.takeIf { bestScore > 0.02 }
    }

    /** L'inchiostro dentro il tondo. Allineato a `Vetro.OnAccent`. */
    private val INK = Rgb(0x12 / 255.0, 0x16 / 255.0, 0x19 / 255.0)

    /**
     * Alza la tinta finche' funziona su due fronti insieme.
     *
     * Si agisce sulla luminosita' e non sulla saturazione: schiarire
     * mantiene riconoscibile il colore della copertina, mentre saturare
     * lo trasformerebbe in una tinta diversa.
     *
     * I fronti sono due e vanno soddisfatti entrambi, perche' tirano
     * nella stessa direzione ma non allo stesso punto:
     *
     *  - la tinta deve staccare dal fondo scuro (barra, icone);
     *  - l'icona dentro il tondo pieno deve staccare dalla tinta.
     *
     * Fermarsi al primo lascia scoperta una fascia di luminosita' —
     * all'incirca fra 0.18 e 0.23 di luminanza — dove la tinta si vede
     * benissimo sul fondo ma *nessun* inchiostro, ne' bianco ne' nero,
     * arriva a 4.5:1 sopra di essa. È il caso del rosso saturo e del blu
     * notte, che senza questo controllo passavano a 4.3:1.
     */
    private fun makeUsable(hsl: Hsl): Hsl {
        // Una saturazione minima serve, altrimenti il "colore" e' un
        // grigio chiaro e tanto valeva usare quello di serie.
        var out = hsl.copy(s = max(hsl.s, 0.45))

        var guard = 0
        while (guard < 120 && !isUsable(out)) {
            out = out.copy(l = min(0.95, out.l + 0.01))
            guard++
        }
        return out
    }

    private fun isUsable(hsl: Hsl): Boolean {
        val rgb = hsl.toRgb()
        // Un margine sopra la soglia: fermarsi esattamente a 4.50 lascia
        // il risultato in balia dell'arrotondamento a 8 bit per canale,
        // che dopo la conversione puo' farlo scendere sotto.
        return contrast(rgb, GROUND) >= MIN_CONTRAST_ON_GROUND + MARGIN &&
            contrast(INK, rgb) >= MIN_CONTRAST_ON_ACCENT + MARGIN
    }

    /**
     * L'inchiostro e' sempre quello scuro.
     *
     * Non e' una semplificazione: dopo [makeUsable] la tinta e' sempre
     * abbastanza chiara perche' il bianco sopra non regga il confronto.
     * Sceglierlo caso per caso darebbe sempre questo risultato, con
     * l'aggravante di far cambiare colore all'icona in modo imprevedibile
     * passando da un brano all'altro.
     */
    private fun bestInk(hsl: Hsl): Color = Color(0xFF121619)

    private const val MARGIN = 0.2

    // ------------------------------------------------------------- colore

    data class Hsl(val h: Double, val s: Double, val l: Double)

    fun Rgb.toHsl(): Hsl {
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val l = (mx + mn) / 2.0
        if (mx == mn) return Hsl(0.0, 0.0, l)

        val d = mx - mn
        val s = if (l > 0.5) d / (2.0 - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + if (g < b) 6.0 else 0.0)
            g -> ((b - r) / d + 2.0)
            else -> ((r - g) / d + 4.0)
        } / 6.0
        return Hsl(h, s, l)
    }

    fun Hsl.toRgb(): Rgb {
        if (s == 0.0) return Rgb(l, l, l)
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        return Rgb(hue(p, q, h + 1.0 / 3), hue(p, q, h), hue(p, q, h - 1.0 / 3))
    }

    private fun hue(p: Double, q: Double, t0: Double): Double {
        var t = t0
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < 1.0 / 2 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }

    fun Rgb.toColor() = Color(
        (r.coerceIn(0.0, 1.0) * 255).toInt(),
        (g.coerceIn(0.0, 1.0) * 255).toInt(),
        (b.coerceIn(0.0, 1.0) * 255).toInt(),
    )

    private fun Hsl.toColor() = toRgb().toColor()

    fun relativeLuminance(c: Rgb): Double =
        0.2126 * gamma(c.r) + 0.7152 * gamma(c.g) + 0.0722 * gamma(c.b)

    private fun gamma(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    fun contrast(a: Rgb, b: Rgb): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Per i test: il fondo contro cui si misura. */
    fun ground() = GROUND
}
