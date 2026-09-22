package com.sosound.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.importing.Confidence
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass

/**
 * Importa una playlist da un altro servizio.
 *
 * Senza autenticazioni: si parte dal file che gli esportatori producono
 * gia' (Exportify, TuneMyMusic), oppure da un elenco incollato. Ogni riga
 * viene cercata sul catalogo, e quello che non e' sicuro viene mostrato
 * da rivedere invece di essere indovinato in silenzio.
 */
@UnstableApi
@Composable
fun ImportScreen(vm: MainViewModel, onClose: () -> Unit) {
    val stato by vm.importState.collectAsState()
    val context = LocalContext.current

    val apriFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val nome = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.trim()
            .orEmpty()
            .ifBlank { "Importata" }
        val testo = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (testo.isNullOrBlank()) vm.resetImport() else vm.startImport(testo, nome)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.resetImport(); onClose() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Vetro.InkSoft)
            }
            Text(
                "Importa una playlist",
                style = MaterialTheme.typography.titleMedium,
                color = Vetro.Ink,
            )
        }

        when (val s = stato) {
            is ImportState.Idle -> Inizio(
                onFile = { apriFile.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "*/*")) },
                onPaste = { testo ->
                    // Un link si riconosce da solo: chiedere all'utente
                    // «e' un link o un elenco?» sarebbe una domanda a cui
                    // la macchina sa gia' rispondere.
                    if (com.sosound.app.data.importing.SpotifyLink.looksLikeLink(testo)) {
                        vm.startImportFromLink(testo)
                    } else if (com.sosound.app.data.importing.YtMusicLink.looksLikeLink(testo)) {
                        vm.startImportFromYtMusicLink(testo)
                    } else {
                        vm.startImport(testo, "Importata")
                    }
                },
            )

            is ImportState.Working -> Box(
                Modifier.fillMaxSize().padding(32.dp), Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Cerco i brani sul catalogo…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Vetro.Ink,
                    )
                    Text(
                        "${s.done} di ${s.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    )
                    LinearProgressIndicator(
                        progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is ImportState.Review -> Revisione(vm, s)

            is ImportState.Done -> Box(
                Modifier.fillMaxSize().padding(32.dp), Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "«${s.playlistName}» creata.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Vetro.Ink,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (s.queued == 0) "Erano già tutti sul telefono."
                        else "${s.queued} brani in scaricamento: li trovi nella playlist man mano che arrivano.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                    )
                    Button(onClick = { vm.resetImport(); onClose() }) { Text("Chiudi") }
                }
            }

            is ImportState.Failed -> Box(
                Modifier.fillMaxSize().padding(32.dp), Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Vetro.InkSoft,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { vm.resetImport() },
                        modifier = Modifier.padding(top = 18.dp),
                    ) { Text("Riprova") }
                }
            }
        }
    }
}

