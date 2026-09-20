package com.sosound.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sosound.app.R
import com.sosound.app.ui.theme.Vetro
import com.sosound.app.ui.theme.glass
import kotlinx.coroutines.launch

/**
 * L'introduzione al primo avvio.
 *
 * Quattro schermate e nient'altro. La cartella viene prima di tutto
 * perche' e' l'unico momento in cui sceglierla non costa niente: la
 * libreria e' vuota, quindi non c'e' niente da spostare ne' da fondere.
 * Chiederlo dopo, con duecento brani gia' scaricati, vorrebbe dire
 * proporre un travaso.
 *
 * Si salta in qualunque momento, e non si rivede.
 */
@UnstableApi
@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    val cartella by vm.storageTree.collectAsState()
    val accent by vm.accent.collectAsState()

    val ultimo = 3
    // HorizontalPager invece di un contatore: si scorre col dito, che e'
    // il gesto che tutti provano per primo su una sequenza di schermate.
    val pager = rememberPagerState(pageCount = { ultimo + 1 })
    val scope = rememberCoroutineScope()
    val passo = pager.currentPage

    // Il pannello di conferma va disegnato anche qui: l'introduzione
    // copre tutto e torna prima del resto dell'interfaccia, quindi
    // quello delle impostazioni non arriva mai sullo schermo. Senza,
    // scegliere una cartella che contiene gia' della musica sembrava
    // non fare niente.
    val messaggioCartella by vm.backupMessage.collectAsState()
    val sempreOffline by vm.sempreOffline.collectAsState()
    val anteprima by vm.anteprimaImport.collectAsState()
    val cartellaInAttesa by vm.cartellaInAttesa.collectAsState()
    val guardando by vm.guardandoCartella.collectAsState()
    if (guardando) Attesa("Guardo cosa c'è nella cartella…")

    val progresso by vm.progressoImport.collectAsState()
    if (progresso != null) Attesa(progresso!!)

    anteprima?.let { ConfermaImport(vm, it, cartellaInAttesa) }

    val scegliCartella = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.chooseFolder(it) } }

    Box(Modifier.fillMaxSize().background(Vetro.Ground)) {
        VetroBackground()

        Column(
            Modifier
                .fillMaxSize()
                // Le barre di sistema: l'introduzione disegna a tutto
                // schermo, e senza questo i tasti finiscono sotto la
                // barra di navigazione — si vedono ma per premerli si
                // colpisce il tasto «indietro» del telefono.
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp)
                // Un po' d'aria sotto: appoggiare i tasti esattamente
                // sul bordo della barra e' scomodo anche quando e'
                // formalmente corretto.
                .padding(bottom = 12.dp)
        ) {

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                // Sempre saltabile: un'introduzione che non si puo'
                // chiudere e' un ostacolo, non un aiuto.
                TextButton(onClick = { vm.completeOnboarding(); onDone() }) {
                    Text(
                        if (passo == ultimo) "Chiudi" else "Salta",
                        color = Vetro.InkFaint,
                    )
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { p ->
                when (p) {
                    0 -> Benvenuto()
                    1 -> Cartella(
                        scelta = cartella != null,
                        nome = vm.storageLabel(),
                        messaggio = messaggioCartella,
                        sempreOffline = sempreOffline,
                        onSempreOffline = { vm.impostaSempreOffline(it) },
                        onScegli = { scegliCartella.launch(null) },
                    )
                    2 -> ComeFunziona(accent.color)
                    else -> Playlist()
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(ultimo + 1) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == passo) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == passo) accent.color else Vetro.GlassStrong)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (passo > 0) {
                        OutlinedButton(
                            onClick = { scope.launch { pager.animateScrollToPage(passo - 1) } },
                            modifier = Modifier.weight(1f),
                        ) { Text("Indietro") }
                    }
                    Button(
                        onClick = {
                            if (passo == ultimo) {
                                vm.completeOnboarding()
                                vm.openImportFromOnboarding()
                                onDone()
                            } else {
                                scope.launch { pager.animateScrollToPage(passo + 1) }
                            }
                        },
                        modifier = Modifier.weight(if (passo > 0) 1f else 2f),
                    ) {
                        Text(if (passo == ultimo) "Importa una playlist" else "Avanti")
                    }
                }

                // Chi non ha playlist da importare non deve trovarsi
                // davanti a un unico tasto che gli chiede di farlo: la
                // seconda strada lo porta dove comincerebbe comunque.
                if (passo == ultimo) {
                    TextButton(
                        onClick = {
                            vm.completeOnboarding()
                            vm.goToSearchTab()
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Non ora, cerca musica", color = Vetro.InkSoft)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- schermate

@Composable
private fun Benvenuto() {
    Colonna {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(132.dp),
        )
        Titolone("SoSound")
        Corpo(
            "La tua musica, scaricata sul telefono e tua per sempre.\n\n" +
                "Niente abbonamento, niente account, niente server: " +
                "tutto avviene qui dentro."
        )
    }
}

@Composable
private fun Cartella(
    scelta: Boolean,
    nome: String,
    messaggio: String?,
    sempreOffline: Boolean,
    onSempreOffline: (Boolean) -> Unit,
    onScegli: () -> Unit,
) {
    Colonna {
        Cerchio(Icons.Default.Folder)
        // Una domanda, non un titolo: a una domanda si risponde, e la
        // risposta e' il tasto che sta qui sotto.
        Titolone("Dove vuoi tenere la tua musica?")
        Corpo(
            "Scegli una cartella e i brani sono tuoi davvero: restano " +
                "anche se cambi telefono o reinstalli l'app. Su Google " +
                "Drive vivono pure fuori dal telefono."
        )

        Spacer(Modifier.height(18.dp))

        messaggio?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (scelta) {
            Row(
                Modifier.fillMaxWidth().glass().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Default.Check, null, tint = Vetro.Accent, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(nome, color = Vetro.Ink, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "I brani salvati finiscono qui.",
                        color = Vetro.InkFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            OutlinedButton(onClick = onScegli, modifier = Modifier.fillMaxWidth()) {
                Text("Scegli una cartella")
            }
            Spacer(Modifier.height(10.dp))
            // La conseguenza di NON scegliere, detta per intero e prima
            // di sceglierla. Scoprirla dopo aver perso una libreria e'
            // il momento sbagliato.
            Text(
                "Se non scegli niente, i brani restano dentro l'app: " +
                    "funzionano, ma spariscono disinstallandola. " +
                    "L'elenco di cosa avevi e le playlist invece li " +
                    "rimettiamo a posto da soli, se riinstalli sullo " +
                    "stesso telefono.",
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkFaint,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Hai già una cartella SoSound? Scegli quella: brani e " +
                "playlist tornano al loro posto adesso.",
            style = MaterialTheme.typography.bodySmall,
            color = Vetro.InkFaint,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))

        // La seconda domanda, che dipende dalla prima.
        Row(
            Modifier
                .fillMaxWidth()
                .glass()
                .clickable { onSempreOffline(!sempreOffline) }
                .padding(14.dp),
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
                        "Ogni brano che metti in una playlist viene scaricato."
                    else
                        "I brani si ascoltano al volo e restano finché c'è " +
                            "posto. Quelli che vuoi tenere li scegli tu.",
                    color = Vetro.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = sempreOffline,
                onCheckedChange = onSempreOffline,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Vetro.Ground,
                    checkedTrackColor = Vetro.Accent,
                ),
            )
        }
    }
}

@Composable
private fun ComeFunziona(accent: androidx.compose.ui.graphics.Color) {
    Colonna {
        Titolone("Come funziona")
        Spacer(Modifier.height(4.dp))

        Passo(
            Icons.Default.Search,
            "Cerca e tocca",
            "Scrivi una canzone e toccala. Parte. Se non ce l'hai, " +
                "viene scaricata prima — non devi fare altro.",
            accent,
        )
        Passo(
            Icons.Default.PlayArrow,
            "Suona anche a schermo spento",
            "Con gli auricolari, in tasca, senza rete. I file sono sul " +
                "telefono, quindi funziona ovunque.",
            accent,
        )
        Passo(
            Icons.Default.MoreVert,
            "I tre puntini aprono tutto",
            "Su ogni brano: il titolo per intero, l'artista, l'album, e " +
                "da lì lo aggiungi a una playlist o alla coda.",
            accent,
        )
    }
}

@Composable
private fun Playlist() {
    Colonna {
        Cerchio(Icons.AutoMirrored.Filled.PlaylistAdd)
        Titolone("Porta le tue playlist")
        Corpo(
            "Hai una playlist su Spotify? Copia il link di condivisione e " +
                "incollalo: SoSound cerca i brani uno per uno e te li mostra " +
                "prima di scaricarli.\n\n" +
                "Nessun account da collegare. Funziona anche con un file " +
                "esportato, o con un elenco scritto a mano."
        )
    }
}

// -------------------------------------------------------------- pezzetti

@Composable
private fun Colonna(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun Cerchio(icona: ImageVector) {
    Box(
        Modifier.size(84.dp).clip(CircleShape).background(Vetro.GlassStrong),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icona, null, tint = Vetro.Accent, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun Titolone(testo: String) {
    Text(
        testo,
        style = MaterialTheme.typography.headlineMedium,
        color = Vetro.Ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 22.dp, bottom = 12.dp),
    )
}

@Composable
private fun Corpo(testo: String) {
    Text(
        testo,
        style = MaterialTheme.typography.bodyMedium,
        color = Vetro.InkSoft,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Passo(
    icona: ImageVector,
    titolo: String,
    testo: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Vetro.Glass),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icona, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(titolo, style = MaterialTheme.typography.titleSmall, color = Vetro.Ink)
            Text(
                testo,
                style = MaterialTheme.typography.bodySmall,
                color = Vetro.InkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
    }
}
