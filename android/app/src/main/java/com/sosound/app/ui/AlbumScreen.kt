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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sosound.app.data.catalog.AlbumPage
import com.sosound.app.data.catalog.ArtistPage
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass

/** La scaletta di un album, con le azioni sull'album intero. */
@UnstableApi
@Composable
fun AlbumScreen(vm: MainViewModel, page: AlbumPage) {
    val owned by vm.ownedIds.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    var sceltaPlaylist by remember { mutableStateOf(false) }

    val mancanti = page.tracks.count { it.videoId !in owned }

    Column(Modifier.fillMaxSize()) {
        Intestazione(
            titolo = page.title,
            sottotitolo = listOfNotNull(page.artist, page.year).joinToString(" · "),
            terzaRiga = page.info,
            immagine = page.thumbnail,
            tonda = false,
            onBack = { vm.browseBack() },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { vm.downloadAlbum(page) },
                enabled = mancanti > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(17.dp))
                Text(
                    if (mancanti == 0) "  Ce l'hai tutto" else "  Scarica ($mancanti)",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedButton(
                onClick = { sceltaPlaylist = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(17.dp))
                Text("  In playlist", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(page.tracks, key = { _, t -> t.videoId }) { i, t ->
                TrackRow(
                    // Il numero di traccia aiuta a ritrovarsi in una
                    // scaletta, dove l'ordine e' un'informazione.
                    title = "${i + 1}. ${t.title}",
                    subtitle = t.durationText ?: "",
                    coverPath = null,
                    coverUrl = t.thumbnail ?: page.thumbnail,
                    playing = stato.current?.videoId == t.videoId,
                    accent = accent.color,
                    explicit = t.explicit == true,
                    onClick = { vm.playFromSearch(t.copy(thumbnail = t.thumbnail ?: page.thumbnail)) },
                    onDetails = { vm.showDetails(t) },
                    trailing = {
                        if (t.videoId in owned) {
                            Icon(
                                Icons.Default.Album, "Già sul telefono",
                                tint = accent.color, modifier = Modifier.size(17.dp),
                            )
                        }
                    },
                )
            }
        }
    }

    if (sceltaPlaylist) {
        ScegliPlaylist(
            vm = vm,
            onScelta = { id -> vm.albumToPlaylist(page, id); sceltaPlaylist = false },
            onDismiss = { sceltaPlaylist = false },
        )
    }
}

/**
 * La scaletta di una playlist pubblica di YouTube Music, trovata
 * cercando — stesso impianto di [AlbumScreen], perche' e' la stessa
 * cosa vista da un altro tipo di pagina: una copertina, delle azioni
 * sull'insieme, la scaletta sotto.
 */
@UnstableApi
@Composable
fun PlaylistScreen(vm: MainViewModel, page: com.sosound.app.data.catalog.PlaylistPage) {
    val owned by vm.ownedIds.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    var sceltaPlaylist by remember { mutableStateOf(false) }

    val mancanti = page.tracks.count { it.videoId !in owned }

    Column(Modifier.fillMaxSize()) {
        Intestazione(
            titolo = page.title,
            sottotitolo = "${page.tracks.size} brani",
            terzaRiga = if (page.troncata) "continua oltre questi — importati solo i primi" else null,
            immagine = page.thumbnail,
            tonda = false,
            onBack = { vm.browseBack() },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { vm.downloadPublicPlaylist(page) },
                enabled = mancanti > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(17.dp))
                Text(
                    if (mancanti == 0) "  Ce l'hai tutto" else "  Scarica ($mancanti)",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            OutlinedButton(
                onClick = { sceltaPlaylist = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(17.dp))
                Text("  In playlist", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(page.tracks, key = { _, t -> t.videoId }) { i, t ->
                TrackRow(
                    title = "${i + 1}. ${t.title}",
                    subtitle = t.subtitle,
                    coverPath = null,
                    coverUrl = t.thumbnail ?: page.thumbnail,
                    playing = stato.current?.videoId == t.videoId,
                    accent = accent.color,
                    explicit = t.explicit == true,
                    onClick = { vm.playFromSearch(t.copy(thumbnail = t.thumbnail ?: page.thumbnail)) },
                    onDetails = { vm.showDetails(t) },
                    trailing = {
                        if (t.videoId in owned) {
                            Icon(
                                Icons.Default.Album, "Già sul telefono",
                                tint = accent.color, modifier = Modifier.size(17.dp),
                            )
                        }
                    },
                )
            }
        }
    }

    if (sceltaPlaylist) {
        ScegliPlaylist(
            vm = vm,
            onScelta = { id -> vm.publicPlaylistToPlaylist(page, id); sceltaPlaylist = false },
            onDismiss = { sceltaPlaylist = false },
        )
    }
}

/** I brani principali di un artista e le sue uscite. */
@UnstableApi
@Composable
fun ArtistScreen(vm: MainViewModel, page: ArtistPage) {
    val owned by vm.ownedIds.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Intestazione(
            titolo = page.name,
            sottotitolo = "",
            terzaRiga = null,
            immagine = page.thumbnail,
            tonda = true,
            onBack = { vm.browseBack() },
        )

        LazyColumn(Modifier.fillMaxSize()) {
            if (page.topSongs.isNotEmpty()) {
                item { Sezione("Brani più ascoltati") }
                items(page.topSongs, key = { it.videoId }) { t ->
                    TrackRow(
                        title = t.title,
                        subtitle = t.subtitle,
                        coverPath = null,
                        coverUrl = t.thumbnail,
                        playing = stato.current?.videoId == t.videoId,
                        accent = accent.color,
                        explicit = t.explicit == true,
                        onClick = { vm.playFromSearch(t) },
                        onDetails = { vm.showDetails(t) },
                        trailing = {
                            if (t.videoId in owned) {
                                Icon(
                                    Icons.Default.Album, "Già sul telefono",
                                    tint = accent.color, modifier = Modifier.size(17.dp),
                                )
                            }
                        },
                    )
                }
            }

            if (page.albums.isNotEmpty()) {
                item { Sezione("Album") }
                items(page.albums, key = { it.browseId }) { a ->
                    RigaCatalogo(
                        title = a.title,
                        subtitle = a.year.orEmpty(),
                        thumbnail = a.thumbnail,
                        fallback = Icons.Default.Album,
                        tonda = false,
                    ) { vm.openAlbum(a.browseId) }
                }
            }

            if (page.singles.isNotEmpty()) {
                item { Sezione("Singoli ed EP") }
                items(page.singles, key = { it.browseId }) { a ->
                    RigaCatalogo(
                        title = a.title,
                        subtitle = a.year.orEmpty(),
                        thumbnail = a.thumbnail,
                        fallback = Icons.Default.Album,
                        tonda = false,
                    ) { vm.openAlbum(a.browseId) }
                }
            }
        }
    }
}

@Composable
private fun Sezione(testo: String) {
    Text(
        testo,
        style = MaterialTheme.typography.labelLarge,
        color = Vetro.InkFaint,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun Intestazione(
    titolo: String,
    sottotitolo: String,
    terzaRiga: String?,
    immagine: String?,
    tonda: Boolean,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Vetro.InkSoft)
        }
        Box(
            Modifier
                .size(72.dp)
                .clip(if (tonda) CircleShape else RoundedCornerShape(10.dp))
                .background(Vetro.Glass),
            contentAlignment = Alignment.Center,
        ) {
            if (immagine != null) {
                AsyncImage(
                    model = immagine,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp),
                )
            } else {
                Icon(
                    if (tonda) Icons.Default.Person else Icons.Default.Album,
                    null, tint = Vetro.InkFaint,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                titolo,
                style = MaterialTheme.typography.titleMedium,
                color = Vetro.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sottotitolo.isNotBlank()) {
                Text(
                    sottotitolo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            terzaRiga?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Vetro.InkFaint)
            }
        }
    }
}

/**
 * Sceglie in quale playlist mettere un album.
 *
 * Se non ce n'e' nessuna, la si crea qui: mandare l'utente a crearla
 * altrove e poi farlo tornare sarebbe un giro inutile.
 */
@UnstableApi
@Composable
fun ScegliPlaylist(vm: MainViewModel, onScelta: (Long) -> Unit, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    var creando by remember { mutableStateOf(false) }

    if (creando) {
        NamePlaylistDialog(
            titolo = "Nuova playlist",
            iniziale = "",
            onConfirm = { nome ->
                vm.createPlaylist(nome)
                creando = false
                // La playlist appena creata non ha ancora un id qui: si
                // chiude e basta, la si ritrova nell'elenco.
                onDismiss()
            },
            onDismiss = { creando = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vetro.Ground,
        title = { Text("In quale playlist?", color = Vetro.Ink) },
        text = {
            if (playlists.isEmpty()) {
                Text(
                    "Non hai ancora nessuna playlist.",
                    color = Vetro.InkSoft,
                    textAlign = TextAlign.Center,
                )
            } else {
                Column {
                    playlists.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onScelta(p.id) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(p.name, color = Vetro.Ink, modifier = Modifier.weight(1f))
                            Text(
                                "${p.trackCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Vetro.InkFaint,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { creando = true }) {
                Text("Nuova playlist", color = Vetro.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = Vetro.InkSoft) }
        },
    )
}
