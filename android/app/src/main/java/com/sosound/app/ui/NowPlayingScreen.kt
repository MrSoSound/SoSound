package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass
import java.io.File

/**
 * L'ascolto a tutto schermo.
 *
 * La copertina grande al centro, i controlli sotto senza cornici. Il
 * fondo resta quello di sempre: l'unica cosa che prende colore dal brano
 * sono il tondo del play e la barra di avanzamento.
 */
@UnstableApi
@Composable
fun NowPlayingScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    val track = state.current

    val tint by animateColorAsState(accent.color, tween(600), label = "tinta")
    val onTint by animateColorAsState(accent.onColor, tween(600), label = "tintaSopra")

    if (track == null) {
        onClose()
        return
    }

    // Mentre l'utente trascina la barra, la posizione mostrata e' la sua,
    // non quella del player: altrimenti il pallino scapperebbe sotto il
    // dito a ogni aggiornamento.
    var apriDispositivi by remember { mutableStateOf(false) }
    // Disegnarlo: era il pezzo che mancava. Il tasto accendeva questa
    // variabile e nessuno la leggeva, quindi non succedeva niente — e
    // da fuori era indistinguibile da un tasto rotto.
    if (apriDispositivi) DeviceSheet(vm) { apriDispositivi = false }
    val suApparecchioDlna by vm.dlnaAttivo.collectAsState()

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = if (scrubbing) scrubValue
    else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    // Trascinare in basso chiude, come in ogni altro lettore.
    //
    // Lo spostamento segue il dito invece di aspettare la fine del
    // gesto: e' cosi' che si capisce, a meta' strada, che si sta per
    // chiudere e che si puo' ancora cambiare idea. Sotto la soglia
    // torna su da solo.
    val densita = LocalDensity.current
    var trascinamento by remember { mutableFloatStateOf(0f) }
    val soglia = with(densita) { 140.dp.toPx() }
    val scostamento by animateFloatAsState(
        trascinamento,
        // Mentre il dito e' giu' non si anima: l'animazione inseguirebbe
        // il dito con un ritardo, e il foglio sembrerebbe molle.
        animationSpec = if (trascinamento == 0f) spring() else snap(),
        label = "scostamento",
    )

    Column(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, scostamento.toInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (trascinamento > soglia) onClose()
                        trascinamento = 0f
                    },
                    onDragCancel = { trascinamento = 0f },
                ) { _, delta ->
                    // Solo verso il basso: tirare in su non porta da
                    // nessuna parte, e lasciarlo fare darebbe l'idea che
                    // ci sia qualcosa sopra.
                    trascinamento = (trascinamento + delta).coerceAtLeast(0f)
                }
            }
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, "Chiudi", tint = Vetro.InkSoft)
            }
            val suCast by vm.castDispositivo.collectAsState()
            val suApparecchio = suCast ?: suApparecchioDlna
            Text(
                // Quando si trasmette, dire dove: altrimenti il telefono
                // resta muto e non si capisce perche'.
                suApparecchio?.let { "Su $it" } ?: "In ascolto",
                style = MaterialTheme.typography.labelMedium,
                color = if (suApparecchio != null) tint else Vetro.InkFaint,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Un tasto solo per «suonare altrove».
            //
            // Prima erano due — quello di Cast e uno con l'icona di uno
            // schermo per il DLNA — perche' sono due protocolli diversi.
            // Ma chi guarda non vede due protocolli: vede due tasti che
            // promettono la stessa cosa, e non sa quale premere. Dentro
            // il pannello ci sono entrambi i mondi, spiegati.
            IconButton(onClick = { apriDispositivi = true }) {
                Icon(
                    Icons.Default.Cast,
                    "Suona su un altro apparecchio",
                    tint = if (suApparecchio != null) tint else Vetro.InkSoft,
                )
            }
            IconButton(onClick = { vm.showQueue(true) }) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    "Coda di riproduzione",
                    tint = Vetro.InkSoft,
                )
            }
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Vetro.Glass)
                    // La copertina apre i dettagli, come i tre puntini
                    // nell'elenco: e' l'unica cosa grande sullo schermo
                    // e toccarla e' il gesto che viene spontaneo.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { vm.showDetails(track) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (track.coverPath != null) {
                    AsyncImage(
                        model = File(track.coverPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote, null,
                        tint = Vetro.InkFaint,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }

        Text(
            track.title,
            style = MaterialTheme.typography.headlineSmall,
            color = Vetro.Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(track.artist, track.album).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = Vetro.InkFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        Slider(
            value = fraction,
            onValueChange = { scrubbing = true; scrubValue = it },
            onValueChangeFinished = {
                vm.player.seekTo((scrubValue * duration).toLong())
                scrubbing = false
            },
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint,
                inactiveTrackColor = Vetro.GlassStrong,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                millis(if (scrubbing) (scrubValue * duration).toLong() else state.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Vetro.InkFaint,
            )
            Text(
                millis(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Vetro.InkFaint,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle,
                    if (state.shuffle) "Casuale attivo" else "Riproduzione casuale",
                    tint = if (state.shuffle) tint else Vetro.InkFaint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(Modifier.size(40.dp))
            IconButton(onClick = { vm.cycleRepeat() }) {
                Icon(
                    // Tre stati e due icone: «ripeti il brano» ha il suo
                    // simbolo col numero uno, se no non si distingue da
                    // «ripeti tutto» e il tasto sembrerebbe rotto.
                    if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    when (state.repeat) {
                        1 -> "Ripeti il brano"
                        2 -> "Ripeti la coda"
                        else -> "Nessuna ripetizione"
                    },
                    tint = if (state.repeat != 0) tint else Vetro.InkFaint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.player.previous() }, enabled = state.hasPrevious) {
                Icon(
                    Icons.Default.SkipPrevious, "Precedente",
                    tint = if (state.hasPrevious) Vetro.InkSoft else Vetro.InkFaint,
                    modifier = Modifier.size(34.dp),
                )
            }

            Box(
                Modifier
                    .padding(horizontal = 26.dp)
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { vm.player.togglePlayPause() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.isPlaying) "Pausa" else "Riproduci",
                    // Bianco o quasi-nero secondo la tinta: e' quello che
                    // tiene leggibile l'icona anche su un giallo acceso.
                    tint = onTint,
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(onClick = { vm.player.next() }, enabled = state.hasNext) {
                Icon(
                    Icons.Default.SkipNext, "Successivo",
                    tint = if (state.hasNext) Vetro.InkSoft else Vetro.InkFaint,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

private fun millis(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
