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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
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
                    "Spotify, oppure un elenco con una riga per brano: «Artista - " +
                    "Titolo», oppure «Titolo by Artista». La numerazione iniziale " +
                    "non dà fastidio.\n\n" +
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

@UnstableApi
@Composable
private fun Revisione(vm: MainViewModel, s: ImportState.Review) {
    var rinomina by remember { mutableStateOf(false) }

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

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(s.entries) { index, e ->
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

                        // Le alternative si mostrano solo dove servono:
                        // su una riga sicura sarebbero rumore.
                        if (e.confidence == Confidence.INCERTO) {
                            e.alternatives.take(2).forEach { alt ->
                                Text(
                                    "· ${alt.artist} — ${alt.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Vetro.InkSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clickable { vm.pickAlternative(index, alt) }
                                        .padding(top = 3.dp),
                                )
                            }
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
}
