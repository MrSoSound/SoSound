package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass
import java.io.File

/**
 * La coda di riproduzione.
 *
 * Prima esisteva solo dentro ExoPlayer: la musica andava avanti da sola
 * ma non c'era modo di sapere cosa sarebbe arrivato dopo, ne' di
 * cambiarlo. Qui si vede, si riordina con una pressione lunga e si
 * toglie quello che non si vuole piu'.
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun QueueSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val coda by vm.playQueue.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()

    val listState = rememberLazyListState()
    val drag = rememberDragReorder(listState) { from, to -> vm.moveInQueue(from, to) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vetro.Ground,
        contentColor = Vetro.Ink,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("In coda", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)
                    Text(
                        if (coda.isEmpty()) "vuota"
                        else "${coda.size} brani · tieni premuto per riordinare",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }
                if (coda.isNotEmpty()) {
                    TextButton(onClick = { vm.clearQueue(); onDismiss() }) {
                        Text("Svuota", color = Vetro.InkSoft)
                    }
                }
            }

            if (coda.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    Text(
                        "Non c'è niente in coda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Vetro.InkFaint,
                    )
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .dragReorder(drag),
            ) {
                itemsIndexed(coda, key = { _, t -> t.videoId }) { index, track ->
                    val inMano = drag.draggingIndex == index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // L'elemento in mano sta sopra gli altri e si
                            // muove col dito: senza zIndex scivolerebbe
                            // sotto quelli vicini.
                            .zIndex(if (inMano) 1f else 0f)
                            .graphicsLayer { translationY = if (inMano) drag.offset else 0f }
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .then(if (inMano) Modifier.glass(strong = true) else Modifier)
                            .clickable { vm.jumpTo(index) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(7.dp))
                                .background(Vetro.Glass),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (track.coverPath != null) {
                                AsyncImage(
                                    model = File(track.coverPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Default.MusicNote, null,
                                    tint = Vetro.InkFaint, modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (index == stato.index) {
                                    Icon(
                                        Icons.Default.Equalizer, "In ascolto",
                                        tint = accent.color, modifier = Modifier.size(13.dp),
                                    )
                                }
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (index == stato.index) accent.color else Vetro.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                // La nuvoletta anche qui: la coda e'
                                // l'unico posto dove si vede cosa sta per
                                // suonare, ed e' li' che serve sapere
                                // quali brani chiederanno la rete.
                                if (track.haFile) track.artist else "☁ ${track.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Vetro.InkFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Icon(
                            Icons.Default.DragHandle, null,
                            tint = Vetro.InkFaint,
                            modifier = Modifier.size(20.dp).alpha(0.7f),
                        )
                        IconButton(
                            onClick = { vm.removeFromQueue(index) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close, "Togli dalla coda",
                                tint = Vetro.InkFaint, modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
