package com.sosound.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.sosound.app.data.catalog.SearchKind
import com.sosound.app.data.update.showsBadge
import kotlinx.coroutines.launch
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import com.sosound.app.ui.theme.glass
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.sosound.app.ui.LibraryScreen
import com.sosound.app.ui.MainViewModel
import com.sosound.app.ui.NowPlayingScreen
import com.sosound.app.ui.OnboardingScreen
import com.sosound.app.ui.PlayerBar
import com.sosound.app.ui.SearchScreen
import com.sosound.app.ui.SettingsScreen
import com.sosound.app.ui.QueueSheet
import com.sosound.app.ui.Segmenti
import com.sosound.app.ui.BarraRicerca
import com.sosound.app.ui.Vista
import com.sosound.app.ui.TrackSheet
import com.sosound.app.ui.VetroBackground
import com.sosound.app.ui.theme.SoSoundTheme
import com.sosound.app.ui.theme.Vetro

/**
 * Le pagine dell'app, in fila.
 *
 * Sono un nastro solo e non tre schede separate: cosi' il trascinamento
 * laterale non si ferma al bordo di una scheda ma prosegue nella
 * successiva, che e' quello che il gesto promette. La barra in fondo
 * resta e fa due mestieri — dice dove si e' e permette di saltare — ma
 * non e' piu' l'unico modo di spostarsi.
 */
private enum class Pagina(val gruppo: Tab, val etichetta: String) {
    LIB_BRANI(Tab.LIBRERIA, "Brani"),
    LIB_ALBUM(Tab.LIBRERIA, "Album"),
    LIB_PLAYLIST(Tab.LIBRERIA, "Playlist"),
    CERCA_BRANI(Tab.CERCA, "Brani"),
    CERCA_ALBUM(Tab.CERCA, "Album"),
    CERCA_ARTISTI(Tab.CERCA, "Artisti"),
    CERCA_PODCAST(Tab.CERCA, "Podcast"),
    IMPOSTAZIONI(Tab.IMPOSTAZIONI, "Impostazioni");

    /** Il tipo di ricerca che questa pagina mostra. */
    fun tipoCercato(): SearchKind = when (this) {
        CERCA_ALBUM -> SearchKind.ALBUM
        CERCA_ARTISTI -> SearchKind.ARTISTI
        CERCA_PODCAST -> SearchKind.PODCAST
        else -> SearchKind.BRANI
    }

    /** La vista di libreria che questa pagina mostra. */
    fun vista(): Vista = when (this) {
        LIB_ALBUM -> Vista.ALBUM
        LIB_PLAYLIST -> Vista.PLAYLIST
        else -> Vista.BRANI
    }

    companion object {
        /** La prima pagina di una scheda: dove si arriva toccandola. */
        fun primaDi(t: Tab) = entries.first { it.gruppo == t }
    }
}

private fun paginaDi(v: Vista) = when (v) {
    Vista.BRANI -> Pagina.LIB_BRANI
    Vista.ALBUM -> Pagina.LIB_ALBUM
    Vista.PLAYLIST -> Pagina.LIB_PLAYLIST
}

private enum class Tab(val label: String) {
    LIBRERIA("Libreria"), CERCA("Cerca"), IMPOSTAZIONI("Impostazioni")
}

@UnstableApi
/**
 * Estende AppCompatActivity e non ComponentActivity per il tasto di
 * trasmissione: il selettore dei dispositivi del Cast SDK e' un
 * DialogFragment, e per aprirsi cerca un FragmentManager risalendo dal
 * contesto. Su una ComponentActivity non lo trova, non apre niente e
 * non dice niente — il tasto sembra rotto e non c'e' nessun errore da
 * cercare.
 */
class MainActivity : AppCompatActivity() {

    private val askNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* se la nega, la musica suona lo stesso: solo senza controlli in notifica */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Da bordo a bordo, e con le barre di sistema forzate a «scure».
        //
        // enableEdgeToEdge() senza argomenti usa SystemBarStyle.auto, che
        // sceglie il colore delle icone di sistema dal tema del TELEFONO.
        // Su un telefono in tema chiaro il risultato erano icone nere
        // sopra il nostro fondo scuro, e la barra di navigazione bianca.
        // SoSound e' scura sempre, quindi il colore delle icone non e' una
        // cosa da dedurre: lo dichiariamo.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { SoSoundTheme { Root() } }
    }
}