@Composable
private fun Inizio(onFile: () -> Unit, onPaste: (String) -> Unit) {
    var testo by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().glass().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Da un file", style = MaterialTheme.typography.titleSmall, color = Vetro.Ink)
            Text(
                "Da Spotify: apri Exportify o TuneMyMusic nel browser, esporta la " +
                    "playlist in CSV e scegli qui il file. Non serve collegare nessun " +
                    "account a SoSound.\n\n" +
                    "Va bene anche un semplice file di testo scritto da te, una riga " +
                    "per brano.",
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkFaint,
            )
            Button(onClick = onFile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
                Text("  Scegli il file", style = MaterialTheme.typography.labelLarge)
            }
        }

        Column(Modifier.fillMaxWidth().glass().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Da un link, o incollando un elenco", style = MaterialTheme.typography.titleSmall, color = Vetro.Ink)
            Text(
                "Incolla il link di condivisione di una playlist o di un album " +
                    "Spotify, il link di una playlist di YouTube Music, oppure un " +
                    "elenco con una riga per brano: «Artista - Titolo», oppure " +
                    "«Titolo by Artista». La numerazione iniziale non dà fastidio.\n\n" +
                    "Anche i soli titoli funzionano, ma senza artista l'abbinamento " +
                    "è incerto e va controllato uno per uno.",
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkFaint,
            )
            GlassField(
                value = testo,
                onValueChange = { testo = it },
                placeholder = "https://open.spotify.com/playlist/…",
            )
            OutlinedButton(
                onClick = { onPaste(testo) },
                enabled = testo.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp))
                Text("  Leggi e cerca")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun Revisione(vm: MainViewModel, s: ImportState.Review) {
    var rinomina by remember { mutableStateOf(false) }
    // L'indice della riga di cui si sta scegliendo il brano — non il
    // brano stesso, cosi' il foglio resta agganciato agli aggiornamenti
    // dello stato quando l'utente sceglie.
    var scegliIndex by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().glass().clickable { rinomina = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Vetro.Ink,
                    )
                    Text(
                        "tocca per rinominare",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }
            }
            Text(
                "${s.sure} sicuri · ${s.unsure} da controllare · ${s.missing} non trovati",
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkFaint,
            )
        }

        // Compare solo quando piu' di una riga e' l'audio di un video e ha
        // gia' un'alternativa pronta: sostituirne una sola si fa dal
        // pulsante sulla riga stessa, questo serve solo quando conviene
        // farlo in blocco.
        if (s.daVideoSostituibili > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .glass()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.VideoLibrary, null, tint = Vetro.InkSoft, modifier = Modifier.size(20.dp))
                Text(
                    "${s.daVideoSostituibili} brani sono l'audio di un video",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.Ink,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.replaceAllVideoTracks() }) {
                    Text("Sostituisci tutti")
                }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(s.entries) { index, e ->
                // Si puo' aprire il foglio di scelta quando c'e' un
                // margine di dubbio: una riga incerta, o una non trovata
                // che pero' ha comunque qualche proposta a bassa fiducia —
                // prima non c'era modo di recuperarla a mano.
                val puoScegliere = e.confidence == Confidence.INCERTO ||
                    (e.confidence == Confidence.NON_TROVATO && e.alternatives.isNotEmpty())

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .glass()
                        .clickable(enabled = e.match != null) { vm.toggleEntry(index) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val (icona, tinta) = when {
                        e.match == null -> Icons.Default.SearchOff to Vetro.Danger
                        e.confidence == Confidence.INCERTO -> Icons.Default.HelpOutline to Vetro.InkSoft
                        e.selected -> Icons.Default.CheckCircle to Vetro.Accent
                        else -> Icons.Default.RadioButtonUnchecked to Vetro.InkFaint
                    }
                    Icon(icona, null, tint = tinta, modifier = Modifier.size(22.dp))

                    Column(Modifier.weight(1f)) {
                        // Prima cosa: quello che c'era nel file. È il
                        // riferimento che l'utente riconosce.
                        Text(
                            "${e.row.artist} — ${e.row.title}".trimStart(' ', '—'),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Vetro.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                e.match == null -> "non trovato"
                                e.alreadyOwned -> "↳ ce l'hai già: ${e.match.title}"
                                else -> "↳ ${e.match.artist} — ${e.match.title}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.match == null) Vetro.Danger else Vetro.InkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // L'utente ha scelto quella canzone, ma quello che
                        // sta per prendere e' il video, non l'incisione:
                        // meglio dirlo qui, dove sta decidendo, che
                        // scoprirlo dopo dentro la libreria.
                        if (e.match?.audioDiVideo == true) {
                            Text(
                                "audio di un video, non un'incisione",
                                style = MaterialTheme.typography.labelSmall,
                                color = Vetro.InkSoft,
                            )
                        }
                    }

                    val pulito = e.alternatives.firstOrNull { !it.audioDiVideo }
                    when {
                        // Priorita' a questo: se e' l'audio di un video e
                        // c'e' un'alternativa pulita pronta, il gesto piu'
                        // utile e' prenderla, non aprire il foglio delle
                        // alternative per ritrovarla a mano in mezzo alle
                        // altre.
                        e.match?.audioDiVideo == true && pulito != null ->
                            TextButton(onClick = { vm.pickForEntry(index, pulito) }) {
                                Text("Audio pulito")
                            }
                        // Un tasto vero, non piu' un filo di testo: prima
                        // scegliere un'alternativa voleva dire centrare una
                        // riga di 12sp con il dito, ed era il punto piu'
                        // scomodo di tutto l'import.
                        puoScegliere -> TextButton(onClick = { scegliIndex = index }) {
                            Text(if (e.match == null) "Scegli" else "Alternative")
                        }
                        e.match == null -> TextButton(onClick = { vm.searchManually(e) }) {
                            Text("Cerca a mano")
                        }
                    }
                }
            }
        }

        Button(
            onClick = { vm.confirmImport() },
            enabled = s.selectedCount > 0,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text(
                if (s.selectedCount == 0) "Non hai scelto niente"
                else "Importa ${s.selectedCount} brani",
            )
        }
    }

    if (rinomina) {
        NamePlaylistDialog(
            titolo = "Nome della playlist",
            iniziale = s.name,
            onConfirm = { vm.renameImport(it); rinomina = false },
            onDismiss = { rinomina = false },
        )
    }

    // Preso dallo stato corrente e non catturato all'apertura: se nel
    // frattempo l'utente sceglie un brano, il foglio deve vedere subito
    // la nuova situazione invece di mostrare quella di un attimo prima.
    scegliIndex?.let { index ->
        s.entries.getOrNull(index)?.let { entry ->
            ChooseMatchSheet(
                entry = entry,
                onPick = { candidato -> vm.pickForEntry(index, candidato); scegliIndex = null },
                onSearchManually = { vm.searchManually(entry); scegliIndex = null },
                onDismiss = { scegliIndex = null },
            )
        }
    }
}

