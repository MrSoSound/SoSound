package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.activity.compose.BackHandler
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.catalog.SearchKind
import com.sosound.app.data.download.QueueItem
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass

@UnstableApi
@Composable
fun SearchScreen(
    vm: MainViewModel,
    /**
     * Il tipo di QUESTA pagina, che non e' sempre quello attivo.
     *
     * Le quattro pagine del «Cerca» stanno una accanto all'altra sul
     * nastro: mentre una scorre, la vicina e' gia' disegnata e deve
     * mostrare il proprio contenuto, se no si vedrebbe la stessa cosa
     * scivolare due volte.
     */
    kindPagina: SearchKind = SearchKind.BRANI,
) {
    val album by vm.albumPage.collectAsState()
    val artista by vm.artistPage.collectAsState()
    val podcast by vm.podcastPage.collectAsState()
    val playlistPubblica by vm.playlistPage.collectAsState()
    val sfogliando by vm.browsing.collectAsState()
    val indietro by vm.canGoBack.collectAsState()

    // Il tasto indietro risale la pila una pagina per volta: da un album
    // all'artista da cui si era arrivati, e solo alla fine alla ricerca.
    BackHandler(enabled = indietro) { vm.browseBack() }

    when {
        sfogliando -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        album != null -> AlbumScreen(vm, album!!)
        artista != null -> ArtistScreen(vm, artista!!)
        podcast != null -> PodcastScreen(vm, podcast!!)
        playlistPubblica != null -> PlaylistScreen(vm, playlistPubblica!!)
        else -> Ricerca(vm, kindPagina)
    }
}

/**
 * Il campo di ricerca e i chip dei tipi.
 *
 * Sta fuori dal nastro perche' non deve scorrere con i risultati:
 * dentro, si vedevano quattro copie del campo passare una dopo l'altra
 * mentre si cambiava tipo — e un campo di ricerca che scivola via
 * mentre lo si guarda non e' un campo di ricerca.
 */