@UnstableApi
@Composable
private fun Root(vm: MainViewModel = viewModel()) {
    val pagine = Pagina.entries
    val nastro = rememberPagerState(initialPage = 0) { pagine.size }
    val scope = rememberCoroutineScope()
    val paginaCorrente = pagine[nastro.currentPage]
    val tab = paginaCorrente.gruppo

    fun vaiA(p: Pagina) = scope.launch { nastro.animateScrollToPage(pagine.indexOf(p)) }

    // Il trascinamento si spegne quando si sta guardando qualcosa dentro
    // una scheda: da li' si esce col tasto indietro, non scivolando via.
    val bloccato by vm.dentroUnaSottoschermata.collectAsState()

    // Le pagine del «Cerca» e i suoi chip sono la stessa scelta: il
    // nastro la scrive, i chip la leggono, e chi tocca un chip fa
    // scorrere il nastro. Senza questo si vedrebbero i due in disaccordo.
    //
    // «settledPage» e non «currentPage»: durante uno scorrimento
    // programmato verso una pagina non adiacente, currentPage cambia
    // anche per le pagine attraversate di passaggio. Se questo effetto
    // reagisse a quelle, riscriverebbe il tipo di ricerca a meta' volo,
    // il che cancellerebbe l'animazione in corso (la key dell'altro
    // effetto, piu' sotto, sarebbe cambiata) — ed e' cosi' che il nastro
    // restava incastrato a meta' fra due schede invece di arrivare a
    // destinazione. settledPage cambia solo quando lo scorrimento si e'
    // fermato, quindi non interrompe se stesso.
    val paginaAssestata = pagine[nastro.settledPage]
    LaunchedEffect(paginaAssestata) {
        when (paginaAssestata) {
            Pagina.CERCA_BRANI -> vm.onKindChange(SearchKind.BRANI)
            Pagina.CERCA_ALBUM -> vm.onKindChange(SearchKind.ALBUM)
            Pagina.CERCA_ARTISTI -> vm.onKindChange(SearchKind.ARTISTI)
            Pagina.CERCA_PODCAST -> vm.onKindChange(SearchKind.PODCAST)
            else -> Unit
        }
    }
    val tipoCercato = vm.search.collectAsState().value.kind
    LaunchedEffect(tipoCercato) {
        if (paginaCorrente.gruppo != Tab.CERCA) return@LaunchedEffect
        val voluta = when (tipoCercato) {
            SearchKind.BRANI -> Pagina.CERCA_BRANI
            SearchKind.ALBUM -> Pagina.CERCA_ALBUM
            SearchKind.ARTISTI -> Pagina.CERCA_ARTISTI
            SearchKind.PODCAST -> Pagina.CERCA_PODCAST
        }
        if (voluta != paginaCorrente) nastro.animateScrollToPage(pagine.indexOf(voluta))
    }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }

    val player by vm.playerState.collectAsState()
    val accent by vm.accent.collectAsState()
    val aggiornamentoStato by vm.appUpdateState.collectAsState()

    // Aprendo un artista dalla scheda di un brano si finisce su una
    // pagina che vive nella scheda «Cerca»: la barra in fondo deve
    // seguire, se no indica un posto dove non siamo.
    val vaiACerca by vm.goToSearch.collectAsState()
    LaunchedEffect(vaiACerca) {
        if (vaiACerca) { vaiA(Pagina.CERCA_BRANI); vm.searchTabShown() }
    }

    val vaiALibreria by vm.goToLibrary.collectAsState()
    LaunchedEffect(vaiALibreria) {
        if (vaiALibreria) { vaiA(Pagina.LIB_BRANI); vm.libraryTabShown() }
    }

    // L'introduzione copre tutto: alla prima apertura non c'e' niente
    // dietro da vedere, e lasciarla trasparire sarebbe solo confusione.
    val introduzione by vm.showOnboarding.collectAsState()
    if (introduzione) {
        OnboardingScreen(vm, onDone = {})
        return
    }

    // La barra di navigazione prende la stessa tinta dei controlli del
    // player: era l'ultimo pezzo rimasto con il viola fisso, e stonava
    // sull'ardesia. La dissolvenza e' la stessa, cosi' cambiando brano
    // tutto l'accento si muove insieme.
    val tint by animateColorAsState(accent.color, tween(600), label = "tintaNav")

    Box(Modifier.fillMaxSize()) {

        // Strato 1: il fondo, sempre lo stesso.
        VetroBackground()

        // Strato 2: l'app.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            // L'intestazione sta QUI, sopra il nastro, e non dentro le
            // pagine.
            //
            // Dentro scorreva insieme al contenuto: cambiando vista si
            // vedevano tre copie dei segmenti passare una dopo l'altra,
            // e il campo di ricerca scivolava via mentre lo si stava
            // guardando. Un'intestazione che si muove con quello che
            // indica non indica piu' niente.
            //
            // Sparisce dentro una sottoschermata — una playlist aperta,
            // un album, un artista — perche' li' quella pagina prende
            // tutto lo schermo e i segmenti parlerebbero di un altrove.
            if (!bloccato) {
                when (tab) {
                    Tab.LIBRERIA -> Segmenti(paginaCorrente.vista()) { v -> vaiA(paginaDi(v)) }
                    Tab.CERCA -> BarraRicerca(vm, paginaCorrente.tipoCercato())
                    Tab.IMPOSTAZIONI -> Unit
                }
            }

            HorizontalPager(
                state = nastro,
                modifier = Modifier.weight(1f),
                userScrollEnabled = !bloccato,
                // Una pagina di margine: basta a far entrare la
                // successiva gia' disegnata invece che a scatto, e non
                // tiene in vita tutte e otto.
                beyondViewportPageCount = 1,
            ) { indice ->
                when (val p = pagine[indice]) {
                    Pagina.LIB_BRANI, Pagina.LIB_ALBUM, Pagina.LIB_PLAYLIST ->
                        LibraryScreen(vm, vista = p.vista())
                    Pagina.IMPOSTAZIONI -> SettingsScreen(vm)
                    else -> SearchScreen(vm, kindPagina = p.tipoCercato())
                }
            }

            // Il guaio, dove si vede da qualunque schermata: sopra la
            // barra del player, che e' l'unica cosa sempre presente.
            val guaio by vm.erroreRiproduzione.collectAsState()
            guaio?.let { testo ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .glass(Vetro.BarShape, strong = true)
                        .clickable { vm.scartaErroreRiproduzione() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.CloudOff, null,
                        tint = Vetro.Danger,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        testo,
                        style = MaterialTheme.typography.bodySmall,
                        color = Vetro.InkSoft,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Chiudi", style = MaterialTheme.typography.labelSmall, color = Vetro.InkFaint)
                }
            }

            PlayerBar(vm, onExpand = { nowPlayingOpen = true })

            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                // Zero, non i margini di serie: il Column qui sopra ha gia'
                // applicato quelli delle barre di sistema, e sommarli
                // lasciava un vuoto sotto le etichette.
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { vaiA(Pagina.primaDi(t)) },
                        icon = {
                            val icona = @Composable {
                                Icon(
                                    when (t) {
                                        Tab.LIBRERIA -> Icons.Default.LibraryMusic
                                        Tab.CERCA -> Icons.Default.Search
                                        Tab.IMPOSTAZIONI -> Icons.Default.Settings
                                    },
                                    contentDescription = t.label,
                                )
                            }
                            // Il pallino dice «c'e' un aggiornamento» solo
                            // sulla scheda dove si scarica, e solo quando
                            // c'e' davvero qualcosa che aspetta un tocco.
                            if (t == Tab.IMPOSTAZIONI && aggiornamentoStato.showsBadge()) {
                                BadgedBox(badge = { Badge() }) { icona() }
                            } else {
                                icona()
                            }
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = tint,
                            selectedTextColor = tint,
                            unselectedIconColor = Vetro.InkFaint,
                            unselectedTextColor = Vetro.InkFaint,
                            indicatorColor = Vetro.GlassStrong,
                        ),
                    )
                }
            }
        }

        // Strato 3: l'ascolto a tutto schermo, che sale dal basso.
        AnimatedVisibility(
            visible = nowPlayingOpen && player.current != null,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(200)),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Lo strato che ferma i tocchi sta SOTTO il contenuto,
                // non intorno.
                //
                // Prima era il contenitore a consumare quello che
                // arrivava: fermava i tocchi diretti all'elenco
                // sottostante, ma si metteva anche in mezzo fra i tasti
                // del player e le loro pressioni — un genitore che
                // consuma annulla il gesto che il figlio sta ancora
                // seguendo, e i comandi smettevano di rispondere.
                //
                // Messo sotto, l'ordine se ne occupa da solo: in un Box
                // si tocca prima quello che sta sopra, e qui arriva solo
                // cio' che non ha colpito niente.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                ) { VetroBackground() }

                Box(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                    NowPlayingScreen(vm, onClose = { nowPlayingOpen = false })
                }
            }
        }
    }

    // I pannelli modali stanno fuori dal Box: devono poter coprire
    // anche l'ascolto a tutto schermo.
    TrackSheet(vm)

    val queueOpen by vm.queueOpen.collectAsState()
    if (queueOpen) QueueSheet(vm, onDismiss = { vm.showQueue(false) })

    // Il tasto indietro chiude l'ascolto invece di uscire dall'app.
    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }
}