/**
 * Il foglio per scegliere fra le proposte di una riga.
 *
 * Sostituisce i due link minuscoli che c'erano prima: ogni proposta è una
 * riga vera, alta abbastanza da toccarla senza mirare, con la copertina e
 * quello che serve per decidere — durata compresa, che è spesso l'unica
 * differenza fra due risultati altrimenti identici.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseMatchSheet(
    entry: ImportEntry,
    onPick: (CatalogTrack) -> Unit,
    onSearchManually: () -> Unit,
    onDismiss: () -> Unit,
) {
    var dettaglio by remember { mutableStateOf<CatalogTrack?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vetro.Ground,
        contentColor = Vetro.Ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Cercavamo", style = MaterialTheme.typography.labelSmall, color = Vetro.InkFaint)
            Text(
                "${entry.row.artist} — ${entry.row.title}".trimStart(' ', '—'),
                style = MaterialTheme.typography.titleSmall,
                color = Vetro.Ink,
            )
            entry.row.durationText?.let {
                Text(
                    "durata dichiarata: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            entry.match?.let { m ->
                SezioneEtichetta("Proposta migliore")
                CandidateRow(
                    candidate = m,
                    expectedDurationSeconds = entry.row.durationSeconds,
                    selected = entry.selected,
                    onClick = { onPick(m) },
                    onDetails = { dettaglio = m },
                )
            }
            if (entry.alternatives.isNotEmpty()) {
                SezioneEtichetta(if (entry.match != null) "Altre proposte" else "Nessuna sicura — forse una di queste")
                entry.alternatives.forEach { c ->
                    CandidateRow(
                        candidate = c,
                        expectedDurationSeconds = entry.row.durationSeconds,
                        selected = false,
                        onClick = { onPick(c) },
                        onDetails = { dettaglio = c },
                    )
                }
            }

            // Nessuna delle proposte era quella giusta: si esce dal
            // foglio dritti sulla scheda «Cerca», con questa riga gia'
            // scritta nel campo invece che a riscriverla da capo.
            SezioneEtichetta("Nessuna di queste?")
            TextButton(
                onClick = onSearchManually,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Cerca a mano")
            }
        }
    }

    dettaglio?.let { c ->
        CandidateDetailDialog(
            candidate = c,
            expectedDurationSeconds = entry.row.durationSeconds,
            onDismiss = { dettaglio = null },
            onPick = { onPick(c); dettaglio = null },
        )
    }
}

@Composable
private fun SezioneEtichetta(testo: String) {
    Text(
        testo.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Vetro.InkFaint,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** Una proposta dentro il foglio di scelta: copertina, testo, dettagli. */
