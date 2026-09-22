package com.sosound.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.data.library.PlaylistSummary
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass
import java.io.File

enum class Vista(val etichetta: String) {
    BRANI("Brani"), ALBUM("Album"), PLAYLIST("Playlist")
}

@UnstableApi
@Composable
fun LibraryScreen(vm: MainViewModel, vista: Vista) {
    // L'importazione sta qui e non dentro la vista «Playlist»: arrivandoci
    // dall'introduzione la vista attiva e' «Brani», e li' dentro non si
    // sarebbe mai vista.
    val importando by vm.importOpen.collectAsState()
    BackHandler(enabled = importando) { vm.resetImport(); vm.setImportOpen(false) }
    if (importando) {
        ImportScreen(vm, onClose = { vm.setImportOpen(false) })
        return
    }

    val apertaId by vm.openPlaylist.collectAsState()

    // Una playlist aperta si comporta come una schermata a se': il tasto
    // indietro la chiude invece di uscire dall'app.
    BackHandler(enabled = apertaId != null) { vm.openPlaylist(null) }

    if (apertaId != null) {
        PlaylistDetail(vm)
        return
    }

    // Qui c'e' solo il contenuto.
    //
    // I segmenti stanno FUORI dal nastro, sopra di esso: dentro
    // scivolavano via insieme alla pagina, e si vedevano tre copie
    // passare una dopo l'altra. Un'intestazione che si muove con quello
    // che indica non indica piu' niente.
    when (vista) {
        Vista.BRANI -> Brani(vm)
        Vista.ALBUM -> Album(vm)
        Vista.PLAYLIST -> Playlist(vm)
    }
}

@Composable
fun Segmenti(attiva: Vista, onChange: (Vista) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Vista.entries.forEach { v ->
            val scelta = v == attiva
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (scelta) Vetro.GlassStrong else Vetro.Glass)
                    .clickable { onChange(v) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    v.etichetta,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (scelta) Vetro.Ink else Vetro.InkFaint,
                )
            }
        }
    }
}

// ----------------------------------------------------------------- brani

