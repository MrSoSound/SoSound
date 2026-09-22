package com.sosound.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sosound.app.data.update.UpdateState
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass

@UnstableApi
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val tree by vm.storageTree.collectAsState()
    val fuoriPosto by vm.misplaced.collectAsState()
    val spostamento by vm.moving.collectAsState()

    // Il selettore di cartelle e' di sistema: torna un permesso duraturo
    // sull'albero scelto, che sopravvive al riavvio del telefono.
    val scegliCartella = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.chooseFolder(it) } }

    val anteprima by vm.anteprimaImport.collectAsState()
    val messaggioBackup by vm.backupMessage.collectAsState()
    val importando by vm.importingBackup.collectAsState()

    val cartellaInAttesa by vm.cartellaInAttesa.collectAsState()

    val bytes by vm.usedBytes.collectAsState()
    val tracks by vm.library.collectAsState()
    val updating by vm.updating.collectAsState()
    val message by vm.engineMessage.collectAsState()
    val ready by vm.engineReady.collectAsState()
    val appUpdate by vm.appUpdateState.collectAsState()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Il primo cartello dentro le impostazioni.
        //
        // L'icona e il nome distinguono le due app dal launcher; questo
        // serve per l'attimo in cui si e' gia' dentro e i due lettori si
        // somigliano — la schermata di ascolto e' identica pixel per
        // pixel, ed e' li' che sbagliare app costa di piu' (importare in
        // quella sbagliata, scrivere note nella cartella sbagliata).
        if (com.sosound.app.BuildConfig.APPLICATION_ID.endsWith(".dev")) {
            Row(
                Modifier.fillMaxWidth().glass().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Construction, null, tint = Vetro.Danger, modifier = Modifier.size(18.dp))
                Text(
                    "Canale di sviluppo — non è la versione distribuita",
                    style = MaterialTheme.typography.labelMedium,
                    color = Vetro.Danger,
                )
            }
        }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dove finiscono i file", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)
                Text(
                    vm.storageLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Vetro.Ink,
                )
                Text(
                    if (tree == null)
                        "I file stanno nello spazio privato dell'app: nessun altro " +
                            "programma li vede, e disinstallando SoSound spariscono."
                    else
                        "I file restano anche se disinstalli l'app, e si vedono " +
                            "dagli altri lettori.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scegliCartella.launch(null) },
                        enabled = spostamento == null,
                        modifier = Modifier.weight(1f),
                    ) { Text("Scegli cartella") }

                    if (tree != null) {
                        OutlinedButton(
                            onClick = { vm.useDefaultFolder() },
                            enabled = spostamento == null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Torna a interna") }
                    }
                }

                // Lo stesso interruttore dell'introduzione: chi l'ha
                // saltata lo trova qui, e chi ha cambiato idea pure.
                val sempreOffline by vm.sempreOffline.collectAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.impostaSempreOffline(!sempreOffline) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Tieni tutto anche senza rete",
                            color = Vetro.Ink,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            if (sempreOffline)
                                "Ogni brano messo in una playlist viene scaricato qui."
                            else
                                "I brani si ascoltano al volo e restano finché c'è " +
                                    "posto. Quelli da tenere li scegli tu, dalla " +
                                    "scheda del brano.",
                            color = Vetro.InkFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = sempreOffline,
                        onCheckedChange = { vm.impostaSempreOffline(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Vetro.Ground,
                            checkedTrackColor = Vetro.Accent,
                        ),
                    )
                }

                val fragile by vm.destinazioneFragile.collectAsState()
                if (fragile) {
                    Text(
                        "Nessuna cartella scelta: i brani stanno dentro l'app e " +
                            "spariscono disinstallandola. L'elenco e le playlist " +
                            "no — quelli tornano da soli.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.Danger,
                    )
                }

                if (tree != null) {
                    // Quando l'import non importa, il sintomo e' sempre
                    // lo stesso e le cause sono diverse: questo dice
                    // quale delle due.
                    val inEsame by vm.inEsame.collectAsState()
                    TextButton(
                        onClick = { vm.esaminaCartella() },
                        enabled = !inEsame,
                    ) {
                        // Non e' un passaggio del percorso normale: li'
                        // i dettagli stanno dentro il pannello che
                        // compare da solo. Questo serve quando il
                        // pannello NON compare, ed e' l'unico caso in
                        // cui c'e' qualcosa da capire.
                        Text(
                            if (inEsame) "Sto guardando…"
                            else "Non riconosce la cartella? Guarda cosa vede",
                            color = Vetro.InkFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // I brani gia' scaricati non si spostano da soli: sarebbe
                // un travaso di centinaia di megabyte deciso da noi.
                if (fuoriPosto > 0 && spostamento == null) {
                    HorizontalDivider()
                    Text(
                        if (fuoriPosto == 1) "1 brano sta ancora nella posizione precedente."
                        else "$fuoriPosto brani stanno ancora nella posizione precedente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                    OutlinedButton(
                        onClick = { vm.moveExisting() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sposta anche quelli") }
                }

                val copertineRotte by vm.brokenCovers.collectAsState()
                if (copertineRotte > 0 && spostamento == null) {
                    HorizontalDivider()
                    Text(
                        if (copertineRotte == 1) "1 brano ha perso la copertina."
                        else "$copertineRotte brani hanno perso la copertina.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                    OutlinedButton(
                        onClick = { vm.repairCovers() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Recupera le copertine") }
                }

                spostamento?.let {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Vetro.InkSoft)
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup e ripristino", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)
                Text(
                    if (tree == null)
                        "Scegli una cartella qui sopra: da quel momento SoSound ci " +
                            "tiene dentro i brani, le copertine e l'elenco delle playlist. " +
                            "Reinstallando l'app basta riselezionarla per riavere tutto."
                    else
                        "Dentro la cartella c'è «SoSound», con i brani divisi per artista, " +
                            "le copertine e un indice che tiene album e playlist. " +
                            "Si riscrive da sé quando la libreria cambia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )

                messaggioBackup?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Vetro.InkSoft)
                }
            }
        }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sul telefono", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)
                Text(
                    "%d brani · %.1f MB".format(tracks.size, bytes / 1024.0 / 1024.0),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Vetro.Ink,
                )
                Text(
                    "Spazio occupato dai brani scaricati.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
        }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Motore di scaricamento", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!ready) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        if (ready) "yt-dlp ${vm.engineVersion() ?: "pronto"}"
                        else "in preparazione…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HorizontalDivider()

                Text(
                    "Se i download cominciano a fallire, quasi sempre è perché " +
                        "YouTube ha cambiato qualcosa e yt-dlp è rimasto indietro. " +
                        "Questo tasto scarica la versione aggiornata senza " +
                        "reinstallare l'app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { vm.updateEngine() },
                    enabled = !updating && ready,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (updating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Aggiorna yt-dlp")
                    }
                }

                message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Aggiornamenti dell'app", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)

                Text(
                    "Hai la versione ${vm.appCurrentVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                HorizontalDivider()

                if (!vm.appUpdateDisponibile) {
                    // Non e' un tasto spento: e' detto perche'.
                    //
                    // Il controllo guarda le release della versione
                    // distribuita, firmata con un'altra chiave. Offrirlo
                    // qui vorrebbe dire proporre di installare un'app
                    // diversa spacciandola per un aggiornamento.
                    Text(
                        "Non disponibile sul canale di sviluppo: le " +
                            "release che questo controllo guarda sono " +
                            "quelle della versione distribuita, firmata " +
                            "con un'altra chiave.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                } else when (val s = appUpdate) {
                    is UpdateState.Idle -> {
                        Button(onClick = { vm.checkForAppUpdate() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Controlla aggiornamenti")
                        }
                    }

                    is UpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Controllo…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    is UpdateState.Available -> {
                        Text(
                            "È disponibile la versione ${s.release.tagName}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        s.release.body?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Vetro.InkFaint,
                                maxLines = 4,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.skipAppUpdate() }) { Text("Salta") }
                            Button(
                                onClick = { vm.downloadAndInstallAppUpdate() },
                                modifier = Modifier.weight(1f),
                            ) { Text("Scarica e installa") }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            "Scarico la ${s.release.tagName}… ${(s.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is UpdateState.Installing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Apro l'installazione…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    is UpdateState.NeedsInstallPermission -> {
                        Text(
                            "Android chiede di autorizzare SoSound a installare " +
                                "aggiornamenti da sé: concedilo, poi torna qui.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Vetro.InkFaint,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.openInstallPermissionSettings() }) {
                                Text("Apri impostazioni")
                            }
                            Button(onClick = { vm.retryAppInstall() }, modifier = Modifier.weight(1f)) {
                                Text("Ho concesso, installa")
                            }
                        }
                    }

                    is UpdateState.Error -> {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Vetro.Danger,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.dismissAppUpdateError() }) { Text("Chiudi") }
                            Button(onClick = { vm.checkForAppUpdate() }) { Text("Riprova") }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Come funziona", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)
                Text(
                    "Tutto avviene sul telefono: la ricerca interroga il catalogo " +
                        "di YouTube Music, il download lo fa yt-dlp che gira dentro " +
                        "l'app, e i file restano qui. Non c'è nessun server di mezzo " +
                        "e niente esce dal dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
        }

        // ----------------------------------------------- le notifiche
        val sentiNotifiche by vm.sentiNotifiche.collectAsState()
        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.impostaSentiNotifiche(!sentiNotifiche) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Mentre ascolti la musica vuoi sentire le notifiche?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Vetro.Ink,
                        )
                        Text(
                            if (sentiNotifiche)
                                "La musica cala per un istante e il suono della " +
                                    "notifica passa sopra. Poi torna com'era."
                            else
                                "La musica non cambia. La notifica arriva sullo " +
                                    "schermo, ma il suo suono resta sotto la canzone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Vetro.InkFaint,
                        )
                    }
                    Switch(
                        checked = sentiNotifiche,
                        onCheckedChange = { vm.impostaSentiNotifiche(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Vetro.Ground,
                            checkedTrackColor = Vetro.Accent,
                        ),
                    )
                }
                Text(
                    "Le telefonate mettono in pausa comunque, in tutti e due i casi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )

                // Perche' a volte non succede niente, e da fuori non si
                // capisce se e' rotto o se non c'era niente da fare.
                val diagnosi by vm.diagnosiAudio.collectAsState()
                TextButton(onClick = { vm.controllaAudio() }) {
                    Text(
                        "Non senti la differenza? Guarda cosa vede l'app",
                        color = Vetro.InkFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                diagnosi?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkSoft,
                    )
                }
            }
        }

        // --------------------------------------------------- la cache
        val cacheByte by vm.cacheByte.collectAsState()
        LaunchedEffect(Unit) { vm.misuraCache() }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Quello che hai ascoltato",
                    style = MaterialTheme.typography.titleMedium,
                    color = Vetro.Ink,
                )
                Text(
                    "I brani che senti senza scaricarli restano qui: la seconda " +
                        "volta partono subito e senza rete. Quando lo spazio " +
                        "finisce se ne va quello che non senti da più tempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // La misura accanto al tasto, non dopo: un tasto che
                    // cancella senza dire quanto libera non lo preme
                    // nessuno.
                    Text(
                        if (cacheByte <= 0) "Niente in cache"
                        else "Occupa ${"%.1f".format(cacheByte / 1024.0 / 1024.0)} MB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Vetro.InkSoft,
                    )
                    OutlinedButton(
                        onClick = { vm.svuotaCache() },
                        enabled = cacheByte > 0,
                    ) { Text("Svuota") }
                }
                Text(
                    "I brani tenuti «anche senza rete» non si toccano: " +
                        "hanno un file loro nella cartella.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
        }

        // Le cartelle annidate: si segnalano solo se ci sono.
        val annidate by vm.annidate.collectAsState()
        val unendo by vm.unendo.collectAsState()
        LaunchedEffect(tree) { vm.controllaAnnidate() }
        if (annidate > 0) {
            Box(Modifier.fillMaxWidth().glass()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "C'è una cartella SoSound dentro l'altra",
                        style = MaterialTheme.typography.titleMedium,
                        color = Vetro.Danger,
                    )
                    Text(
                        "Dentro ci sono $annidate file. Succedeva scegliendo la " +
                            "propria cartella SoSound come destinazione: l'app ne " +
                            "creava un'altra dentro, e la musica restava divisa fra " +
                            "le due. Adesso non succede più.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                    Button(
                        onClick = { vm.unisciAnnidate() },
                        enabled = unendo == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(unendo ?: "Portali nella cartella principale") }
                    Text(
                        "I doppioni non si cancellano: quello che arriva viene " +
                            "rinominato. Un doppione si vede e si toglie a mano, " +
                            "un brano sparito no.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkFaint,
                    )
                }
            }
        }

        val guardando by vm.guardandoCartella.collectAsState()
        if (guardando) Attesa("Guardo cosa c'è nella cartella…")

        val progresso by vm.progressoImport.collectAsState()
        if (progresso != null) Attesa(progresso!!)

        anteprima?.let { ConfermaImport(vm, it, cartellaInAttesa) }

        val referto by vm.esame.collectAsState()
        referto?.let { testo ->
            AlertDialog(
                onDismissRequest = { vm.chiudiEsame() },
                containerColor = Vetro.Ground,
                title = { Text("Cosa vedo nella cartella", color = Vetro.Ink) },
                text = {
                    // Monospaziato e scorrevole: e' un referto, si legge
                    // riga per riga e spesso si manda a qualcuno.
                    Text(
                        testo,
                        color = Vetro.InkSoft,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.chiudiEsame() }) {
                        Text("Chiudi", color = Vetro.Accent)
                    }
                },
            )
        }

        Box(Modifier.fillMaxWidth().glass()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Il consiglio", style = MaterialTheme.typography.titleMedium, color = Vetro.Ink)
                Text(
                    "Scegli una cartella su Google Drive. Da lì il backup vive fuori " +
                        "dal telefono: se lo perdi, lo rompi o lo cambi, reinstalli " +
                        "SoSound, riselezioni quella cartella e ritrovi tutto — brani, " +
                        "album e playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
                Text(
                    "Su una cartella in cloud la scrittura passa dall'app di Drive, " +
                        "quindi serve connessione e i download sono un po' più lenti. " +
                        "Se preferisci la velocità, tieni una cartella del telefono e " +
                        "falla sincronizzare da Autosync, FolderSync o Syncthing: il " +
                        "risultato è lo stesso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Vetro.InkFaint,
                )
            }
        }
    }
}


