package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass
import java.io.File

/**
 * La barra del player, in fondo. Compare solo quando c'e' qualcosa in
 * riproduzione: una barra vuota ruberebbe spazio alla lista per niente.
 *
 * Toccarla apre l'ascolto a tutto schermo.
 */
@UnstableApi
@Composable
fun PlayerBar(vm: MainViewModel, onExpand: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    val track = state.current ?: return

    // Il brano in ascolto puo' essere anche quello che sta scaricando in
    // questo momento — capita scaricandolo apposta mentre lo si ascolta
    // gia' in streaming. La copertina qui e' piccola, ma l'anello resta
    // leggibile lo stesso: e' lo stesso linguaggio delle righe sopra.
    val downloads by vm.downloads.collectAsState()
    // In coda di scaricamento ha la precedenza, perche' porta una
    // percentuale vera. Senza quella, ma con l'audio che ancora non
    // esce, resta il caso di un brano appena toccato che sta risolvendo
    // il suo indirizzo in streaming (un paio di secondi, sempre) — un
    // anello indeterminato dice comunque "sto lavorando", non "mi sono
    // bloccato".
    val download = CoverDownload.da(downloads.firstOrNull { it.track.videoId == track.videoId })
        ?: CoverDownload.InAttesa.takeIf { state.isBuffering }

    // Il passaggio da un brano all'altro e' una dissolvenza: uno stacco
    // secco attirerebbe l'occhio su un dettaglio che non lo merita.
    val tint by animateColorAsState(accent.color, tween(600), label = "tinta")

    // Trascinare in su apre l'ascolto a tutto schermo, come in ogni
    // altro lettore. Il tocco resta e fa la stessa cosa: sono due modi
    // di dire «fammi vedere», e chi ne conosce uno solo non deve
    // impararne un altro.
    val densita = LocalDensity.current
    val soglia = with(densita) { 44.dp.toPx() }
    var salita by remember { mutableFloatStateOf(0f) }

    // La barra sale col dito invece di restare ferma finche' non si
    // stacca: senza, il gesto sembrava non fare niente per tutta la sua
    // durata e poi "saltava" alla schermata intera di colpo. snap()
    // mentre il dito e' giu' la fa seguire 1:1; spring() al rilascio la
    // fa tornare al suo posto con un rimbalzo, sia che si apra
    // l'ascolto sia che il gesto non abbia superato la soglia.
    val scostamento by animateFloatAsState(
        -salita,
        animationSpec = if (salita == 0f) spring() else snap(),
        label = "salitaBarra",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, scostamento.toInt()) }
            .glass(Vetro.BarShape, strong = true)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (salita > soglia) onExpand()
                        salita = 0f
                    },
                    onDragCancel = { salita = 0f },
                ) { _, delta ->
                    // Solo verso l'alto: trascinare in giu' una barra
                    // che sta gia' in fondo non porta da nessuna parte.
                    // Il tetto e' un peek, non una corsa: oltre un certo
                    // punto la barra non deve sembrare in fuga verso
                    // l'alto, la schermata intera ci pensa da sola.
                    salita = (salita - delta).coerceIn(0f, with(densita) { 120.dp.toPx() })
                }
            }
            .clickable(onClick = onExpand),
    ) {
        // La riga di avanzamento e' sottilissima e senza sfondo: qui basta
        // sapere a che punto si e', non poterci agire — per quello c'e'
        // la schermata intera.
        val fraction = if (state.durationMs > 0) {
            (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        } else 0f

        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Vetro.GlassEdge),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(tint),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Vetro.Glass),
                contentAlignment = Alignment.Center,
            ) {
                if (track.coverPath != null) {
                    AsyncImage(
                        model = File(track.coverPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(38.dp),
                    )
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = Vetro.InkFaint,
                        modifier = Modifier.size(18.dp))
                }
                if (download != null) CoverDownloadOverlay(download, accent = tint)
            }

            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vetro.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = { vm.player.togglePlayPause() }) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.isPlaying) "Pausa" else "Riproduci",
                    tint = tint,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = { vm.player.next() }, enabled = state.hasNext) {
                Icon(
                    Icons.Default.SkipNext, "Successivo",
                    tint = if (state.hasNext) Vetro.InkSoft else Vetro.InkFaint,
                )
            }
        }
    }
}