@UnstableApi
@Composable
private fun Brani(vm: MainViewModel) {
    val tracks by vm.library.collectAsState()
    val filter by vm.libraryFilter.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val downloadByVideoId = downloads.associateBy { it.track.videoId }

    Column(Modifier.fillMaxSize()) {
        GlassField(
            value = filter,
            onValueChange = vm::filterLibrary,
            placeholder = "Filtra la tua musica",
        )

        if (tracks.isEmpty()) {
            Vuoto(
                if (filter.isBlank())
                    "Non hai ancora niente.\n\nVai su «Cerca» e tocca una canzone:\nparte da sola appena scesa."
                else "Nessun brano per «$filter»"
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .glass()
                    .clickable { vm.shufflePlay(tracks) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Shuffle, null,
                    tint = accent.color, modifier = Modifier.size(20.dp),
                )
                Text(
                    "Ascolta tutto a caso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vetro.Ink,
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
                    TrackRow(
                        title = track.title,
                        senzaFile = !track.haFile,
                        subtitle = listOfNotNull(track.artist, track.album)
                            .joinToString(" · ") + " · ${track.durationText}",
                        coverPath = track.coverPath,
                        playing = stato.current?.videoId == track.videoId,
                        accent = accent.color,
                        download = CoverDownload.da(downloadByVideoId[track.videoId]),
                        onClick = { vm.play(tracks, index) },
                        onDetails = { vm.showDetails(track) },
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- album

/**
 * Gli album ricostruiti dai brani che hai.
 *
 * Non c'e' una tabella «album»: il nome sta gia' su ogni brano e
 * raggrupparlo basta. Una tabella in piu' andrebbe tenuta allineata a
 * mano, e si disallineerebbe al primo brano cancellato.
 */
@UnstableApi
@Composable
private fun Album(vm: MainViewModel) {
    val albums by vm.localAlbums.collectAsState()
    val aperto by vm.openLocalAlbum.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val downloadByVideoId = downloads.associateBy { it.track.videoId }

    BackHandler(enabled = aperto != null) { vm.openLocalAlbum(null) }

    val scelto = albums.firstOrNull { it.title == aperto }
    if (scelto != null) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.openLocalAlbum(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Vetro.InkSoft)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        scelto.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Vetro.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${scelto.artist} · ${scelto.tracks.size} brani",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }
                IconButton(onClick = { vm.shufflePlay(scelto.tracks) }) {
                    Icon(Icons.Default.Shuffle, "Ascolta a caso", tint = Vetro.InkSoft)
                }
                IconButton(onClick = { vm.play(scelto.tracks, 0) }) {
                    Icon(Icons.Default.PlayArrow, "Riproduci", tint = accent.color)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(scelto.tracks, key = { _, t -> t.videoId }) { i, t ->
                    TrackRow(
                        title = t.title,
                        senzaFile = !t.haFile,
                        subtitle = t.durationText,
                        coverPath = t.coverPath,
                        playing = stato.current?.videoId == t.videoId,
                        accent = accent.color,
                        download = CoverDownload.da(downloadByVideoId[t.videoId]),
                        onClick = { vm.play(scelto.tracks, i) },
                        onDetails = { vm.showDetails(t) },
                    )
                }
            }
        }
        return
    }

    if (albums.isEmpty()) {
        Vuoto("Nessun album.\n\nI brani scaricati vengono raggruppati qui\nquando portano il nome di un album.")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(albums, key = { it.title + it.artist }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .glass()
                    .clickable { vm.openLocalAlbum(a.title) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)).background(Vetro.Glass),
                    contentAlignment = Alignment.Center,
                ) {
                    if (a.coverPath != null) {
                        AsyncImage(
                            model = File(a.coverPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp),
                        )
                    } else {
                        Icon(Icons.Default.Album, null, tint = Vetro.InkFaint)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        a.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Vetro.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${a.artist} · ${a.tracks.size} brani",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------- playlist

@UnstableApi
@Composable
private fun Playlist(vm: MainViewModel) {
    val playlists by vm.playlists.collectAsState()
    var creando by remember { mutableStateOf(false) }
    var mescolando by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .glass()
                .clickable { creando = true }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Add, null, tint = Vetro.InkSoft, modifier = Modifier.size(20.dp))
            Text("Nuova playlist", style = MaterialTheme.typography.bodyMedium, color = Vetro.Ink)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .glass()
                .clickable { vm.setImportOpen(true) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.FileDownload, null,
                tint = Vetro.InkSoft, modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    "Importa da Spotify o altro",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vetro.Ink,
                )
                Text(
                    "da un file esportato, senza collegare account",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
        }

        // Ha senso solo con almeno due playlist da poter unire: con zero
        // o una sola sarebbe un tasto morto, e coerentemente con il resto
        // della schermata (che con la libreria vuota semplicemente non
        // mostra «Ascolta tutto a caso») qui si sceglie di non mostrarla
        // affatto invece di tenerla lì disabilitata.
        if (playlists.size >= 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .glass()
                    .clickable { mescolando = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Shuffle, null,
                    tint = Vetro.InkSoft, modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        "Mescola più playlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Vetro.Ink,
                    )
                    Text(
                        "scegline due o più: i brani si uniscono in una coda sola, mischiata",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }
            }
        }

        if (playlists.isEmpty()) {
            Vuoto(
                "Nessuna playlist.\n\nCreane una qui sopra, oppure dai tre puntini\n" +
                    "di un brano che vuoi metterci dentro."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .glass()
                            .clickable { vm.openPlaylist(p.id) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))
                                .background(Vetro.Glass),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (p.coverPath != null) {
                                AsyncImage(
                                    model = File(p.coverPath),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp),
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic, null,
                                    tint = Vetro.InkFaint,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Vetro.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (p.trackCount == 1) "1 brano" else "${p.trackCount} brani",
                                style = MaterialTheme.typography.bodySmall,
                                color = Vetro.InkFaint,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creando) {
        NamePlaylistDialog(
            titolo = "Nuova playlist",
            iniziale = "",
            onConfirm = { vm.createPlaylist(it); creando = false },
            onDismiss = { creando = false },
        )
    }

    if (mescolando) {
        MixPlaylistsSheet(
            playlists = playlists,
            onConfirm = { scelte -> vm.mixAndPlay(scelte); mescolando = false },
            onDismiss = { mescolando = false },
        )
    }
}

/**
 * Il foglio per scegliere quali playlist mischiare insieme.
 *
 * Un `ModalBottomSheet` invece di un `AlertDialog`: la lista di playlist
 * puo' essere lunga, e un foglio scorre dove un dialogo si schiaccerebbe.
 * Ogni riga si tocca per selezionarla — un cerchio pieno o vuoto, come le
 * proposte di abbinamento nell'import — e il tasto in fondo resta spento
 * finche' le scelte non sono almeno due: mischiarne una sola non vuol
 * dire niente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixPlaylistsSheet(
    playlists: List<PlaylistSummary>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selezionate = remember { mutableStateListOf<Long>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vetro.Ground,
        contentColor = Vetro.Ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                "Mescola più playlist",
                style = MaterialTheme.typography.titleSmall,
                color = Vetro.Ink,
            )
            Text(
                "scegline due o più: si uniscono e si mischiano in una coda sola",
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkFaint,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
        }

        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(playlists, key = { it.id }) { p ->
                val scelta = p.id in selezionate
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (scelta) selezionate.remove(p.id) else selezionate.add(p.id)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)).background(Vetro.Glass),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (p.coverPath != null) {
                            AsyncImage(
                                model = File(p.coverPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp),
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic, null,
                                tint = Vetro.InkFaint,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Vetro.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (p.trackCount == 1) "1 brano" else "${p.trackCount} brani",
                            style = MaterialTheme.typography.bodySmall,
                            color = Vetro.InkFaint,
                        )
                    }
                    Icon(
                        if (scelta) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (scelta) Vetro.Accent else Vetro.InkFaint,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Button(
            onClick = { onConfirm(selezionate.toList()) },
            enabled = selezionate.size >= 2,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(
                if (selezionate.size < 2) "Scegli almeno due playlist"
                else "Mescola ${selezionate.size} playlist",
            )
        }
    }
}

@UnstableApi
@Composable
private fun PlaylistDetail(vm: MainViewModel) {
    val id by vm.openPlaylist.collectAsState()
    val nome by vm.openPlaylistName.collectAsState()
    val tracks by vm.openPlaylistTracks.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    val downloads by vm.downloads.collectAsState()
    val downloadByVideoId = downloads.associateBy { it.track.videoId }

    var rinominando by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf(false) }
    val pid = id ?: return

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.openPlaylist(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Vetro.InkSoft)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    nome,
                    style = MaterialTheme.typography.titleMedium,
                    color = Vetro.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (tracks.size == 1) "1 brano" else "${tracks.size} brani",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
            IconButton(onClick = { rinominando = true }) {
                Icon(Icons.Default.Edit, "Rinomina", tint = Vetro.InkFaint)
            }
            IconButton(onClick = { eliminando = true }) {
                Icon(Icons.Default.Delete, "Elimina playlist", tint = Vetro.InkFaint)
            }
            if (tracks.isNotEmpty()) {
                IconButton(onClick = { vm.shufflePlay(tracks) }) {
                    Icon(Icons.Default.Shuffle, "Ascolta a caso", tint = Vetro.InkSoft)
                }
                IconButton(onClick = { vm.play(tracks, 0) }) {
                    Icon(Icons.Default.PlayArrow, "Riproduci tutto", tint = accent.color)
                }
            }
        }

        if (tracks.isEmpty()) {
            Vuoto("Playlist vuota.\n\nApri i tre puntini di un brano\ne aggiungilo da lì.")
        } else {
            val listState = rememberLazyListState()
            val drag = rememberDragReorder(listState) { from, to ->
                vm.movePlaylistTrack(pid, from, to)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().dragReorder(drag),
            ) {
                itemsIndexed(tracks, key = { _, t -> t.videoId }) { index, track ->
                    val inMano = drag.draggingIndex == index
                    Box(
                        Modifier
                            .zIndex(if (inMano) 1f else 0f)
                            .graphicsLayer { translationY = if (inMano) drag.offset else 0f }
                    ) {
                        TrackRow(
                            title = track.title,
                            senzaFile = !track.haFile,
                            subtitle = "${track.artist} · ${track.durationText}",
                            coverPath = track.coverPath,
                            playing = stato.current?.videoId == track.videoId,
                            accent = accent.color,
                            download = CoverDownload.da(downloadByVideoId[track.videoId]),
                            onClick = { vm.play(tracks, index) },
                            onDetails = { vm.showDetails(track) },
                            trailing = {
                                Icon(
                                    Icons.Default.DragHandle, null,
                                    tint = Vetro.InkFaint,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (rinominando) {
        NamePlaylistDialog(
            titolo = "Rinomina playlist",
            iniziale = nome,
            onConfirm = { vm.renamePlaylist(pid, it); rinominando = false },
            onDismiss = { rinominando = false },
        )
    }

    if (eliminando) {
        AlertDialog(
            onDismissRequest = { eliminando = false },
            containerColor = Vetro.Ground,
            title = { Text("Eliminare «$nome»?", color = Vetro.Ink) },
            text = {
                Text(
                    "La playlist sparisce, ma i brani restano sul telefono.",
                    color = Vetro.InkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaylist(pid); eliminando = false }) {
                    Text("Elimina", color = Vetro.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { eliminando = false }) {
                    Text("Annulla", color = Vetro.InkSoft)
                }
            },
        )
    }
}

@Composable
private fun Vuoto(testo: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            testo,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Vetro.InkFaint,
        )
    }
}
