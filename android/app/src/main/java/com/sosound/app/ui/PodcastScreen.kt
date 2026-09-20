package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.data.catalog.PodcastPage
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass

/**
 * La pagina di un podcast: una descrizione breve e l'elenco delle puntate.
 *
 * Ci si arriva toccando il nome del programma su una puntata trovata
 * cercando — prima quel nome era solo testo, e il programma si poteva
 * raggiungere solo ricercandolo a mano.
 */
@UnstableApi
@Composable
fun PodcastScreen(vm: MainViewModel, page: PodcastPage) {
    val owned by vm.ownedIds.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    var descrizioneAperta by remember { mutableStateOf(false) }

    val mancanti = page.episodes.count { it.videoId !in owned }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { vm.browseBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Vetro.InkSoft)
            }
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Vetro.Glass),
                contentAlignment = Alignment.Center,
            ) {
                if (page.thumbnail != null) {
                    AsyncImage(
                        model = page.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                } else {
                    Icon(Icons.Default.Mic, null, tint = Vetro.InkFaint)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    page.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Vetro.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        page.publisher,
                        if (page.episodes.size == 1) "1 puntata" else "${page.episodes.size} puntate",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        page.description?.let { testo ->
            Text(
                testo,
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkSoft,
                // Chiusa mostra tre righe; toccandola si apre tutta.
                // Le descrizioni dei podcast sono spesso lunghissime e
                // riempirebbero lo schermo prima delle puntate.
                maxLines = if (descrizioneAperta) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable { descrizioneAperta = !descrizioneAperta },
            )
        }

        if (page.episodes.isNotEmpty()) {
            OutlinedButton(
                onClick = { vm.downloadPodcast(page) },
                enabled = mancanti > 0,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(17.dp))
                Text(
                    if (mancanti == 0) "  Le hai tutte" else "  Scarica tutte le puntate ($mancanti)",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(page.episodes, key = { it.videoId }) { ep ->
                TrackRow(
                    title = ep.title,
                    subtitle = ep.durationText.orEmpty(),
                    coverPath = null,
                    coverUrl = ep.thumbnail ?: page.thumbnail,
                    playing = stato.current?.videoId == ep.videoId,
                    accent = accent.color,
                    onClick = { vm.playFromSearch(ep.copy(album = page.title)) },
                    onDetails = { vm.showDetails(ep) },
                    trailing = {
                        if (ep.videoId in owned) {
                            Icon(
                                Icons.Default.Mic, "Già sul telefono",
                                tint = accent.color, modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}
