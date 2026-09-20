package com.sosound.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Cast
import com.sosound.app.cast.StatoCast
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sosound.app.ui.theme.Vetro

/**
 * Gli apparecchi su cui si puo' suonare, oltre a quelli Cast.
 *
 * Cast ha il suo selettore, fornito dal SDK. Questo e' per il resto:
 * TV Samsung e LG, ricevitori, impianti — tutto quello che parla DLNA e
 * che il selettore di Google non mostra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun DeviceSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val trovati by vm.dlnaTrovati.collectAsState()
    val inCerca by vm.dlnaInCerca.collectAsState()
    val attivo by vm.dlnaAttivo.collectAsState()
    val suCast by vm.castDispositivo.collectAsState()
    val accent by vm.accent.collectAsState()

    LaunchedEffect(Unit) { vm.cercaDispositivi() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vetro.Ground,
        contentColor = Vetro.Ink,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dove suonare", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)
                    Text(
                        if (inCerca) "cerco sulla rete…"
                        else if (trovati.isEmpty()) "nessun apparecchio trovato"
                        else "${trovati.size} trovati",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }
                if (inCerca) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { vm.cercaDispositivi() }) {
                        Icon(Icons.Default.Refresh, "Cerca di nuovo", tint = Vetro.InkSoft)
                    }
                }
            }

            Riga(
                icona = Icons.Default.PhoneAndroid,
                titolo = "Questo telefono",
                sotto = null,
                attivo = attivo == null,
                tinta = accent.color,
            ) { vm.smettiDiTrasmettere(); onDismiss() }

            // Cast, con il suo selettore.
            //
            // Sceglierlo resta compito del SDK: il suo pannello sa fare
            // cose che rifatte a mano si comporterebbero in modo
            // leggermente diverso da quello che la gente ha imparato su
            // ogni altra app. Qui dentro e' una riga come le altre, e il
            // tasto vero e' quello di Google.
            val statoCast by vm.castStato.collectAsState()
            if (statoCast != StatoCast.NON_DISPONIBILE) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Cast, null,
                        tint = if (suCast != null) accent.color else Vetro.InkSoft,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(
                            suCast ?: "Chromecast e Google TV",
                            color = if (suCast != null) accent.color else Vetro.Ink,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            when {
                                suCast != null -> "in riproduzione — tocca per cambiare"
                                statoCast == StatoCast.NESSUN_DISPOSITIVO ->
                                    "nessuno acceso sulla rete"
                                else -> "tocca per scegliere"
                            },
                            color = Vetro.InkFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    CastButton(vm, tinta = if (suCast != null) accent.color else Vetro.InkSoft)
                }
            }

            trovati.forEach { d ->
                Riga(
                    icona = Icons.Default.Tv,
                    titolo = d.nome,
                    sotto = d.costruttore,
                    attivo = attivo == d.nome,
                    tinta = accent.color,
                ) { vm.trasmettiA(d); onDismiss() }
            }

            if (!inCerca && trovati.isEmpty()) {
                Text(
                    "Gli apparecchi devono essere accesi e sulla stessa rete Wi-Fi " +
                        "del telefono. Le TV con Chromecast compaiono nella riga " +
                        "qui sopra, non in questo elenco.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Riga(
    icona: androidx.compose.ui.graphics.vector.ImageVector,
    titolo: String,
    sotto: String?,
    attivo: Boolean,
    tinta: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icona, null, tint = if (attivo) tinta else Vetro.InkFaint, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titolo,
                style = MaterialTheme.typography.bodyMedium,
                color = if (attivo) tinta else Vetro.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sotto?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Vetro.InkFaint)
            }
        }
    }
}
