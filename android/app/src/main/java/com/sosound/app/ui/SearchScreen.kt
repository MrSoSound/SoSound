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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

        if (queue.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                queue.take(4).forEach { voce ->
                    QueueRow(
                        item = voce,
                        onRetry = { vm.retryDownload(voce.track.videoId) },
                        onDismiss = { vm.dismiss(voce.track.videoId) },
                    )
                }
                if (queue.size > 4) {
                    Text(
                        "…e altri ${queue.size - 4} in coda",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }
            }
            HorizontalDivider()
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
                    val queued = queue.any {
                        it.track.videoId == track.videoId && it.state != QueueItem.State.ERRORE
                    }
                    ResultRow(
                        track = track,
                        owned = track.videoId in owned,
                        queued = queued,
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
            }
        }
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
                            subtitle = t.artist,
                            coverPath = null,
                            coverUrl = t.thumbnail,
                            playing = stato.current?.videoId == t.videoId,
                            accent = accent.color,
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

@Composable
private fun QueueRow(item: QueueItem, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${item.track.artist} — ${item.track.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Vetro.Accent)
                }
                item.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Vetro.Danger)
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
        if (item.state != QueueItem.State.ERRORE) {
            LinearProgressIndicator(
                progress = { item.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResultRow(
    track: CatalogTrack,
    owned: Boolean,
    queued: Boolean,
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
                queued -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
