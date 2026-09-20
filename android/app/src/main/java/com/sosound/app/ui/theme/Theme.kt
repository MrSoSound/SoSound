package com.sosound.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * La direzione «Vetro».
 *
 * Il colore vero dell'app arriva dalla copertina del brano: questi sono i
 * valori che reggono quando non c'e' niente in ascolto, e i neutri che
 * stanno sopra qualunque fondo.
 */
object Vetro {
    /** Il fondo. Ardesia: grigio con una deriva fredda appena percettibile. */
    val Ground = Color(0xFF0E1116)

    /**
     * Le tre macchie sopra il fondo.
     *
     * Molto desaturate di proposito: restano come variazione di
     * luminosita' piu' che come colore, ed e' quello che evita
     * l'effetto «schermo spento» senza tornare a tingere l'interfaccia.
     * L'opacita' vera e' in [com.sosound.app.ui.VetroBackground].
     */
    val Wash1 = Color(0xFF607EA6)
    val Wash2 = Color(0xFF7896B4)
    val Wash3 = Color(0xFF506E96)

    /** L'accento fisso: barra di navigazione, e i controlli quando il
     *  brano non ha una copertina da cui cavare una tinta. */
    val Accent = Color(0xFFA594FF)

    /** L'inchiostro dentro il tondo del play. Quasi nero, virato ardesia
     *  come il resto: un nero puro qui stonerebbe. */
    val OnAccent = Color(0xFF121619)

    // Neutri con la stessa deriva fredda del fondo: un grigio puro su
    // ardesia legge come sporco.
    val Ink = Color(0xFFE9ECF1)
    val InkSoft = Color(0xFFB4BCC6)
    val InkFaint = Color(0xFF8B939E)

    /** I pannelli smerigliati. Bianco a bassissima opacita': sopra un
     *  fondo gia' sfocato legge come vetro, senza bisogno di sfocare
     *  davvero cio' che sta dietro — cosa che Compose non sa fare
     *  sotto Android 12. */
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x1FFFFFFF)
    val GlassEdge = Color(0x1AFFFFFF)

    val Danger = Color(0xFFFF8FA3)

    val CardShape = RoundedCornerShape(14.dp)
    val BarShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
}

/** Un pannello di vetro: fondo tenue piu' un bordo ancora piu' tenue. */
fun Modifier.glass(
    shape: androidx.compose.ui.graphics.Shape = Vetro.CardShape,
    strong: Boolean = false,
): Modifier = this
    .clip(shape)
    .background(if (strong) Vetro.GlassStrong else Vetro.Glass)
    .border(1.dp, Vetro.GlassEdge, shape)

private val VetroScheme = darkColorScheme(
    primary = Vetro.Accent,
    onPrimary = Vetro.OnAccent,
    secondary = Vetro.InkSoft,
    // Trasparenti di proposito: sotto c'e' la copertina sfocata, e una
    // superficie opaca la coprirebbe.
    background = Color.Transparent,
    onBackground = Vetro.Ink,
    surface = Color.Transparent,
    onSurface = Vetro.Ink,
    surfaceVariant = Vetro.Glass,
    onSurfaceVariant = Vetro.InkFaint,
    error = Vetro.Danger,
    outline = Vetro.GlassEdge,
)

@Composable
fun SoSoundTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VetroScheme) {
        // Senza questo, ogni Text a cui non si passa un colore esplicito
        // ricade sul nero: e' il valore di serie di LocalContentColor in
        // Material3. Di solito lo imposta Surface, che qui non c'e'
        // perche' le superfici sono trasparenti sopra il fondo dipinto.
        CompositionLocalProvider(LocalContentColor provides Vetro.Ink, content = content)
    }
}
