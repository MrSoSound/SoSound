package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass
import java.io.File

/**
 * La scheda che si apre toccando i tre puntini su un brano.
 *
 * Esiste per una ragione precisa: nelle liste titolo e artista vengono
 * troncati, e senza questa scheda non c'era nessun modo di leggerli per
 * intero. Oltre al testo completo mostra quello che una lista non ha
 * spazio per dire — album, formato, peso — e raccoglie le azioni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun TrackSheet(vm: MainViewModel) {
    val details by vm.details.collectAsState()
    val d = details ?: return

    val playlists by vm.playlists.collectAsState()
    val inPlaylists by vm.detailsPlaylists.collectAsState()
    val accent by vm.accent.collectAsState()

    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { vm.closeDetails() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vetro.Ground,
        contentColor = Vetro.Ink,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------------------------------------- intestazione
            //
            // La copertina grande e centrata, con sotto un alone del suo
            // stesso colore. E' la stessa tinta che gia' governa i
            // comandi del player: la scheda di un brano e quel brano in
            // riproduzione si somigliano perche' sono la stessa cosa
            // vista da due posti.
            val tinta by animateColorAsState(accent.color, tween(500), label = "tintaScheda")

            Box(
                Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                // L'alone: un cerchio sfocato dietro la copertina. Fatto
                // con un gradiente e non con un'ombra, perche' un'ombra
                // colorata su fondo scuro non si vede.
                Box(
                    Modifier
                        .size(190.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(tinta.copy(alpha = 0.40f), Color.Transparent)
                            ),
                            CircleShape,
                        )
                )
                Box(
                    Modifier
                        .size(156.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Vetro.Glass),
                    contentAlignment = Alignment.Center,
                ) {
                    val model = d.coverPath?.let { File(it) } ?: d.coverUrl
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote, null,
                            tint = Vetro.InkFaint,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Nessun maxLines: e' tutto il senso di questa scheda.
                Text(
                    d.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Vetro.Ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    d.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Vetro.InkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Sotto il nome, in piccolo, cosa ne e' di questo brano:
                // e' l'informazione che cambia il significato di tutti i
                // tasti qui sotto.
                Text(
                    when {
                        d.owned == null -> "Non è ancora nella tua libreria"
                        !d.owned.haFile -> "Si ascolta dalla rete · ${d.durationText}"
                        else -> "Tuo, anche senza rete · ${d.durationText}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.owned?.haFile == true) tinta else Vetro.InkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ------------------------------------------------ i dettagli
            Column(Modifier.fillMaxWidth().glass().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Artista, album e programma sono collegamenti quando il
                // catalogo ci ha dato un riferimento: prima erano testo
                // morto, e per arrivare all'artista bisognava ricercarlo.
                val c = d.catalog
                // Per una puntata l'«artista» e' il programma, e chiamarlo
                // artista confonderebbe: il collegamento al programma sta
                // piu' sotto, con la sua etichetta.
                when {
                    c?.showId != null -> Unit
                    c?.artistId != null -> Detail("Artista", d.artist, accent.color) {
                        vm.browseFromDetails(c.artistId, "artista")
                    }
                    else -> Detail("Artista", d.artist)
                }

                d.album?.let { nome ->
                    c?.albumId?.let { id ->
                        Detail("Album", nome, accent.color) {
                            vm.browseFromDetails(id, "album")
                        }
                    } ?: Detail("Album", nome)
                }

                c?.showId?.let { id ->
                    Detail("Podcast", c.artist, accent.color) {
                        vm.browseFromDetails(id, "podcast")
                    }
                }

                Detail("Durata", d.durationText)
                d.owned?.let {
                    Detail("Formato", "${it.formatText} · ${it.sizeText}")
                } ?: Detail("Sul telefono", "no, non ancora scaricato")

                c?.description?.let { testo ->
                    Text(
                        testo,
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                        maxLines = 6,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // -------------------------------------------------- le azioni
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Action(
                    icon = Icons.Default.PlayArrow,
                    label = if (d.owned != null) "Riproduci" else "Scarica e riproduci",
                    tint = accent.color,
                ) {
                    d.owned?.let { vm.play(listOf(it), 0) }
                        ?: d.catalog?.let { vm.playFromSearch(it) }
                    vm.closeDetails()
                }

                if (d.owned != null) {
                    Action(Icons.AutoMirrored.Filled.PlaylistPlay, "Riproduci dopo", Vetro.InkSoft) {
                        vm.playNext(d.owned)
                        vm.closeDetails()
                    }
                    Action(Icons.AutoMirrored.Filled.QueueMusic, "Aggiungi alla coda", Vetro.InkSoft) {
                        vm.addToQueue(d.owned)
                        vm.closeDetails()
                    }
                    Action(Icons.Default.Delete, "Togli dal telefono", Vetro.Danger) {
                        confirmDelete = true
                    }
                }
            }

            // ------------------------------------------------- playlist
            //
            // Anche per un brano che non e' ancora nostro: prima
            // bisognava scaricarlo e aspettare la fine per poterlo
            // mettere in una playlist — due passaggi e un'attesa per un
            // gesto che nella testa di chi lo fa e' uno solo.
            if (d.owned != null || d.catalog != null) {
                Text(
                    "Playlist",
                    style = MaterialTheme.typography.labelLarge,
                    color = Vetro.InkFaint,
                )

                if (d.owned != null && !d.owned.haFile) {

                    // Il brano e' in libreria ma senza file: si sente dalla rete.

                    // Scaricarlo non serve ad ascoltarlo — serve a non dipendere

                    // dalla rete per riascoltarlo, ed e' una cosa diversa.

                    Action(Icons.Default.DownloadForOffline, "Tieni anche senza rete", accent.color) {

                        vm.tieniSenzaRete(d.videoId)

                    }

                }


                Action(Icons.Default.Add, "Nuova playlist", Vetro.InkSoft) { creating = true }

                // Column e non LazyColumn: una lista pigra dentro un
                // contenitore che scorre gia' e' una fonte di guai, e qui
                // le playlist sono poche per definizione.
                Column {
                    playlists.forEach { p ->
                        val dentro = p.id in inPlaylists
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (dentro) vm.removeFromPlaylist(p.id, d.videoId)
                                    else vm.aggiungiAPlaylistDaiDettagli(p.id)
                                }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                if (dentro) Icons.Default.Check else Icons.Default.PlaylistAdd,
                                null,
                                tint = if (dentro) accent.color else Vetro.InkFaint,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (dentro) Vetro.Ink else Vetro.InkSoft,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${p.trackCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Vetro.InkFaint,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NamePlaylistDialog(
            titolo = "Nuova playlist",
            iniziale = "",
            onConfirm = { nome ->
                vm.createPlaylist(nome, addAfter = d.videoId)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Vetro.Ground,
            title = { Text("Togliere dal telefono?", color = Vetro.Ink) },
            text = {
                Text(
                    "«${d.title}» verrà cancellato, e tolto da tutte le playlist. " +
                        "Puoi riscaricarlo quando vuoi.",
                    color = Vetro.InkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    d.owned?.let { vm.remove(it) }
                    confirmDelete = false
                }) { Text("Cancella", color = Vetro.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Annulla", color = Vetro.InkSoft)
                }
            },
        )
    }
}

/**
 * Una riga di dettaglio. Con [onClick] diventa un collegamento, e deve
 * vedersi: prima cambiava solo colore, e nessuno capiva che si potesse
 * toccare. Ora c'e' anche la freccia, che e' il segno che tutti
 * riconoscono come «qui si va da qualche parte».
 */
@Composable
private fun Detail(
    etichetta: String,
    valore: String,
    tinta: androidx.compose.ui.graphics.Color = Vetro.InkSoft,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = if (onClick != null) 7.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            etichetta,
            style = MaterialTheme.typography.bodySmall,
            color = Vetro.InkFaint,
            modifier = Modifier.width(88.dp),
        )
        Text(
            valore,
            style = MaterialTheme.typography.bodySmall,
            color = tinta,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = tinta,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun Action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Vetro.Ink)
    }
}

@Composable
fun NamePlaylistDialog(
    titolo: String,
    iniziale: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var nome by remember { mutableStateOf(iniziale) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vetro.Ground,
        title = { Text(titolo, color = Vetro.Ink) },
        text = {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                singleLine = true,
                placeholder = { Text("Nome", color = Vetro.InkFaint) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nome) }) { Text("Salva", color = Vetro.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla", color = Vetro.InkSoft) }
        },
    )
}