@Composable
private fun CandidateRow(
    candidate: CatalogTrack,
    expectedDurationSeconds: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    onDetails: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Vetro.Glass),
            contentAlignment = Alignment.Center,
        ) {
            if (candidate.thumbnail != null) {
                AsyncImage(
                    model = candidate.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = Vetro.InkFaint, modifier = Modifier.size(22.dp))
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    candidate.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Vetro.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (candidate.explicit == true) ExplicitBadge()
            }
            Text(
                candidate.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = durationTint(expectedDurationSeconds, candidate.durationSeconds),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onDetails, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Info, "Dettagli", tint = Vetro.InkFaint, modifier = Modifier.size(20.dp))
        }

        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (selected) Vetro.Accent else Vetro.InkFaint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Quanto la durata di [candidate] si avvicina a quella dichiarata.
 *
 * È lo stesso ragionamento di [com.sosound.app.data.importing.TrackMatcher.durationAdjustment],
 * ma qui serve a farlo *vedere*: un colore che dice a colpo d'occhio se
 * questa proposta dura quanto ci si aspettava o no, senza dover fare la
 * sottrazione a mente fra due numeri scritti in un mm:ss.
 */
private fun durationTint(expected: Int?, found: Int?): Color {
    if (expected == null || found == null) return Vetro.InkSoft
    val scarto = kotlin.math.abs(expected - found)
    return when {
        scarto <= 5 -> Vetro.Accent
        scarto <= 20 -> Vetro.InkSoft
        else -> Vetro.Danger
    }
}

/** I dettagli di una proposta: quello che una riga stretta non ha spazio
 *  per dire, con la copertina grande per riconoscerla a colpo d'occhio. */
@Composable
private fun CandidateDetailDialog(
    candidate: CatalogTrack,
    expectedDurationSeconds: Int?,
    onDismiss: () -> Unit,
    onPick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vetro.Ground,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    candidate.title, color = Vetro.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (candidate.explicit == true) ExplicitBadge()
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(Vetro.Glass),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (candidate.thumbnail != null) {
                            AsyncImage(
                                model = candidate.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(Icons.Default.MusicNote, null, tint = Vetro.InkFaint, modifier = Modifier.size(40.dp))
                        }
                    }
                }
                DettaglioCandidato("Artista", candidate.artist)
                candidate.album?.let { DettaglioCandidato("Album", it) }
                candidate.durationText?.let { trovata ->
                    val confronto = expectedDurationSeconds?.let { atteso ->
                        candidate.durationSeconds?.let { s ->
                            val scarto = kotlin.math.abs(atteso - s)
                            when {
                                scarto <= 2 -> " (uguale a quella richiesta)"
                                scarto <= 20 -> " (${scarto}s di differenza)"
                                else -> " (${scarto}s di differenza — probabilmente un'altra versione)"
                            }
                        }
                    }.orEmpty()
                    DettaglioCandidato("Durata", "$trovata$confronto")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPick) { Text("Scegli questo", color = Vetro.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi", color = Vetro.InkSoft) }
        },
    )
}

@Composable
private fun DettaglioCandidato(etichetta: String, valore: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            etichetta,
            style = MaterialTheme.typography.bodySmall,
            color = Vetro.InkFaint,
            modifier = Modifier.width(90.dp),
        )
        Text(
            valore,
            style = MaterialTheme.typography.bodySmall,
            color = Vetro.InkSoft,
        )
    }
}
