package com.sosound.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sosound.app.ui.theme.Vetro

/**
 * Il bollino "E" per un brano esplicito.
 *
 * Uno stile discreto, come su Spotify o Apple Music: un riquadro piccolo
 * con un bordo sottile, non un allarme rosso. Va sempre ACCANTO al
 * titolo, mai al posto suo — chi sceglie cosa ascoltare deve continuare
 * a leggerlo per intero.
 */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    Text(
        "E",
        modifier = modifier
            .border(1.dp, Vetro.GlassEdge, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Vetro.InkFaint,
    )
}
