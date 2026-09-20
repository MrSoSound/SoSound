package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass
import java.io.File

/**
 * Una riga di brano, uguale in libreria e dentro le playlist.
 *
 * Due bersagli distinti e nessuna ambiguita': la riga fa partire il
 * brano, i tre puntini aprono la scheda. Prima il tocco sulla riga
 * riproduceva e il cestino cancellava — due azioni a un dito di
 * distanza, una delle quali irreversibile.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    coverPath: String?,
    coverUrl: String? = null,
    playing: Boolean = false,
    accent: Color = Vetro.Accent,
    /**
     * Falso per un brano che sta in libreria ma non ha un file.
     *
     * Si ascolta dalla rete, e va detto: altrimenti «ce l'ho» e «lo
     * conosco» hanno lo stesso aspetto, e in aereo ci si accorge della
     * differenza nel momento peggiore.
     */
    senzaFile: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    onDetails: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .glass()
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)).background(Vetro.Glass),
            contentAlignment = Alignment.Center,
        ) {
            val model = coverPath?.let { File(it) } ?: coverUrl
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = Vetro.InkFaint)
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Il brano in ascolto si riconosce a colpo d'occhio invece
                // di dover leggere la barra in fondo.
                if (playing) {
                    Icon(
                        Icons.Default.Equalizer, "In ascolto",
                        tint = accent, modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (playing) accent else Vetro.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (senzaFile) {
                    // Una nuvoletta piccola, prima del testo: dice «questo
                    // arriva dalla rete» senza rubare una riga.
                    Icon(
                        Icons.Default.CloudQueue,
                        "Si ascolta dalla rete",
                        tint = Vetro.InkFaint,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        trailing?.invoke()

        if (onDetails != null) {
            IconButton(onClick = onDetails) {
                Icon(Icons.Default.MoreVert, "Dettagli", tint = Vetro.InkFaint)
            }
        }
    }
}
