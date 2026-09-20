package com.sosound.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sosound.app.ui.theme.Vetro

/**
 * Il fondo dell'app: sempre lo stesso.
 *
 * Tre macchie di colore molto larghe su indaco quasi nero. Non cambia col
 * brano di proposito — un fondo che si ritinge a ogni canzone e' la cosa
 * piu' vistosa dell'interfaccia e finisce per essere quella che stanca
 * prima, oltre a mettere il testo sopra una superficie di cui non si
 * conosce in anticipo la luminosita'.
 *
 * Il colore della copertina entra comunque, ma solo dove serve: sui
 * controlli del player. Li' e' un'informazione — dice quale brano stai
 * ascoltando — invece che decorazione.
 */
@Composable
fun VetroBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Vetro.Ground)

        // I raggi sono proporzionali alla LARGHEZZA, non alla diagonale:
        // su uno schermo alto la diagonale darebbe macchie enormi e
        // indistinguibili fra loro.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Vetro.Wash1.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.24f, size.height * 0.14f),
                radius = size.width * 0.80f,
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Vetro.Wash2.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(size.width * 0.84f, size.height * 0.30f),
                radius = size.width * 0.68f,
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Vetro.Wash3.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.44f, size.height * 0.88f),
                radius = size.width * 0.84f,
            )
        )

        // Chiude in basso, dove stanno player e navigazione: le macchie
        // chiare sotto ai controlli li renderebbero meno leggibili.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.62f to Color.Transparent,
                1f to Vetro.Ground.copy(alpha = 0.72f),
            )
        )
    }
}
