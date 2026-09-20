package com.sosound.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sosound.app.ui.theme.Vetro

/**
 * «Sto facendo qualcosa», mentre lo si fa.
 *
 * Aprire l'elenco di una cartella di Google Drive puo' richiedere
 * parecchi secondi: la risposta arriva dalla rete, non dal telefono. In
 * quei secondi non compariva niente, e niente che compare e'
 * indistinguibile da un tocco che non ha funzionato — chi guarda tocca
 * di nuovo, e intanto il primo giro e' ancora in corso.
 */
@Composable
fun Attesa(testo: String) {
    AlertDialog(
        // Non si chiude toccando fuori: non c'e' niente da annullare, e
        // farlo sparire darebbe l'impressione che sia finito.
        onDismissRequest = {},
        containerColor = Vetro.Ground,
        title = null,
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Vetro.Accent,
                    strokeWidth = 2.dp,
                )
                Text(testo, color = Vetro.Ink, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {},
    )
}
