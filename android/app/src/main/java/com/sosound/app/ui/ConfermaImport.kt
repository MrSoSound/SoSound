package com.sosound.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sosound.app.ui.theme.Vetro

/**
 * Cosa contiene la cartella scelta, e cosa farne.
 *
 * Compare solo se la cartella contiene gia' un backup. Le due strade
 * sono entrambe legittime: unire, oppure usarla come destinazione
 * lasciando dov'e' il backup che c'era. Non si sceglie per l'utente,
 * perche' nessuna delle due e' ovvia.
 */
@UnstableApi
@Composable
fun ConfermaImport(
    vm: MainViewModel,
    anteprima: com.sosound.app.data.storage.ImportPreview,
    uri: android.net.Uri?,
) {
    val doppi = vm.duplicatiConAnteprima()
    val miei by vm.library.collectAsState()

    AlertDialog(
        onDismissRequest = { vm.clearAnteprima() },
        containerColor = Vetro.Ground,
        title = { Text("Questa cartella ha già una libreria", color = Vetro.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (anteprima.daIndice) {
                        "Dentro ci sono ${anteprima.validi.size} brani e " +
                            "${anteprima.index.playlist.size} playlist."
                    } else {
                        // Senza indice si dice cosa manca invece di
                        // lasciarlo scoprire dopo: le playlist non
                        // tornano, e non e' un guasto da cercare.
                        "L'indice non c'è, ma i file sì: ho riconosciuto " +
                            "${anteprima.validi.size} brani dai nomi e li " +
                            "rimetto a posto con le loro copertine.\n\n" +
                            "Le playlist stavano solo nell'indice e non " +
                            "si possono recuperare."
                    },
                    color = Vetro.Ink,
                )

                if (miei.isNotEmpty()) {
                    Text(
                        "Tu ne hai ${miei.size}. Unendo le due, si tengono tutti: " +
                            if (doppi > 0)
                                "i $doppi in comune non si duplicano, restano uno."
                            else "non ce ne sono in comune.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkSoft,
                    )
                    Text(
                        "I tuoi brani restano dove sono adesso. Per portarli anche " +
                            "in questa cartella, dopo usa «Sposta anche quelli».",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }

                if (anteprima.nonVerificato) {
                    Text(
                        "L'indice non supera il controllo di integrità: quasi " +
                            "sempre vuol dire che è stato scritto da una versione " +
                            "precedente. Viene usato lo stesso — brani e playlist " +
                            "sono lì e si leggono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }

                if (anteprima.sospetti.isNotEmpty()) {
                    // Entrano lo stesso: va detto, non nascosto.
                    Text(
                        "${anteprima.sospetti.size} hanno un'impronta diversa da " +
                            "quella scritta nell'indice, ma la stessa dimensione: " +
                            "vengono importati. Succede con gli indici scritti da " +
                            "versioni precedenti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }

                if (anteprima.mancanti.isNotEmpty() || anteprima.corrotti.isNotEmpty()) {
                    val guasti = buildList {
                        if (anteprima.mancanti.isNotEmpty())
                            add("${anteprima.mancanti.size} senza file")
                        if (anteprima.corrotti.isNotEmpty())
                            add("${anteprima.corrotti.size} di dimensione diversa")
                    }
                    Text(
                        "Saltati: ${guasti.joinToString(", ")}. " +
                            "Di solito è una copia interrotta a metà.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.Danger,
                    )
                }

                var dettagli by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { dettagli = !dettagli; if (dettagli) vm.preparaEsame() },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        if (dettagli) "Nascondi i dettagli" else "Cosa c'è dentro, nel dettaglio",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.Accent,
                    )
                }
                if (dettagli) {
                    val referto by vm.esame.collectAsState()
                    Text(
                        referto ?: "Sto guardando…",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }

                if (anteprima.validi.isEmpty()) {
                    // Il vicolo cieco va spiegato. Un pannello con il
                    // tasto spento e nessuna ragione e' il modo in cui
                    // un'app sembra rotta.
                    Text(
                        if (anteprima.totale == 0)
                            "Non ho trovato nessun brano: dentro SoSound/Brani " +
                                "non c'è niente che io riconosca."
                        else
                            "Nessuno dei ${anteprima.totale} brani elencati è " +
                                "utilizzabile: i file non ci sono o non " +
                                "corrispondono. Puoi comunque usare la cartella " +
                                "come destinazione.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.Danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { uri?.let { vm.confirmFolderImport(it) } },
                enabled = anteprima.validi.isNotEmpty(),
            ) { Text("Unisci", color = Vetro.Accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.useFolderOnly() }) {
                    Text("Solo destinazione", color = Vetro.InkSoft)
                }
                TextButton(onClick = { vm.clearAnteprima() }) {
                    Text("Annulla", color = Vetro.InkFaint)
                }
            }
        },
    )
}