@UnstableApi
@Composable
fun BarraRicerca(vm: MainViewModel, kindPagina: SearchKind) {
    val ui by vm.search.collectAsState()
    val accent by vm.accent.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        GlassField(
            value = ui.query,
            onValueChange = vm::onQueryChange,
            placeholder = when (kindPagina) {
                SearchKind.BRANI -> "Cerca un brano"
                SearchKind.ALBUM -> "Cerca un album"
                SearchKind.ARTISTI -> "Cerca un artista"
                SearchKind.PODCAST -> "Cerca un episodio di podcast"
                SearchKind.PLAYLIST -> "Cerca una playlist"
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchKind.entries.forEach { k ->
                val scelto = kindPagina == k
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (scelto) Vetro.GlassStrong else Vetro.Glass)
                        .clickable { vm.onKindChange(k) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        k.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (scelto) accent.color else Vetro.InkFaint,
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun Ricerca(vm: MainViewModel, kindPagina: SearchKind) {
    val ui by vm.search.collectAsState()
    val owned by vm.ownedIds.collectAsState()
    val queue by vm.downloads.collectAsState()
    val ready by vm.engineReady.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    var mostraCoda by remember { mutableStateOf(false) }

    // Un brano scaricato compare qui per il suo videoId: e' quello che
    // dice alla riga se disegnare l'anello sopra la copertina, e a che
    // punto e'. Una mappa sola, ricalcolata quando cambia la coda — non
    // ogni riga per conto suo, che vorrebbe dire scorrerla N volte.
    val downloadByVideoId = queue.associateBy { it.track.videoId }

    Column(Modifier.fillMaxSize()) {
        if (!ready) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Preparo il motore di scaricamento…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
        }

        // L'elenco che stava qui — le prime righe della coda, testuali,
        // in cima ai risultati — invadeva lo spazio della ricerca anche
        // per un solo brano. Adesso il progresso si vede sulla copertina
        // di ogni riga, e qui resta solo una chip: dice se c'e' qualcosa
        // da guardare, non lo mostra per forza.
        if (queue.isNotEmpty()) {
            DownloadsChip(queue = queue, onClick = { mostraCoda = true })
        }

        when {
            ui.loading && ui.results.isEmpty ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            ui.error != null -> Messaggio("Ricerca non riuscita.\n${ui.error}", Vetro.Danger)

            // A campo vuoto non si mostra un invito e basta: si mostra
            // cosa gira adesso. È la differenza fra una casella di
            // ricerca e un posto dove si scopre qualcosa.
            ui.query.isBlank() -> Proposte(vm, kindPagina)

            ui.results.isEmpty && !ui.loading -> Messaggio("Nessun risultato", Vetro.InkFaint)

            else -> LazyColumn(Modifier.fillMaxSize()) {
                // I programmi per primi: cercando «supernova» si vuole
                // il podcast, non la sua terza puntata.
                if (ui.results.shows.isNotEmpty()) {
                    item { Titolo("Programmi") }
                    items(ui.results.shows, key = { it.browseId }) { sh ->
                        RigaCatalogo(
                            title = sh.title,
                            subtitle = sh.publisher.orEmpty(),
                            thumbnail = sh.thumbnail,
                            fallback = Icons.Default.Mic,
                            tonda = false,
                        ) { vm.openPodcast(sh.browseId) }
                    }
                    item { Titolo("Puntate") }
                }

                items(ui.results.tracks, key = { it.videoId }) { track ->
                    val voce = downloadByVideoId[track.videoId]
                    val queued = voce != null && voce.state != QueueItem.State.ERRORE
                    val staCaricando = stato.current?.videoId == track.videoId && stato.isBuffering
                    ResultRow(
                        track = track,
                        owned = track.videoId in owned,
                        queued = queued,
                        // Lo stesso brano appena toccato: se sta ancora
                        // risolvendo il suo indirizzo in streaming (nessuna
                        // voce in coda di scaricamento, solo il player che
                        // aspetta), l'anello compare comunque qui — non solo
                        // sulla copertina del player, che magari non si sta
                        // guardando in quel momento.
                        download = CoverDownload.da(voce) ?: CoverDownload.InAttesa.takeIf { staCaricando },
                        enabled = ready,
                        playing = stato.current?.videoId == track.videoId,
                        accent = accent.color,
                        onPlay = { vm.playFromSearch(track) },
                        onAdd = { vm.add(track) },
                        onDetails = { vm.showDetails(track) },
                    )
                }

                items(ui.results.albums, key = { it.browseId }) { a ->
                    RigaCatalogo(
                        title = a.title,
                        subtitle = a.subtitle,
                        thumbnail = a.thumbnail,
                        fallback = Icons.Default.Album,
                        tonda = false,
                    ) { vm.openAlbum(a.browseId) }
                }

                items(ui.results.artists, key = { it.browseId }) { ar ->
                    RigaCatalogo(
                        title = ar.name,
                        subtitle = ar.subtitle.orEmpty(),
                        thumbnail = ar.thumbnail,
                        fallback = Icons.Default.Person,
                        // Le foto degli artisti sono ritratti: tonde si
                        // distinguono a colpo d'occhio dalle copertine.
                        tonda = true,
                    ) { vm.openArtist(ar.browseId) }
                }

                items(ui.results.playlists, key = { it.browseId }) { p ->
                    RigaCatalogo(
                        title = p.title,
                        subtitle = p.subtitle,
                        thumbnail = p.thumbnail,
                        fallback = Icons.AutoMirrored.Filled.QueueMusic,
                        tonda = false,
                    ) { vm.browsePlaylist(p.browseId) }
                }
            }
        }
    }

    // Se nel frattempo la coda si e' svuotata (l'ultimo brano e' finito,
    // o e' stato rinunciato dal foglio stesso) non ha senso restare
    // aperti su un elenco vuoto: si chiude da solo.
    LaunchedEffect(queue.isEmpty()) { if (queue.isEmpty()) mostraCoda = false }

    if (mostraCoda) {
        DownloadsSheet(
            queue = queue,
            onRetry = vm::retryDownload,
            onDismiss = vm::dismiss,
            onClose = { mostraCoda = false },
        )
    }
}

/**
 * Quello che YouTube Music propone adesso, per il tipo scelto.
 *
 * Ogni filtro mostra la sua roba: sotto «Brani» le tendenze, sotto
 * «Artisti» la classifica, sotto «Album» le uscite recenti. Impilarle
 * tutte sotto un filtro solo vorrebbe dire che gli altri tre non servono
 * a niente finche' non scrivi.
 */
@UnstableApi
@Composable
private fun Proposte(vm: MainViewModel, kind: SearchKind) {
    val feed by vm.discover.collectAsState()
    val caricando by vm.discovering.collectAsState()
    val owned by vm.ownedIds.collectAsState()
    val stato by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    val queue by vm.downloads.collectAsState()
    val downloadByVideoId = queue.associateBy { it.track.videoId }

    LaunchedEffect(Unit) { vm.loadDiscover() }

    // Per i podcast non c'e' niente da proporre, e non e' una mancanza
    // nostra: YouTube Music non pubblica una classifica dei podcast — le
    // pagine che la conterrebbero rispondono 404. Meglio dirlo che
    // riempire lo spazio con risultati di una ricerca travestiti da
    // classifica.
    if (kind == SearchKind.PODCAST) {
        val miei by vm.myShows.collectAsState()
        if (miei.isEmpty()) {
            Messaggio(
                "YouTube Music non pubblica una classifica dei podcast.\n\n" +
                    "Scrivi il nome di un programma o un argomento:\ntrovi le puntate, " +
                    "e da lì si arriva al programma.",
                Vetro.InkFaint,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Titolo("I podcast che segui") }
                items(miei, key = { it.videoId }) { t ->
                    RigaCatalogo(
                        title = t.album ?: t.artist,
                        subtitle = "puntate sul telefono",
                        thumbnail = null,
                        fallback = Icons.Default.Mic,
                        tonda = false,
                    ) { t.showId?.let { vm.openPodcast(it) } }
                }
                item {
                    Text(
                        "Una classifica dei podcast YouTube Music non la pubblica: " +
                            "per trovarne di nuovi, scrivi qui sopra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        return
    }

    if (caricando && feed.isEmpty) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        when (kind) {
            SearchKind.BRANI -> {
                if (feed.trending.isEmpty()) {
                    item { Messaggio("Scrivi per cercare un brano.", Vetro.InkFaint) }
                } else {
                    item { Titolo("Di tendenza adesso") }
                    itemsIndexed(feed.trending) { i, t ->
                        TrackRow(
                            // Il numero rende esplicito che e' una
                            // classifica: senza, sembra un elenco a caso.
                            title = "${i + 1}. ${t.title}",
                            subtitle = listOfNotNull(t.artist, t.durationText).joinToString(" · "),
                            coverPath = null,
                            coverUrl = t.thumbnail,
                            playing = stato.current?.videoId == t.videoId,
                            accent = accent.color,
                            explicit = t.explicit == true,
                            download = CoverDownload.da(downloadByVideoId[t.videoId]),
                            onClick = { vm.playFromSearch(t) },
                            onDetails = { vm.showDetails(t) },
                            trailing = {
                                if (t.videoId in owned) {
                                    Icon(
                                        Icons.Default.CheckCircle, "Già sul telefono",
                                        tint = accent.color, modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            SearchKind.ARTISTI -> {
                if (feed.topArtists.isEmpty()) {
                    item { Messaggio("Scrivi per cercare un artista.", Vetro.InkFaint) }
                } else {
                    item { Titolo("Artisti più ascoltati") }
                    itemsIndexed(feed.topArtists) { i, a ->
                        RigaCatalogo(
                            title = "${i + 1}. ${a.name}",
                            subtitle = a.subtitle.orEmpty(),
                            thumbnail = a.thumbnail,
                            fallback = Icons.Default.Person,
                            tonda = true,
                        ) { vm.openArtist(a.browseId) }
                    }
                }
            }

            SearchKind.ALBUM -> {
                if (feed.newAlbums.isEmpty()) {
                    item { Messaggio("Scrivi per cercare un album.", Vetro.InkFaint) }
                } else {
                    // Non numerati, e il titolo lo dice: qui non c'e' una
                    // classifica. YouTube Music pubblica le uscite
                    // recenti, non gli album piu' ascoltati — numerarli
                    // farebbe credere a un ordine che non esiste.
                    item { Titolo("Usciti da poco") }
                    items(feed.newAlbums, key = { it.browseId }) { a ->
                        RigaCatalogo(
                            title = a.title,
                            subtitle = a.subtitle,
                            thumbnail = a.thumbnail,
                            fallback = Icons.Default.Album,
                            tonda = false,
                        ) { vm.openAlbum(a.browseId) }
                    }
                }
            }

            SearchKind.PODCAST -> Unit   // gestito sopra

            // Niente da proporre a campo vuoto: le playlist "di
            // tendenza" vorrebbero un'altra interrogazione a parte, e non
            // e' quello che questo filtro serve a fare — trovare una
            // playlist per nome, non sfogliarne una classifica.
            SearchKind.PLAYLIST -> item {
                Messaggio("Scrivi per cercare una playlist.", Vetro.InkFaint)
            }
        }
    }
}

@Composable
private fun Titolo(testo: String) {
    Text(
        testo,
        style = MaterialTheme.typography.labelLarge,
        color = Vetro.InkFaint,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun RigaCatalogo(
    title: String,
    subtitle: String,
    thumbnail: String?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector,
    tonda: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .glass()
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(if (tonda) CircleShape else RoundedCornerShape(9.dp))
                .background(Vetro.Glass),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(fallback, null, tint = Vetro.InkFaint)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Vetro.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Messaggio(testo: String, colore: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            testo,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = colore,
        )
    }
}

/**
 * Una voce della coda, con la copertina e il suo anello — lo stesso
 * componente che si vede sulle righe dei risultati, qui dentro il
 * foglio che elenca tutto quello che sta scaricando.
 *
 * Niente piu' barra lineare separata: il progresso lo dice l'anello, ed
 * e' lo stesso linguaggio ovunque in app.
 */
@Composable
private fun QueueRow(item: QueueItem, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).background(Vetro.Glass),
            contentAlignment = Alignment.Center,
        ) {
            if (item.track.thumbnail != null) {
                AsyncImage(
                    model = item.track.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = Vetro.InkFaint)
            }
            CoverDownload.da(item)?.let { CoverDownloadOverlay(it, accent = Vetro.Accent) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${item.track.artist} — ${item.track.title}",
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                item.error != null ->
                    Text(item.error, style = MaterialTheme.typography.bodySmall, color = Vetro.Danger)
                item.note != null ->
                    Text(item.note, style = MaterialTheme.typography.bodySmall, color = Vetro.Accent)
                item.state == QueueItem.State.IN_ATTESA ->
                    Text("In coda", style = MaterialTheme.typography.bodySmall, color = Vetro.InkFaint)
                else -> Text(
                    "Scarico… ${(item.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
        }
        if (item.state == QueueItem.State.ERRORE) {
            // Prima c'era solo la ics per farlo sparire: l'unica cosa
            // che si poteva fare con un brano fallito era rinunciarci.
            IconButton(onClick = onRetry, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, "Riprova", Modifier.size(18.dp), tint = Vetro.Accent)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Rinuncia", Modifier.size(18.dp), tint = Vetro.InkFaint)
            }
        }
    }
}

/**
 * La chip che sostituisce l'elenco che stava in cima a «Cerca».
 *
 * Compare solo quando c'e' qualcosa in coda, e dice il minimo che serve
 * per decidere se aprire il foglio: quanti in corso, e se qualcuno ha
 * bisogno di una persona — con un colore che non si confonde con il
 * resto, cosi' un errore non si perde in mezzo a una coda lunga.
 */
@Composable
private fun DownloadsChip(queue: List<QueueItem>, onClick: () -> Unit) {
    val attivi = queue.count {
        it.state == QueueItem.State.IN_CORSO ||
            it.state == QueueItem.State.AGGIORNO ||
            it.state == QueueItem.State.IN_ATTESA
    }
    val errori = queue.count { it.state == QueueItem.State.ERRORE }

    val testo = buildString {
        if (attivi > 0) append(if (attivi == 1) "1 in scaricamento" else "$attivi in scaricamento")
        if (errori > 0) {
            if (isNotEmpty()) append(" · ")
            append(if (errori == 1) "1 non riuscito" else "$errori non riusciti")
        }
    }

    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(CircleShape)
            .background(if (errori > 0) Vetro.Danger.copy(alpha = 0.16f) else Vetro.Glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (errori > 0) {
            Icon(
                Icons.Default.ErrorOutline, null,
                tint = Vetro.Danger, modifier = Modifier.size(16.dp),
            )
        } else {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Vetro.Accent)
        }
        Text(
            testo,
            style = MaterialTheme.typography.labelMedium,
            color = if (errori > 0) Vetro.Danger else Vetro.Ink,
        )
    }
}

/**
 * Il foglio con tutta la coda — quello che prima si vedeva spalmato in
 * cima ai risultati. Qui dentro c'e' spazio per ogni voce, comprese
 * quelle che prima sparivano oltre le prime quattro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsSheet(
    queue: List<QueueItem>,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vetro.Ground,
        contentColor = Vetro.Ink,
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item {
                Text(
                    "In scaricamento",
                    style = MaterialTheme.typography.titleSmall,
                    color = Vetro.Ink,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(queue, key = { it.track.videoId }) { voce ->
                QueueRow(
                    item = voce,
                    onRetry = { onRetry(voce.track.videoId) },
                    onDismiss = { onDismiss(voce.track.videoId) },
                )
            }
            item { Box(Modifier.padding(bottom = 12.dp)) }
        }
    }
}

@Composable
private fun ResultRow(
    track: CatalogTrack,
    owned: Boolean,
    queued: Boolean,
    download: CoverDownload?,
    enabled: Boolean,
    playing: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    onDetails: () -> Unit,
) {
    TrackRow(
        title = track.title,
        subtitle = track.subtitle,
        coverPath = null,
        coverUrl = track.thumbnail,
        playing = playing,
        accent = accent,
        explicit = track.explicit == true,
        download = download,
        // Tocco sulla riga = parte. Il "+" resta per chi vuole solo
        // scaricare senza interrompere quello che sta ascoltando.
        onClick = { if (enabled) onPlay() },
        onDetails = onDetails,
        trailing = {
            when {
                owned -> Icon(
                    Icons.Default.CheckCircle, "Già sul telefono",
                    tint = accent, modifier = Modifier.size(20.dp),
                )
                // In coda: il progresso si vede gia' sulla copertina,
                // un secondo indicatore qui accanto ripeterebbe la
                // stessa informazione due volte nella stessa riga.
                queued -> Unit
                else -> IconButton(onClick = onAdd, enabled = enabled) {
                    Icon(
                        Icons.Default.AddCircleOutline, "Scarica senza riprodurre",
                        tint = Vetro.InkFaint,
                    )
                }
            }
        },
    )
}
