package com.sosound.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.sosound.app.SoSoundApp
import com.sosound.app.data.catalog.AlbumPage
import com.sosound.app.data.catalog.AlbumRef
import com.sosound.app.data.catalog.ArtistPage
import com.sosound.app.data.catalog.DiscoverFeed
import com.sosound.app.data.catalog.PodcastPage
import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.catalog.SearchKind
import com.sosound.app.data.catalog.SearchResults
import com.sosound.app.data.download.QueueItem
import com.sosound.app.data.library.PlaylistEntity
import com.sosound.app.data.library.PlaylistSummary
import com.sosound.app.data.importing.Confidence
import com.sosound.app.data.importing.ImportRow
import com.sosound.app.data.importing.PlaylistFile
import com.sosound.app.data.importing.TrackMatcher
import com.sosound.app.data.library.TrackEntity
import com.sosound.app.data.storage.ImportPreview
import com.sosound.app.data.storage.ImportProblem
import com.sosound.app.data.storage.BackupStore
import com.sosound.app.data.storage.MusicStorage
import com.sosound.app.data.update.UpdateState
import com.sosound.app.playback.PlayerConnection
import com.sosound.app.playback.PlayerState
import com.sosound.app.ui.accent.AccentLoader
import com.sosound.app.ui.accent.TrackAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUi(
    val query: String = "",
    val kind: SearchKind = SearchKind.BRANI,
    val results: SearchResults = SearchResults(SearchKind.BRANI),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Una raccolta ricostruita dai brani che hai sul telefono. */
data class LocalAlbum(
    val title: String,
    val artist: String,
    val coverPath: String?,
    val tracks: List<com.sosound.app.data.library.TrackEntity>,
)

/**
 * Cosa mostra la scheda dettagli.
 *
 * Due sorgenti possibili: un brano che hai (con peso, formato, percorso)
 * oppure un risultato di ricerca che non hai ancora. La scheda mostra
 * quello che sa, e le azioni cambiano di conseguenza.
 */
data class TrackDetails(
    val owned: TrackEntity? = null,
    val catalog: CatalogTrack? = null,
) {
    val videoId: String get() = owned?.videoId ?: catalog?.videoId.orEmpty()
    val title: String get() = owned?.title ?: catalog?.title.orEmpty()
    val artist: String get() = owned?.artist ?: catalog?.artist.orEmpty()
    val album: String? get() = owned?.album ?: catalog?.album
    val coverPath: String? get() = owned?.coverPath
    val coverUrl: String? get() = catalog?.thumbnail
    val durationText: String
        get() = owned?.durationText ?: catalog?.durationText ?: "--:--"
    /**
     * Solo dal catalogo: un brano posseduto (`TrackEntity`) non porta
     * questo dato — aggiungerlo li' vorrebbe una migrazione del
     * database per un'informazione che i brani gia' scaricati non
     * hanno comunque mai avuto. Per un brano visto dalla ricerca invece
     * c'e' sempre.
     */
    val explicit: Boolean get() = catalog?.explicit == true
}

@UnstableApi
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as SoSoundApp
    private val dao = application.database.tracks()
    private val playlistDao = application.database.playlists()
    private val catalog = application.catalog
    private val downloader = application.downloader
    private val queue = application.downloadQueue
    private val storage = application.storage
    private val backup = application.backup
    private val appUpdateManager = application.appUpdateManager

    val player = PlayerConnection(app)
    val playerState: StateFlow<PlayerState> get() = player.state

    // ------------------------------------------- colore preso dalla copertina

    private val accentLoader = AccentLoader()
    private val _accent = MutableStateFlow(TrackAccent.Default)
    /** Tinta dei controlli del player, dal brano in ascolto. */
    val accent: StateFlow<TrackAccent> = _accent

    // ------------------------------------------------------------ libreria

    private val libraryQuery = MutableStateFlow("")

    val library: StateFlow<List<TrackEntity>> = libraryQuery
        .flatMapLatest { q ->
            if (q.isBlank()) dao.observeAll() else dao.observeSearch(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryFilter: StateFlow<String> = libraryQuery

    /** Gli id già presenti: serve alla ricerca per marcare i risultati. */
    val ownedIds: StateFlow<Set<String>> = dao.observeIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val usedBytes: StateFlow<Long> = dao.observeTotalBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun filterLibrary(q: String) {
        libraryQuery.value = q
    }

    fun play(tracks: List<TrackEntity>, index: Int) = player.play(tracks, index)

    /** Il guaio dell'ultima riproduzione, o null. */
    val erroreRiproduzione: StateFlow<String?> get() = player.errore

    init {
        // Gli avvisi che solo il servizio puo' sapere arrivano qui e si
        // vedono come gli altri.
        viewModelScope.launch {
            com.sosound.app.playback.PlaybackService.avvisi.collect { _backupMsg.value = it }
        }
    }
    fun scartaErroreRiproduzione() = player.scartaErrore()

    // ---------------------------------------------------------------- coda

    /** `queue` era gia' preso dalla coda dei download: qui si parla di
     *  quella di riproduzione, e due code con lo stesso nome nello stesso
     *  file sono un errore che aspetta di succedere. */
    val playQueue: StateFlow<List<TrackEntity>> get() = player.queue

    fun playNext(track: TrackEntity) = player.playNext(track)
    fun addToQueue(track: TrackEntity) = player.addToQueue(track)
    fun removeFromQueue(index: Int) = player.removeFromQueue(index)
    fun moveInQueue(from: Int, to: Int) = player.moveInQueue(from, to)
    fun jumpTo(index: Int) = player.jumpTo(index)
    fun clearQueue() = player.clearQueue()
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun shufflePlay(tracks: List<TrackEntity>) = player.shufflePlay(tracks)

    private val _queueOpen = MutableStateFlow(false)
    val queueOpen: StateFlow<Boolean> = _queueOpen
    fun showQueue(v: Boolean) { _queueOpen.value = v }

    fun remove(track: TrackEntity) = viewModelScope.launch {
        player.forget(track.videoId)
        dao.delete(track.videoId)
        downloader.remove(track)
        closeDetails()
    }

    // ------------------------------------------------------------ playlist

    val playlists: StateFlow<List<PlaylistSummary>> = playlistDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val openPlaylistId = MutableStateFlow<Long?>(null)
    val openPlaylist: StateFlow<Long?> = openPlaylistId

    val openPlaylistTracks: StateFlow<List<TrackEntity>> = openPlaylistId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else playlistDao.observeTracks(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openPlaylistName: StateFlow<String> = openPlaylistId
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf("")
            else playlistDao.observeOne(id).map { it?.name.orEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun openPlaylist(id: Long?) {
        openPlaylistId.value = id
    }

    fun createPlaylist(name: String, addAfter: String? = null) = viewModelScope.launch {
        val clean = name.trim().ifBlank { "Senza nome" }
        val id = playlistDao.insert(PlaylistEntity(name = clean, createdAt = System.currentTimeMillis()))
        // Creando una playlist dalla scheda di un brano, quel brano ci
        // finisce dentro: è il motivo per cui la stai creando.
        addAfter?.let { playlistDao.addTrack(id, it) }
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch {
        playlistDao.rename(id, name.trim().ifBlank { "Senza nome" })
    }

    fun deletePlaylist(id: Long) = viewModelScope.launch {
        if (openPlaylistId.value == id) openPlaylistId.value = null
        playlistDao.delete(id)
    }

    /**
     * Unisce due o piu' playlist in una coda sola, mischiata, e la avvia.
     *
     * Legge i brani di ogni playlist con [PlaylistDao.tracksOf] (una
     * lettura sola, non un flusso: qui serve uno scatto del contenuto
     * attuale, non restare in ascolto) e passa il resto a [mixPlaylists],
     * che decide unione, doppioni e mescolamento. Vedi li' il perche' la
     * lista nasce gia' mischiata invece di affidarsi allo shuffle del
     * player.
     */
    fun mixAndPlay(playlistIds: List<Long>) = viewModelScope.launch {
        val gruppi = playlistIds.map { playlistDao.tracksOf(it) }
        val mix = mixPlaylists(gruppi)
        if (mix.isNotEmpty()) play(mix, 0)
    }

    /**
     * Mette in libreria un brano del catalogo, senza scaricarlo.
     *
     * E' la riga che permette a un brano di esistere prima di avere un
     * file: entra in libreria, sta nelle playlist, si ascolta dalla
     * rete. Il download diventa una scelta successiva invece di un
     * passaggio obbligato.
     */
    private suspend fun assicuraInLibreria(d: TrackDetails): Boolean {
        if (dao.byId(d.videoId) != null) return true
        val c = d.catalog ?: return false
        dao.upsert(
            TrackEntity(
                videoId = c.videoId,
                title = c.title,
                artist = c.artist,
                album = c.album,
                durationSeconds = c.durationSeconds,
                // Vuoto: e' proprio questo che vuol dire «non scaricato».
                path = "",
                coverPath = null,
                sizeBytes = 0,
                addedAt = System.currentTimeMillis(),
                showId = c.showId,
            )
        )
        return true
    }

    /**
     * Aggiunge a una playlist un brano che puo' non essere ancora nostro.
     *
     * Prima bisognava scaricarlo e aspettare: due passaggi e un'attesa
     * per un gesto che nella testa di chi lo fa e' uno solo. Ora entra
     * subito — e siccome una playlist e' roba che si vuole avere, parte
     * anche lo scaricamento.
     */
    fun aggiungiAPlaylistDaiDettagli(playlistId: Long) = viewModelScope.launch {
        val d = _details.value ?: return@launch
        if (!assicuraInLibreria(d)) return@launch
        playlistDao.addTrack(playlistId, d.videoId)
        // Entrare in una playlist vuol dire entrare in libreria: da qui
        // compare negli elenchi e nell'indice.
        dao.segnaSalvato(d.videoId)
        // Scaricarlo davvero e' una scelta, e sta in un interruttore
        // solo — qui e nelle impostazioni. Spento, la playlist e' un
        // elenco e i brani si sentono; acceso, funziona anche in aereo.
        if (storage.sempreOffline.value && dao.byId(d.videoId)?.haFile != true) {
            d.catalog?.let { queue.enqueue(it) }
        }
    }

    fun addToPlaylist(playlistId: Long, videoId: String) = viewModelScope.launch {
        playlistDao.addTrack(playlistId, videoId)
    }

    fun removeFromPlaylist(playlistId: Long, videoId: String) = viewModelScope.launch {
        playlistDao.removeTrack(playlistId, videoId)
    }

    /**
     * Riordina una playlist.
     *
     * Si riscrive la posizione di tutti i brani e non solo dei due
     * scambiati: con le sole posizioni toccate si accumulano buchi e
     * duplicati, e dopo qualche trascinamento l'ordine non torna piu'.
     */
    fun movePlaylistTrack(playlistId: Long, from: Int, to: Int) = viewModelScope.launch {
        val attuale = openPlaylistTracks.value.toMutableList()
        if (from !in attuale.indices || to !in attuale.indices || from == to) return@launch
        attuale.add(to, attuale.removeAt(from))
        playlistDao.reorder(playlistId, attuale.map { it.videoId })
    }

    /** In quali playlist sta il brano aperto nella scheda dettagli. */
    private val _detailsPlaylists = MutableStateFlow<Set<Long>>(emptySet())
    val detailsPlaylists: StateFlow<Set<Long>> = _detailsPlaylists

    // ---------------------------------------------------------- importazione

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _import

    /** videoId -> playlist in cui infilarlo appena e' scaricato. */
    private val inAttesaDiPlaylist = mutableMapOf<String, Long>()

    fun resetImport() { _import.value = ImportState.Idle }

    private val spotify = com.sosound.app.data.importing.SpotifyLink()
    private val ytMusic = com.sosound.app.data.importing.YtMusicLink()

    /**
     * Importa da un link di Spotify incollato.
     *
     * Passa dalla pagina di anteprima, che e' pubblica: nessun account da
     * collegare. Se Spotify cambia la pagina questa strada si chiude, e
     * il messaggio d'errore dice di ripiegare sul file esportato.
     */
    fun startImportFromLink(url: String) = viewModelScope.launch {
        _import.value = ImportState.Working(0, 0)
        spotify.fetch(url).fold(
            onSuccess = { lista ->
                if (lista.forsePiuDiCosi) {
                    // Detto prima, non dopo: a import finito «47 su 47»
                    // sembra un risultato completo anche quando non lo e'.
                    _backupMsg.value =
                        "La pagina di anteprima di Spotify ne dà al massimo " +
                        "${lista.rows.size} per volta e non dice quanti sono in " +
                        "tutto: se la playlist è più lunga, il resto non arriva."
                }
                importRows(lista.rows, lista.name)
            },
            onFailure = {
                _import.value = ImportState.Failed(
                    it.message ?: "Non sono riuscito a leggere quel link."
                )
            },
        )
    }

    /**
     * Importa da un link di playlist di YouTube Music.
     *
     * Il link porta gia' l'identificativo della playlist: niente ricerca,
     * niente punteggio. Ogni brano arriva con il suo videoId vero e parte
     * come abbinamento sicuro — vedi [importCertainTracks].
     */
    fun startImportFromYtMusicLink(url: String) = viewModelScope.launch {
        _import.value = ImportState.Working(0, 0)
        ytMusic.fetch(url).fold(
            onSuccess = { lista ->
                if (lista.troncata) {
                    // Come per Spotify: dirlo prima, non lasciare che un
                    // «1000 su 1000» sembri un risultato completo. Capita
                    // soprattutto con le playlist radio (RD…), che non
                    // finiscono mai davvero.
                    _backupMsg.value =
                        "Questa playlist continua oltre i ${lista.tracks.size} brani: " +
                        "è una playlist molto lunga o una radio automatica di YouTube " +
                        "Music, che non ha una fine vera. Importati solo i primi " +
                        "${lista.tracks.size}."
                }
                importCertainTracks(lista.tracks, lista.name)
            },
            onFailure = {
                _import.value = ImportState.Failed(
                    it.message ?: "Non sono riuscito a leggere quel link."
                )
            },
        )
    }

    /**
     * Legge l'elenco e cerca ogni brano sul catalogo.
     *
     * Le ricerche sono in sequenza e non in parallelo: venti richieste
     * insieme a YouTube sono il modo piu' veloce per farsi rispondere
     * «sei un bot», ed e' proprio il file grande — quello che serve
     * davvero — a scatenarlo.
     */
    fun startImport(text: String, suggestedName: String) = viewModelScope.launch {
        val righe = PlaylistFile.parse(text)
        if (righe.isEmpty()) {
            _import.value = ImportState.Failed(
                "Non ho riconosciuto nessun brano. Serve un CSV esportato " +
                    "(Exportify, TuneMyMusic) o un elenco «Artista - Titolo»."
            )
            return@launch
        }

        importRows(righe, suggestedName)
    }

    private suspend fun importRows(righe: List<ImportRow>, nome: String) {
        _import.value = ImportState.Working(0, righe.size)
        val posseduti = ownedIds.value
        val esiti = mutableListOf<ImportEntry>()

        righe.forEachIndexed { i, row ->
            val risultati = runCatching {
                catalog.search(TrackMatcher.query(row), limit = 8)
            }.getOrDefault(emptyList())

            val m = TrackMatcher.match(row, risultati)
            esiti += ImportEntry(
                row = row,
                match = m.track,
                confidence = m.confidence,
                score = m.score,
                alternatives = m.alternatives,
                // Solo gli abbinamenti sicuri partono selezionati: gli
                // incerti li deve guardare una persona, altrimenti tanto
                // vale non averli marcati come incerti.
                selected = m.confidence == Confidence.SICURO,
                alreadyOwned = m.track?.videoId in posseduti,
            )
            _import.value = ImportState.Working(i + 1, righe.size)
        }

        // In cima le righe che hanno bisogno di una persona: non trovate,
        // poi incerte, infine quelle gia' sicure.
        _import.value = ImportState.Review(nome, esiti.ordinataPerRevisione())
    }

    /**
     * Come [importRows], ma per brani che arrivano gia' identificati con
     * certezza — un `videoId` vero letto da una playlist di YouTube
     * Music, non un titolo da cercare. Non passa da [TrackMatcher]: non
     * c'e' niente da indovinare quando si sa gia' esattamente qual e' il
     * brano.
     */
    private suspend fun importCertainTracks(tracce: List<CatalogTrack>, nome: String) {
        _import.value = ImportState.Working(0, tracce.size)
        val posseduti = ownedIds.value

        val esiti = tracce.mapIndexed { i, t ->
            // Qui il brano non e' cercato, e' gia' quello vero della
            // playlist di origine — anche quando e' l'audio di un video.
            // Non lo si scarta come fa la ricerca (sarebbe importare
            // un'altra canzone al posto di quella scelta): si cerca solo
            // un'alternativa pulita da proporre, senza sostituirla da
            // soli.
            val alternative = if (t.audioDiVideo) {
                runCatching { catalog.search("${t.artist} ${t.title}", limit = 5) }
                    .getOrDefault(emptyList())
                    .filterNot { it.videoId == t.videoId }
            } else emptyList()

            _import.value = ImportState.Working(i + 1, tracce.size)
            ImportEntry(
                row = ImportRow(
                    title = t.title,
                    artist = t.artist,
                    album = t.album,
                    durationSeconds = t.durationSeconds,
                ),
                match = t,
                confidence = Confidence.SICURO,
                score = 1.0,
                alternatives = alternative,
                selected = true,
                alreadyOwned = t.videoId in posseduti,
            )
        }

        _import.value = ImportState.Review(nome, esiti)
    }

    /**
     * Sostituisce in blocco ogni riga segnata come "audio di un video" con
     * la sua alternativa pulita, quando ce n'e' una pronta.
     *
     * Una sola per volta si fa gia' con [pickForEntry]; questa serve
     * quando sono tante — rifarlo riga per riga sarebbe un tocco a testa
     * su una playlist che puo' averne decine.
     */
    fun replaceAllVideoTracks() {
        val stato = _import.value as? ImportState.Review ?: return
        _import.value = stato.copy(
            entries = stato.entries.map { e ->
                val pulito = e.alternatives.firstOrNull { !it.audioDiVideo }
                if (e.match?.audioDiVideo == true && pulito != null) {
                    e.copy(
                        match = pulito,
                        confidence = Confidence.SICURO,
                        score = 1.0,
                        selected = true,
                        alreadyOwned = pulito.videoId in ownedIds.value,
                        alternatives = (e.alternatives + e.match)
                            .filter { it.videoId != pulito.videoId },
                    )
                } else e
            }
        )
    }

    fun toggleEntry(index: Int) {
        val stato = _import.value as? ImportState.Review ?: return
        _import.value = stato.copy(
            entries = stato.entries.mapIndexed { i, e ->
                if (i == index && e.match != null) e.copy(selected = !e.selected) else e
            }
        )
    }

    /**
     * Sceglie uno dei candidati proposti per una riga — incerta, o non
     * trovata ma con qualche alternativa a bassa fiducia.
     *
     * Funziona anche quando [candidate] e' gia' il risultato principale:
     * in quel caso equivale a confermarlo, senza toccare le alternative.
     */
    fun pickForEntry(index: Int, candidate: com.sosound.app.data.catalog.CatalogTrack) {
        val stato = _import.value as? ImportState.Review ?: return
        _import.value = stato.copy(
            entries = stato.entries.mapIndexed { i, e ->
                if (i != index) e
                else if (candidate.videoId == e.match?.videoId) e.copy(selected = true)
                else e.copy(
                    match = candidate,
                    // Scegliendo a mano la confidenza diventa massima:
                    // l'ha deciso una persona.
                    confidence = Confidence.SICURO,
                    score = 1.0,
                    selected = true,
                    alreadyOwned = candidate.videoId in ownedIds.value,
                    alternatives = (e.alternatives + listOfNotNull(e.match))
                        .filter { it.videoId != candidate.videoId },
                )
            }
        )
    }

    fun renameImport(name: String) {
        val stato = _import.value as? ImportState.Review ?: return
        _import.value = stato.copy(name = name)
    }

    /**
     * Manda alla scheda «Cerca» con titolo e artista di [entry] gia'
     * scritti nel campo — il recupero a mano per una riga che il
     * catalogo non e' riuscito a trovare da solo.
     *
     * Chiude l'import ma non lo azzera (niente [resetImport]): l'utente
     * puo' tornare indietro e ritrovare la revisione dov'era, con questa
     * riga ancora li' se non l'ha aggiunta alla libreria dalla ricerca.
     */
    fun searchManually(entry: ImportEntry) {
        val query = "${entry.row.artist} ${entry.row.title}".trim()
        setImportOpen(false)
        _search.value = _search.value.copy(
            kind = SearchKind.BRANI,
            query = query,
            results = SearchResults(SearchKind.BRANI),
        )
        rilancia(immediato = true)
        _goToSearch.value = true
    }

    /** Crea la playlist e mette in coda quello che manca. */
    fun confirmImport() = viewModelScope.launch {
        val stato = _import.value as? ImportState.Review ?: return@launch
        val scelti = stato.entries.filter { it.selected && it.match != null }
        if (scelti.isEmpty()) return@launch

        val id = playlistDao.insert(
            PlaylistEntity(
                name = stato.name.trim().ifBlank { "Importata" },
                createdAt = System.currentTimeMillis(),
            )
        )

        var accodati = 0
        for (e in scelti) {
            val t = e.match ?: continue
            if (e.alreadyOwned) {
                // Gia' in libreria: dentro subito, senza riscaricarlo.
                playlistDao.addTrack(id, t.videoId)
            } else {
                inAttesaDiPlaylist[t.videoId] = id
                queue.enqueue(t)
                accodati++
            }
        }
        _import.value = ImportState.Done(stato.name, accodati)
    }

    // ------------------------------------------------------- scheda dettagli

    private val _details = MutableStateFlow<TrackDetails?>(null)
    val details: StateFlow<TrackDetails?> = _details

    private var detailsJob: Job? = null

    fun showDetails(track: TrackEntity) {
        openDetails(TrackDetails(owned = track))
        // I collegamenti ad artista e album vivono nel catalogo, non nel
        // database: di un brano scaricato sappiamo il nome dell'artista,
        // non il suo indirizzo. Si chiede al catalogo, e quando la
        // risposta arriva i due nomi diventano cliccabili — la scheda e'
        // gia' aperta e non aspetta.
        arricchisciJob?.cancel()
        arricchisciJob = viewModelScope.launch {
            val trovato = runCatching {
                catalog.search("${track.artist} ${track.title}", SearchKind.BRANI, limit = 10)
                    .tracks.firstOrNull { it.videoId == track.videoId }
            }.getOrNull() ?: return@launch
            // Solo se la scheda e' ancora quella: nel frattempo puo'
            // essere stata chiusa o aperta su un altro brano.
            val adesso = _details.value
            if (adesso?.videoId == track.videoId && adesso.catalog == null) {
                _details.value = adesso.copy(catalog = trovato)
            }
        }
    }

    private var arricchisciJob: Job? = null

    fun showDetails(track: CatalogTrack) = viewModelScope.launch {
        // Se ce l'abbiamo già, mostriamo la scheda ricca invece di quella
        // povera del catalogo: peso e formato l'utente li vuole vedere.
        val owned = dao.byId(track.videoId)
        openDetails(TrackDetails(owned = owned, catalog = track))
    }

    private fun openDetails(d: TrackDetails) {
        _details.value = d
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            playlistDao.observePlaylistsOf(d.videoId).collect {
                _detailsPlaylists.value = it.toSet()
            }
        }
    }

    /**
     * Apre artista, album o podcast partendo dalla scheda di un brano.
     *
     * Chiude la scheda e chiede di passare a «Cerca», dove vivono quelle
     * pagine: aprirle sotto la Libreria lascerebbe l'utente in un posto
     * che la barra in fondo dice essere un altro.
     */
    fun browseFromDetails(target: String, tipo: String) {
        closeDetails()
        when (tipo) {
            "artista" -> openArtist(target)
            "album" -> openAlbum(target)
            "podcast" -> openPodcast(target)
        }
        _goToSearch.value = true
    }

    private val _goToSearch = MutableStateFlow(false)
    val goToSearch: StateFlow<Boolean> = _goToSearch
    fun searchTabShown() { _goToSearch.value = false }

    /** Chiude l'introduzione portando alla ricerca invece che all'import. */
    fun goToSearchTab() { _goToSearch.value = true }


    fun closeDetails() {
        detailsJob?.cancel()
        _details.value = null
    }

    // ------------------------------------------------------------- ricerca

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _search.value = _search.value.copy(query = q)
        rilancia(immediato = false)
    }

    /** Cambiare tipo ricerca subito: l'utente ha gia' finito di scrivere. */
    fun onKindChange(kind: SearchKind) {
        if (_search.value.kind == kind) return
        _search.value = _search.value.copy(
            kind = kind,
            results = SearchResults(kind),
        )
        rilancia(immediato = true)
    }

    private fun rilancia(immediato: Boolean) {
        val stato = _search.value
        searchJob?.cancel()
        if (stato.query.isBlank()) {
            _search.value = stato.copy(
                results = SearchResults(stato.kind), loading = false, error = null,
            )
            return
        }
        searchJob = viewModelScope.launch {
            // Aspetta che l'utente smetta di scrivere: senza, ogni lettera
            // manda una richiesta e i risultati arrivano fuori ordine.
            if (!immediato) delay(400)
            _search.value = _search.value.copy(loading = true, error = null)
            runCatching { catalog.search(stato.query, stato.kind) }
                .onSuccess { _search.value = _search.value.copy(results = it, loading = false) }
                .onFailure {
                    _search.value = _search.value.copy(
                        loading = false,
                        error = it.message ?: "Ricerca non riuscita",
                    )
                }
        }
    }

    // --------------------------------------------------- album e artisti

    private val _albumPage = MutableStateFlow<AlbumPage?>(null)
    val albumPage: StateFlow<AlbumPage?> = _albumPage

    private val _artistPage = MutableStateFlow<ArtistPage?>(null)
    val artistPage: StateFlow<ArtistPage?> = _artistPage

    private val _podcastPage = MutableStateFlow<PodcastPage?>(null)
    val podcastPage: StateFlow<PodcastPage?> = _podcastPage

    private val _playlistPage = MutableStateFlow<com.sosound.app.data.catalog.PlaylistPage?>(null)
    val playlistPage: StateFlow<com.sosound.app.data.catalog.PlaylistPage?> = _playlistPage

    private val _browsing = MutableStateFlow(false)
    val browsing: StateFlow<Boolean> = _browsing

    /**
     * Le pagine visitate, in ordine.
     *
     * Serve perche' da un brano si va all'artista, da li' a un album, e
     * da quello a un altro artista: senza una pila, il tasto indietro
     * riporterebbe alla ricerca saltando tutti i passaggi in mezzo.
     */
    private sealed interface Tappa {
        data class Album(val id: String) : Tappa
        data class Artista(val id: String) : Tappa
        data class Podcast(val id: String) : Tappa
        data class Playlist(val id: String) : Tappa
    }

    private val pila = mutableListOf<Tappa>()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    fun openAlbum(browseId: String) = vai(Tappa.Album(browseId))
    fun openArtist(browseId: String) = vai(Tappa.Artista(browseId))
    fun openPodcast(browseId: String) = vai(Tappa.Podcast(browseId))
    // Non "openPlaylist": quel nome è già preso da una playlist LOCALE
    // aperta in Libreria (id numerico). Questa apre una playlist
    // PUBBLICA di YouTube Music, trovata cercando (browseId).
    fun browsePlaylist(browseId: String) = vai(Tappa.Playlist(browseId))

    private fun vai(t: Tappa) {
        pila += t
        carica(t)
    }

    private fun carica(t: Tappa) = viewModelScope.launch {
        _browsing.value = true
        _albumPage.value = null
        _artistPage.value = null
        _podcastPage.value = null
        _playlistPage.value = null
        when (t) {
            is Tappa.Album -> _albumPage.value = runCatching { catalog.album(t.id) }.getOrNull()
            is Tappa.Artista -> _artistPage.value = runCatching { catalog.artist(t.id) }.getOrNull()
            is Tappa.Podcast -> _podcastPage.value = runCatching { catalog.podcast(t.id) }.getOrNull()
            is Tappa.Playlist -> _playlistPage.value = runCatching { catalog.playlist(t.id) }.getOrNull()
        }
        _browsing.value = false
        _canGoBack.value = pila.isNotEmpty()
    }

    /** Torna alla pagina precedente, o alla ricerca se non ce ne sono. */
    fun browseBack() {
        if (pila.isNotEmpty()) pila.removeAt(pila.lastIndex)
        val precedente = pila.lastOrNull()
        _canGoBack.value = pila.isNotEmpty()
        if (precedente == null) closeBrowse() else carica(precedente)
    }

    fun closeBrowse() {
        pila.clear()
        _canGoBack.value = false
        _albumPage.value = null
        _artistPage.value = null
        _podcastPage.value = null
        _playlistPage.value = null
    }

    // --------------------------------------------------- le proposte

    private val _discover = MutableStateFlow(DiscoverFeed())
    val discover: StateFlow<DiscoverFeed> = _discover

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering

    /** Si carica una volta sola: le classifiche non cambiano al minuto. */
    fun loadDiscover() {
        if (_discovering.value || !_discover.value.isEmpty) return
        viewModelScope.launch {
            _discovering.value = true
            _discover.value = runCatching { catalog.discover() }.getOrDefault(DiscoverFeed())
            _discovering.value = false
        }
    }

    /** Scarica tutte le puntate di un podcast. */
    fun downloadPodcast(page: PodcastPage) {
        page.episodes.forEach { queue.enqueue(it.copy(album = page.title)) }
    }

    /** Scarica tutte le tracce di un album, in ordine. */
    fun downloadAlbum(page: AlbumPage) {
        page.tracks.forEach { t ->
            // La copertina delle tracce di un album e' vuota: quella
            // buona e' dell'album, e senza passarla ogni brano
            // resterebbe senza immagine.
            queue.enqueue(t.copy(thumbnail = t.thumbnail ?: page.thumbnail))
        }
    }

    /** Scarica l'album e lo mette tutto in una playlist. */
    fun albumToPlaylist(page: AlbumPage, playlistId: Long) = viewModelScope.launch {
        for (t in page.tracks) {
            if (t.videoId in ownedIds.value) {
                playlistDao.addTrack(playlistId, t.videoId)
            } else {
                inAttesaDiPlaylist[t.videoId] = playlistId
                queue.enqueue(t.copy(thumbnail = t.thumbnail ?: page.thumbnail))
            }
        }
    }

    /** Scarica tutti i brani di una playlist pubblica di YouTube Music
     *  trovata cercando — stesso giro di [downloadAlbum]. */
    fun downloadPublicPlaylist(page: com.sosound.app.data.catalog.PlaylistPage) {
        page.tracks.forEach { t ->
            queue.enqueue(t.copy(thumbnail = t.thumbnail ?: page.thumbnail))
        }
    }

    /** Scarica una playlist pubblica e la mette tutta in una playlist
     *  nostra — stesso giro di [albumToPlaylist]. */
    fun publicPlaylistToPlaylist(page: com.sosound.app.data.catalog.PlaylistPage, playlistId: Long) =
        viewModelScope.launch {
            for (t in page.tracks) {
                if (t.videoId in ownedIds.value) {
                    playlistDao.addTrack(playlistId, t.videoId)
                } else {
                    inAttesaDiPlaylist[t.videoId] = playlistId
                    queue.enqueue(t.copy(thumbnail = t.thumbnail ?: page.thumbnail))
                }
            }
        }

    /**
     * Le raccolte ricostruite dai brani scaricati.
     *
     * Non serve una tabella «album»: il nome sta gia' su ogni brano, e
     * raggrupparlo e' sufficiente. Una tabella in piu' andrebbe tenuta
     * allineata a mano, e si disallineerebbe.
     */
    val localAlbums: StateFlow<List<LocalAlbum>> = library
        .map { tracks ->
            tracks.filter { !it.album.isNullOrBlank() }
                .groupBy { it.album!! to it.artist }
                .map { (chiave, brani) ->
                    LocalAlbum(
                        title = chiave.first,
                        artist = chiave.second,
                        coverPath = brani.firstNotNullOfOrNull { it.coverPath },
                        tracks = brani.sortedBy { it.title },
                    )
                }
                .sortedBy { it.title.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _openLocalAlbum = MutableStateFlow<String?>(null)
    val openLocalAlbum: StateFlow<String?> = _openLocalAlbum
    fun openLocalAlbum(titolo: String?) { _openLocalAlbum.value = titolo }

    /**
     * Toccare un risultato di ricerca: parte.
     *
     * Se il brano c'è già parte subito; se non c'è, si scarica e parte da
     * solo appena pronto. È il comportamento che una persona si aspetta —
     * l'alternativa, «scarica e poi vai in un'altra scheda a cercartelo»,
     * costringe a conoscere come è fatta l'app dentro.
     */
    /**
     * Si tocca un risultato e parte.
     *
     * Prima si metteva in coda uno scaricamento e si aspettava che
     * finisse: per sentire tre secondi di una canzone bisognava
     * scaricarla tutta. Ora il brano entra in libreria senza file e
     * suona dalla rete — quello che arriva resta in cache, quindi la
     * seconda volta parte da fermo.
     *
     * Scaricarlo resta possibile e adesso e' una scelta: «tieni anche
     * senza rete», dalla scheda del brano.
     */
    fun playFromSearch(track: CatalogTrack) = viewModelScope.launch {
        val owned = dao.byId(track.videoId)
        if (owned != null) {
            player.play(listOf(owned), 0)
            return@launch
        }
        val riferimento = riferimentoPer(track)
        player.play(listOf(riferimento), 0)
        // La copertina si prende comunque: e' piccola, e senza, la
        // schermata di blocco resta vuota mentre il brano suona.
        launch { runCatching { copertinaPerRiferimento(riferimento, track.thumbnail) } }
    }

    /**
     * Un brano del catalogo che non e' ancora nostro, pero' nel
     * database: di passaggio, sta nella cache e non negli elenchi ne'
     * nell'indice. Lo diventa per davvero se lo si mette in una
     * playlist o si sceglie di tenerlo. Usato per farlo suonare subito
     * ([playFromSearch]) e per metterlo in coda senza scaricarlo prima
     * ([addToQueue], [playNext]).
     */
    private suspend fun riferimentoPer(track: CatalogTrack): TrackEntity {
        val riferimento = TrackEntity(
            videoId = track.videoId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds,
            path = "",
            coverPath = null,
            sizeBytes = 0,
            addedAt = System.currentTimeMillis(),
            showId = track.showId,
            salvato = false,
        )
        dao.upsert(riferimento)
        return riferimento
    }

    /**
     * Mette in coda di riproduzione un brano trovato cercando, anche se
     * non e' ancora sul telefono — prima si poteva solo con un brano
     * gia' scaricato.
     *
     * Entra come riferimento (vedi [riferimentoPer]), cosi' la coda lo
     * tratta come un brano vero, e si scarica da solo in sottofondo
     * sulla stessa coda di scaricamento del tasto «+» — cosi' e' gia'
     * pronto quando arriva il suo turno, invece di fermare la
     * riproduzione nel momento in cui tocca a lui per risolverlo dalla
     * rete. L'avviso in fondo allo schermo dice che e' successo: senza,
     * toccare «aggiungi alla coda» su un brano mai sentito prima
     * sembrerebbe non aver fatto niente.
     */
    fun addToQueue(track: CatalogTrack) = codaDalCatalogo(track, subito = false)

    /** Come [addToQueue] ma lo mette subito dopo il brano in ascolto. */
    fun playNext(track: CatalogTrack) = codaDalCatalogo(track, subito = true)

    private fun codaDalCatalogo(track: CatalogTrack, subito: Boolean) = viewModelScope.launch {
        val owned = dao.byId(track.videoId)
        val voce = owned ?: riferimentoPer(track).also { r ->
            launch { runCatching { copertinaPerRiferimento(r, track.thumbnail) } }
        }
        if (subito) player.playNext(voce) else player.addToQueue(voce)

        _backupMsg.value = if (owned != null) {
            if (subito) "«${track.title}» è la prossima." else "«${track.title}» aggiunta alla coda."
        } else {
            // Solo in cache: metterlo in coda non e' una scelta di
            // tenerlo. Se lo si vuole anche dopo, si salva a parte —
            // dal player o dalla sua scheda.
            queue.enqueue(track, salvato = false)
            "«${track.title}» ${if (subito) "è la prossima" else "in coda"}: si scarica in sottofondo."
        }
    }

    /** Scarica solo l'immagine, non l'audio. */
    private suspend fun copertinaPerRiferimento(t: TrackEntity, url: String?) {
        if (url.isNullOrBlank()) return
        val percorso = storage.salvaCopertinaDaRete(t.videoId, url) ?: return
        val aggiornato = (dao.byId(t.videoId) ?: t).copy(coverPath = percorso)
        dao.upsert(aggiornato)
        // E anche nella coda che sta gia' suonando: il database da solo
        // non basta, perche' il player si e' portato via una copia del
        // brano quando e' partito.
        player.aggiorna(aggiornato)
    }

    // --------------------------------------------------------------- coda

    val downloads: StateFlow<List<QueueItem>> get() = queue.items
    val engineReady: StateFlow<Boolean> get() = queue.engineReady

    /**
     * «Tieni anche senza rete»: da riferimento a file vero.
     *
     * E' lo stesso scaricamento di sempre. La differenza e' che adesso
     * non e' il modo per ascoltare un brano — e' il modo per non
     * dipendere dalla rete per riascoltarlo.
     */
    fun tieniSenzaRete(videoId: String) = viewModelScope.launch {
        val t = dao.byId(videoId) ?: return@launch
        dao.segnaSalvato(videoId)
        // Anche nella coda che sta gia' suonando, e subito: chi guarda
        // l'ascolto vuole vedere il tasto cambiare nello stesso istante
        // in cui lo tocca, non quando lo scaricamento (che puo' metterci
        // secondi) sarà finito.
        player.aggiorna(t.copy(salvato = true))
        if (t.haFile) return@launch

        // Prima si guarda se ce l'abbiamo gia'.
        //
        // Un brano ascoltato per intero e' tutto nella cache: scaricarlo
        // di nuovo sarebbe chiedere alla rete qualcosa che e' sul
        // telefono. Si prova a tirarlo fuori, e solo se il pezzo manca
        // si va a prenderlo.
        if (promuoviDallaCache(t)) return@launch
        queue.enqueue(
            CatalogTrack(
                videoId = t.videoId,
                title = t.title,
                artist = t.artist,
                album = t.album,
                durationSeconds = t.durationSeconds,
                showId = t.showId,
            )
        )
    }

    /**
     * Da cache a file vero, senza rete. Falso se non era tutto li'.
     */
    private suspend fun promuoviDallaCache(t: TrackEntity): Boolean =
        withContext(Dispatchers.IO) {
            val tmp = java.io.File(application.cacheDir, "promuovi-${t.videoId}")
            val estensione = runCatching {
                com.sosound.app.playback.CacheAudio.estrai(application, t.videoId, tmp)
            }.getOrNull()
            if (estensione == null || tmp.length() <= 0) {
                tmp.delete()
                return@withContext false
            }
            runCatching {
                val conNome = java.io.File(tmp.parentFile, "${t.videoId}.$estensione")
                tmp.renameTo(conNome)
                val posizione = storage.publish(t.videoId, t.artist, t.title, conNome)
                dao.upsert(t.copy(path = posizione, sizeBytes = conNome.length().takeIf { it > 0 } ?: t.sizeBytes))
                _backupMsg.value = "«${t.title}» era già sul telefono: tenuto senza riscaricarlo."
                true
            }.getOrElse {
                tmp.delete()
                false
            }
        }

    /** Scarica soltanto, senza far partire niente. */
    fun add(track: CatalogTrack) = queue.enqueue(track)

    fun dismiss(videoId: String) = queue.dismiss(videoId)
    fun retryDownload(videoId: String) = queue.retry(videoId)

    // ------------------------------------------------------------ archivio

    /** La cartella scelta, o null se si usa quella di serie. */
    val storageTree: StateFlow<android.net.Uri?> get() = storage.tree

    fun storageLabel(): String = storage.describe()

    /** Quanti brani stanno fuori dalla destinazione corrente. */
    val misplaced: StateFlow<Int> = combine(storage.tree, library) { tree, tracks ->
        tracks.count { (it.path.startsWith("content://")) == (tree == null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _moving = MutableStateFlow<String?>(null)
    /** Messaggio di avanzamento dello spostamento, o null se fermo. */
    val moving: StateFlow<String?> = _moving

    /**
     * Sceglie la cartella di destinazione.
     *
     * Prima guarda se contiene gia' un backup: in quel caso non la si
     * adotta di nascosto ma si chiede cosa farne, perche' scegliere una
     * cartella e ritrovarsi la libreria di un'altra installazione
     * sarebbe una sorpresa.
     */
    /** Vero mentre si sta guardando dentro una cartella appena scelta. */
    private val _guardando = MutableStateFlow(false)
    val guardandoCartella: StateFlow<Boolean> = _guardando

    fun chooseFolder(uri: android.net.Uri) = viewModelScope.launch {
        _cartellaInAttesa.value = uri
        // Acceso subito: aprire l'elenco di una cartella di Drive puo'
        // richiedere parecchi secondi, e in quei secondi non succedeva
        // niente di visibile — che e' indistinguibile da un tocco non
        // registrato.
        _guardando.value = true
        _backupMsg.value = null
        _esame.value = null
        val esito = backup.inspect(uri)
        _guardando.value = false
        esito.fold(
            onSuccess = { _anteprima.value = it },
            onFailure = { errore ->
                // Nessun backup dentro: e' solo una cartella, si adotta.
                _anteprima.value = null
                _cartellaInAttesa.value = null
                runCatching { storage.adopt(uri) }
                    .onSuccess {
                        controllaAnnidate()
                        // Se conteneva qualcosa e non si e' riusciti a
                        // leggerlo, tacere e' peggio che dirlo: da fuori
                        // si vedrebbe solo una cartella scelta e nessun
                        // brano, senza sapere perche'.
                        val motivo = (errore as? BackupStore.ImportException)?.problem
                        if (motivo != null) {
                            _backupMsg.value =
                                "Cartella impostata, ma la libreria dentro non si è " +
                                "potuta leggere ($motivo)."
                        }
                    }
                    .onFailure { e -> _backupMsg.value = "Cartella non utilizzabile: ${e.message}" }
            },
        )
    }

    private val _cartellaInAttesa = MutableStateFlow<android.net.Uri?>(null)
    val cartellaInAttesa: StateFlow<android.net.Uri?> = _cartellaInAttesa

    /** Adotta la cartella senza importare niente. */
    fun useFolderOnly() = viewModelScope.launch {
        val uri = _cartellaInAttesa.value ?: return@launch
        runCatching { storage.adopt(uri) }
            .onSuccess { _backupMsg.value = "Cartella impostata. Il backup esistente resta com'è." }
            .onFailure { _backupMsg.value = "Cartella non utilizzabile: ${it.message}" }
        _anteprima.value = null
        _cartellaInAttesa.value = null
    }

    fun useDefaultFolder() = storage.useDefault()

    // ------------------------------------------- le cartelle annidate

    private val _annidate = MutableStateFlow(0)
    /** Quanti file stanno in una cartella SoSound dentro l'altra. */
    val annidate: StateFlow<Int> = _annidate

    private val _unendo = MutableStateFlow<String?>(null)
    val unendo: StateFlow<String?> = _unendo

    fun controllaAnnidate() = viewModelScope.launch {
        _annidate.value = runCatching { storage.trovaAnnidate().spostati }.getOrDefault(0)
    }

    /** Porta il contenuto della cartella interna in quella esterna. */
    fun unisciAnnidate() = viewModelScope.launch {
        _unendo.value = "Unisco le due cartelle…"
        val esito = runCatching {
            storage.unisciAnnidate { fatti, totale -> _unendo.value = "Sposto $fatti di $totale…" }
        }.getOrNull()
        _unendo.value = null
        _annidate.value = 0
        _backupMsg.value = when {
            esito == null -> "Non sono riuscito a unire le cartelle."
            esito.falliti > 0 -> "Spostati ${esito.spostati} file, ${esito.falliti} no: quelli sono rimasti dentro."
            else -> "Fatto: ${esito.spostati} file portati nella cartella principale."
        }
        // L'indice va riscritto: i percorsi sono cambiati tutti.
        backup.write()
    }

    val sempreOffline: StateFlow<Boolean> get() = storage.sempreOffline
    val sentiNotifiche: StateFlow<Boolean> get() = storage.sentiNotifiche
    fun impostaSentiNotifiche(v: Boolean) = storage.impostaSentiNotifiche(v)

    private val _diagnosiAudio = MutableStateFlow<String?>(null)
    /** Cosa l'app ha visto suonare, per capire se l'abbassamento puo' scattare. */
    val diagnosiAudio: StateFlow<String?> = _diagnosiAudio

    fun controllaAudio() {
        _diagnosiAudio.value = com.sosound.app.playback.PlaybackService.statoAudio()
            ?: "La riproduzione non è attiva: fai partire un brano e riprova."
    }
    val destinazioneFragile: StateFlow<Boolean> get() = storage.destinazioneFragile

    /**
     * Accende o spegne «tieni tutto anche senza rete».
     *
     * Accendendolo si mette subito in coda quello che e' gia' in
     * playlist e non ha ancora un file: se no l'interruttore
     * prometterebbe una cosa che vale solo per il futuro.
     */
    fun impostaSempreOffline(valore: Boolean) = viewModelScope.launch {
        storage.impostaSempreOffline(valore)
        if (!valore) return@launch
        for (t in dao.tutti()) {
            if (t.salvato && !t.haFile) tieniSenzaRete(t.videoId)
        }
    }

    // ------------------------------------------------------------- cache

    private val _cacheByte = MutableStateFlow(0L)
    /** Quanto occupa adesso quello che si e' ascoltato. */
    val cacheByte: StateFlow<Long> = _cacheByte

    fun misuraCache() = viewModelScope.launch {
        _cacheByte.value = withContext(Dispatchers.IO) {
            runCatching { com.sosound.app.playback.CacheAudio.occupati(application) }.getOrDefault(0L)
        }
    }

    /**
     * Svuota la cache dell'ascolto.
     *
     * Non tocca i brani tenuti «anche senza rete»: quelli hanno un file
     * loro nella cartella, e questo cancella solo cio' che si era
     * fermato di passaggio.
     */
    fun svuotaCache() = viewModelScope.launch {
        // Quello che sta suonando non si tocca.
        val inCorso = player.state.value.current?.videoId?.takeIf {
            player.state.value.isPlaying
        }
        withContext(Dispatchers.IO) {
            runCatching {
                com.sosound.app.playback.CacheAudio.svuota(application, risparmia = inCorso)
            }
        }
        misuraCache()
        _backupMsg.value =
            if (inCorso != null) "Cache svuotata. Il brano in riproduzione è rimasto."
            else "Cache svuotata."
    }

    // ------------------------------------------------- esame della cartella

    private val _esame = MutableStateFlow<String?>(null)
    /** Il referto di «Esamina la cartella», o null se non e' stato chiesto. */
    val esame: StateFlow<String?> = _esame

    private val _inEsame = MutableStateFlow(false)
    val inEsame: StateFlow<Boolean> = _inEsame

    /**
     * Chiede all'app di raccontare cosa vede nella cartella scelta.
     *
     * Serve quando l'import non importa: da fuori il sintomo e' sempre
     * lo stesso, e le cause sono diverse.
     */
    /** Prepara il referto per la cartella in attesa, senza chiederlo. */
    fun preparaEsame() = viewModelScope.launch {
        val uri = _cartellaInAttesa.value ?: return@launch
        if (_esame.value != null) return@launch
        _esame.value = runCatching { backup.diagnostica(uri, _anteprima.value) }
            .getOrElse { "Non riesco a guardarci dentro: ${it.message}" }
    }

    fun esaminaCartella() = viewModelScope.launch {
        val uri = storage.tree.value ?: run {
            _esame.value = "Nessuna cartella scelta: i brani stanno nello spazio privato dell'app."
            return@launch
        }
        _inEsame.value = true
        _esame.value = runCatching { backup.diagnostica(uri) }
            .getOrElse { "L'esame non è riuscito: ${it.message}" }
        _inEsame.value = false
    }

    fun chiudiEsame() { _esame.value = null }

    /**
     * Sposta nella destinazione corrente i brani rimasti nell'altra.
     *
     * Uno alla volta, e ogni brano viene copiato prima e cancellato
     * dopo: se lo spostamento si interrompe a meta' si perde spazio, non
     * musica.
     */
    fun moveExisting() = viewModelScope.launch {
        val tracks = dao.all()
        val tree = storage.tree.value
        val daSpostare = tracks.filter {
            // Chi non ha un file non ha niente da spostare: prima
            // finiva in questo elenco e veniva contato fra quelli «non
            // riusciti», che era un modo di dire il falso.
            it.haFile && (it.path.startsWith("content://")) == (tree == null)
        }
        if (daSpostare.isEmpty()) return@launch

        var fatti = 0
        var falliti = 0
        for (t in daSpostare) {
            _moving.value = "Sposto ${fatti + falliti + 1} di ${daSpostare.size}…"
            runCatching { storage.relocate(t) }
                .onSuccess { esito ->
                    if (esito != null) {
                        dao.upsert(t.copy(path = esito.path, coverPath = esito.coverPath))
                        fatti++
                    }
                }
                .onFailure { falliti++ }
        }
        _moving.value = null
        _engineMessage.value = when {
            falliti == 0 -> "Spostati $fatti brani"
            else -> "Spostati $fatti brani, $falliti non riusciti"
        }
    }

    /**
     * Riscarica le copertine mancanti.
     *
     * Serve a rimediare ai brani che hanno perso il file: fino a poco fa
     * la copertina stava nella stessa cartella dell'audio, e spostare i
     * brani se la portava via. Il riferimento nel database restava, ma
     * puntava al vuoto.
     */
    fun repairCovers() = viewModelScope.launch {
        val rotti = dao.all().filter { !storage.coverExists(it.coverPath) }
        if (rotti.isEmpty()) {
            _engineMessage.value = "Le copertine ci sono tutte"
            return@launch
        }

        var recuperate = 0
        _moving.value = "Recupero le copertine…"
        for ((i, t) in rotti.withIndex()) {
            _moving.value = "Copertina ${i + 1} di ${rotti.size}…"

            // Prima si prova a rimetterla al sicuro: a volte il file c'e'
            // ancora ma il database punta al posto sbagliato.
            val salvata = runCatching { storage.ensureCoverSafe(t.videoId, t.coverPath) }.getOrNull()
            if (salvata != null && storage.coverExists(salvata)) {
                dao.upsert(t.copy(coverPath = salvata)); recuperate++; continue
            }

            // Altrimenti si riscarica.
            val bytes = scaricaCopertina(t) ?: continue
            storage.saveCover(t.videoId, bytes)?.let {
                dao.upsert(t.copy(coverPath = it)); recuperate++
            }
        }
        _moving.value = null
        _engineMessage.value = when (recuperate) {
            rotti.size -> "Recuperate tutte e $recuperate le copertine"
            0 -> "Nessuna copertina recuperata. Controlla la connessione."
            else -> "Recuperate $recuperate copertine su ${rotti.size}"
        }
    }

    /**
     * Riscarica la copertina di un brano.
     *
     * ⚠️ `withContext(Dispatchers.IO)` non e' decorativo: viewModelScope
     * gira sul thread principale, e una lettura di rete da li' lancia
     * NetworkOnMainThreadException. Finendo dentro un runCatching,
     * l'eccezione spariva e ogni copertina falliva in silenzio — il
     * contatore avanzava e non si salvava niente.
     *
     * Due sorgenti, in ordine di qualita':
     *
     *  1. la copertina quadrata di YouTube Music, se la ricerca ritrova
     *     esattamente questo brano;
     *  2. la miniatura del video, costruita dall'identificativo. Non
     *     dipende da nessuna ricerca, quindi funziona sempre — ed e'
     *     quello che serve, perche' cercare per titolo e artista e
     *     pretendere lo stesso videoId fra i primi risultati fallisce
     *     spesso, e per le puntate dei podcast quasi sempre.
     */
    private suspend fun scaricaCopertina(t: TrackEntity): ByteArray? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val candidati = buildList {
                runCatching {
                    catalog.search("${t.title} ${t.artist}", limit = 8)
                        .firstOrNull { it.videoId == t.videoId }?.thumbnail
                }.getOrNull()?.let { add(it) }
                add("https://i.ytimg.com/vi/${t.videoId}/maxresdefault.jpg")
                add("https://i.ytimg.com/vi/${t.videoId}/hqdefault.jpg")
            }

            for (url in candidati) {
                val bytes = runCatching {
                    java.net.URL(url).openStream().use { it.readBytes() }
                }.getOrNull()
                // Sotto il chilobyte e' il segnaposto grigio che YouTube
                // restituisce quando la miniatura chiesta non esiste.
                if (bytes != null && bytes.size > 1024) return@withContext bytes
            }
            null
        }

    /** I programmi di cui hai almeno una puntata. */
    val myShows: StateFlow<List<TrackEntity>> = dao.observeShows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Quante copertine risultano mancanti. */
    val brokenCovers: StateFlow<Int> = library
        .map { tracks -> tracks.count { !storage.coverExists(it.coverPath) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ----------------------------------------------------------- backup

    private val _backupMsg = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMsg

    private val _anteprima = MutableStateFlow<ImportPreview?>(null)
    val anteprimaImport: StateFlow<ImportPreview?> = _anteprima

    fun clearBackupMessage() { _backupMsg.value = null }
    fun clearAnteprima() {
        _anteprima.value = null
        _cartellaInAttesa.value = null
    }

    /**
     * Quanti brani finirebbero in doppio importando.
     *
     * Non e' un problema — un videoId e' una chiave, quindi il doppione
     * si sovrascrive invece di moltiplicarsi — ma va detto prima, se no
     * i numeri dopo l'importazione non tornano con quelli annunciati.
     */
    fun duplicatiConAnteprima(): Int {
        val p = _anteprima.value ?: return 0
        val miei = ownedIds.value
        return p.validi.count { it.videoId in miei }
    }

    /**
     * Riscrive l'indice nella cartella.
     *
     * Costa una lettura del database e pochi kilobyte scritti, quindi si
     * rifa' ogni volta che la libreria cambia senza doverci pensare.
     */
    fun saveIndex(silenzioso: Boolean = false) = viewModelScope.launch {
        if (storage.tree.value == null) {
            if (!silenzioso) _backupMsg.value = "Scegli prima una cartella."
            return@launch
        }
        backup.write().fold(
            onSuccess = { if (!silenzioso) _backupMsg.value = "Indice salvato: $it brani" },
            onFailure = { if (!silenzioso) _backupMsg.value = "Indice non salvato: ${it.message}" },
        )
    }

    /** Guarda cosa c'e' in una cartella, senza importare niente. */
    fun inspectFolder(uri: android.net.Uri) = viewModelScope.launch {
        _backupMsg.value = null
        backup.inspect(uri).fold(
            onSuccess = { _anteprima.value = it },
            onFailure = { e ->
                _backupMsg.value = when (val p = (e as? BackupStore.ImportException)?.problem) {
                    is ImportProblem.NonRiconosciuta ->
                        "Questa cartella non contiene un backup di SoSound."
                    is ImportProblem.VersioneFutura ->
                        "Il backup è stato scritto da una versione più nuova " +
                            "dell'app (formato ${p.trovata}). Aggiorna SoSound."
                    is ImportProblem.IndiceAlterato ->
                        "L'indice non corrisponde al suo contenuto: la copia " +
                            "potrebbe essersi interrotta a metà. Rifai il backup."
                    is ImportProblem.Vuoto -> "Backup vuoto: ${p.motivo}"
                    null -> "Non riesco a leggere questa cartella: ${e.message}"
                }
            },
        )
    }

    private val _progressoImport = MutableStateFlow<String?>(null)
    /** «Importo 12 di 47…»: un'attesa muta sembra un blocco. */
    val progressoImport: StateFlow<String?> = _progressoImport

    private val _importing = MutableStateFlow(false)
    val importingBackup: StateFlow<Boolean> = _importing

    fun confirmFolderImport(uri: android.net.Uri) = viewModelScope.launch {
        val p = _anteprima.value ?: return@launch
        _cartellaInAttesa.value = null
        _importing.value = true
        // Adottare la cartella prima di importare: se no i brani
        // entrerebbero puntando a indirizzi su cui perderemmo il permesso
        // al primo riavvio.
        runCatching { storage.adopt(uri) }
        controllaAnnidate()
        backup.import(uri, p) { fatti, totale ->
            _progressoImport.value = "Importo $fatti di $totale…"
        }.fold(
            onSuccess = { _backupMsg.value = "Importati $it brani e ${p.index.playlist.size} playlist" },
            onFailure = { _backupMsg.value = "Importazione non riuscita: ${it.message}" },
        )
        _importing.value = false
        _progressoImport.value = null
        _anteprima.value = null
    }

    // -------------------------------------------------------- impostazioni

    private val _engineMessage = MutableStateFlow<String?>(null)
    val engineMessage: StateFlow<String?> = _engineMessage

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating

    fun engineVersion(): String? = downloader.version()

    /**
     * Aggiorna yt-dlp. È il tasto che sistema quasi tutti i download che
     * smettono di funzionare, e per questo sta in prima pagina nelle
     * impostazioni invece che nascosto in fondo.
     */
    fun updateEngine() = viewModelScope.launch {
        _updating.value = true
        // Aggiornamento voluto dall'utente: azzera il freno automatico,
        // altrimenti il prossimo errore non riproverebbe per sei ore.
        queue.onManualUpdate()
        downloader.updateEngine()
            .onSuccess { _engineMessage.value = it }
            .onFailure { _engineMessage.value = "Aggiornamento non riuscito: ${it.message}" }
        _updating.value = false
    }

    fun clearEngineMessage() {
        _engineMessage.value = null
    }

    // -------------------------------------------- aggiornamento dell'app

    val appUpdateState: StateFlow<UpdateState> = appUpdateManager.state
    val appUpdateDisponibile: Boolean get() = appUpdateManager.disponibile
    val appCurrentVersion: String get() = appUpdateManager.currentVersion

    /** Il tasto «Controlla aggiornamenti» in Impostazioni. */
    fun checkForAppUpdate() = appUpdateManager.check(manual = true)

    /** Un solo tasto: scarica e installa, senza altri passaggi da
     *  cercare. Mostra comunque il dialogo di sistema di Android — quello
     *  non si puo' saltare. */
    fun downloadAndInstallAppUpdate() = appUpdateManager.downloadAndInstall()

    fun skipAppUpdate() = appUpdateManager.skipCurrent()

    /** Da richiamare tornando dalla schermata «installa da questa fonte». */
    fun retryAppInstall() = appUpdateManager.retryInstall()

    fun openInstallPermissionSettings() = appUpdateManager.requestInstallPermission()

    fun dismissAppUpdateError() = appUpdateManager.dismissError()

    // ------------------------------------------------ trasmissione

    private val castManager = com.sosound.app.cast.CastManager(app)
    val castStato: StateFlow<com.sosound.app.cast.StatoCast> get() = castManager.stato
    val castDispositivo: StateFlow<String?> get() = castManager.dispositivo

    fun interrompiCast() = castManager.interrompi()

    // --- DLNA: la fascia che non parla Cast (Samsung, LG, impianti) ---

    private val reteDlna = com.sosound.app.cast.dlna.DlnaNetwork(app)

    private val _dlnaTrovati =
        MutableStateFlow<List<com.sosound.app.cast.dlna.DlnaDevice>>(emptyList())
    val dlnaTrovati: StateFlow<List<com.sosound.app.cast.dlna.DlnaDevice>> = _dlnaTrovati

    private val _dlnaInCerca = MutableStateFlow(false)
    val dlnaInCerca: StateFlow<Boolean> = _dlnaInCerca

    private val _dlnaAttivo = MutableStateFlow<String?>(null)
    /** Il nome dell'apparecchio DLNA su cui si sta suonando. */
    val dlnaAttivo: StateFlow<String?> = _dlnaAttivo

    fun cercaDispositivi() = viewModelScope.launch {
        if (_dlnaInCerca.value) return@launch
        _dlnaInCerca.value = true
        _dlnaTrovati.value = runCatching { reteDlna.cerca() }.getOrDefault(emptyList())
        _dlnaInCerca.value = false
    }

    fun trasmettiA(device: com.sosound.app.cast.dlna.DlnaDevice) {
        com.sosound.app.playback.PlaybackService.attivo()?.passaADlna(device)
        _dlnaAttivo.value = device.nome
    }

    fun smettiDiTrasmettere() {
        com.sosound.app.playback.PlaybackService.attivo()?.tornaAlTelefonoDaFuori()
        _dlnaAttivo.value = null
    }

    // ------------------------------------------------- primo avvio

    private val prefs = app.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)

    private val _onboarding = MutableStateFlow(!prefs.getBoolean(KEY_VISTO, false))
    /** Se mostrare l'introduzione. Vera solo alla primissima apertura. */
    val showOnboarding: StateFlow<Boolean> = _onboarding

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_VISTO, true).apply()
        _onboarding.value = false
    }

    /** L'ultima schermata dell'introduzione porta dritti all'importazione. */
    private val _apriImport = MutableStateFlow(false)
    val importOpen: StateFlow<Boolean> = _apriImport

    /**
     * Vero quando si sta guardando qualcosa **dentro** una scheda:
     * una playlist aperta, un album, la pagina di un artista, l'import.
     *
     * Li' il trascinamento laterale va spento. Quelle pagine si sono
     * aperte da un tocco e si chiudono col tasto indietro: farle
     * scivolare via di lato porterebbe altrove senza che nessuno
     * l'abbia chiesto, e quello che si stava leggendo sparirebbe.
     */
    val dentroUnaSottoschermata: StateFlow<Boolean> =
        combine(
            openPlaylist, importOpen, openLocalAlbum,
            albumPage, artistPage, podcastPage, playlistPage,
        ) { valori -> valori.any { it != null && it != false } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun openImportFromOnboarding() { _apriImport.value = true; _goToLibrary.value = true }
    fun setImportOpen(v: Boolean) { _apriImport.value = v }

    private val _goToLibrary = MutableStateFlow(false)
    val goToLibrary: StateFlow<Boolean> = _goToLibrary
    fun libraryTabShown() { _goToLibrary.value = false }

    // ------------------------------------------------------------- interno

    init {
        // Un secondo osservatore accanto a quello del servizio: qui serve
        // solo a sapere se mostrare il tasto e quale nome scriverci.
        castManager.inizializza()

        // La coda di ieri torna al suo posto, ferma.
        player.connect {
            viewModelScope.launch {
                val salvata = com.sosound.app.playback.CodaSalvata.leggi(application)
                    ?: return@launch
                // I brani si rileggono dal database: quello che e' stato
                // cancellato nel frattempo semplicemente non c'e' piu',
                // e la coda si accorcia invece di puntare al vuoto.
                val brani = salvata.brani.mapNotNull { dao.byId(it) }
                if (brani.isEmpty()) return@launch
                val indice = com.sosound.app.playback.CodaSalvata.indiceDopoLaPotatura(
                    salvati = salvata.brani,
                    sopravvissuti = brani.map { it.videoId }.toSet(),
                    indice = salvata.indice,
                )
                player.ripristina(brani, indice, salvata.posizioneMs)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (player.state.value.isPlaying) player.tick()
            }
        }

        // I controlli prendono il colore dal brano in ascolto.
        // distinctUntilChanged perche' playerState cambia due volte al
        // secondo per la barra di posizione, e rileggere la copertina
        // ogni mezzo secondo si sentirebbe sulla batteria.
        viewModelScope.launch {
            player.state
                .map { it.current?.coverPath }
                .distinctUntilChanged()
                .collect { _accent.value = accentLoader.load(it) }
        }

        // Un brano toccato per essere ascoltato parte appena e' sceso.
        viewModelScope.launch {
            queue.readyToPlay.collect { player.play(listOf(it), 0) }
        }

        // I brani importati entrano nella playlist solo dopo essere
        // esistiti in libreria: prima la chiave esterna li rifiuta.
        viewModelScope.launch {
            queue.completed.collect { entity ->
                inAttesaDiPlaylist.remove(entity.videoId)?.let { playlistId ->
                    playlistDao.addTrack(playlistId, entity.videoId)
                }
            }
        }

        // L'indice segue la libreria, ma non una volta per notifica.
        //
        // Scaricando dieci brani di fila le notifiche arrivano a raffica.
        // Prima qui c'era un delay dentro il collect, che non e' la
        // stessa cosa: ritardava ogni scrittura invece di saltarne
        // nessuna, e le dieci partivano lo stesso, sfasate di poco.
        // debounce aspetta che la raffica finisca e ne scrive una.
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            library.debounce(3000).collect {
                // Atteso e non lanciato: saveIndex apriva una coroutine
                // per conto suo, quindi due scritture potevano trovarsi
                // dentro la cartella insieme e creare due indici.
                backup.write()
            }
        }
    }

    private companion object {
        const val KEY_VISTO = "introduzione_vista"
    }

    override fun onCleared() {
        reteDlna.chiudi()
        castManager.rilascia()
        player.release()
        catalog.close()
        super.onCleared()
    }
}
