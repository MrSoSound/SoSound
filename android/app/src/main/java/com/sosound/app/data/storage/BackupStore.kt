package com.sosound.app.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sosound.app.data.library.PlaylistDao
import com.sosound.app.data.library.TrackDao
import com.sosound.app.data.library.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Scrive e rilegge l'indice dentro la cartella scelta.
 *
 * È quello che trasforma una cartella di file audio in un backup: senza,
 * reinstallando l'app si ritroverebbero i brani ma non gli album, non le
 * playlist, non l'ordine.
 */
class BackupStore(
    private val context: Context,
    private val storage: MusicStorage,
    private val tracks: TrackDao,
    private val playlists: PlaylistDao,
) {

    // ------------------------------------------------------------ scrittura

    /**
     * Riscrive l'indice con lo stato attuale della libreria.
     *
     * Non tocca i file: quelli sono gia' al loro posto. Costa una lettura
     * del database e una scrittura di pochi kilobyte, quindi si puo'
     * rifare a ogni cambiamento senza pensarci.
     */
    /**
     * Una scrittura per volta.
     *
     * L'indice segue la libreria, e la libreria cambia a raffica mentre
     * si scarica. Due scritture sovrapposte trovano tutte e due la
     * cartella senza indice e lo creano tutte e due: e' il modo in cui
     * nascono due file identici, stessa dimensione e stessa ora.
     */
    private val unaAllaVolta = kotlinx.coroutines.sync.Mutex()

    suspend fun write(): Result<Int> = withContext(Dispatchers.IO) {
        unaAllaVolta.withLock {
        runCatching {
            // Senza cartella l'indice si scrive lo stesso, solo nello
            // spazio privato.
            //
            // E' proprio il caso in cui serve di piu': li' i file se ne
            // vanno con l'app, e sapere COSA c'era e' quasi tutto — i
            // brani si riscaricano, una playlist ricostruita a mano no.
            // Prima qui si lanciava un errore e non restava niente.
            val tree = storage.tree.value
            val root = tree?.let {
                DocumentFile.fromTreeUri(context, it)?.findFile(MusicStorage.RADICE)
            }

            // Le impronte gia' calcolate si riusano.
            //
            // Calcolarne una vuol dire leggere il file fino in fondo, e
            // su Drive leggere vuol dire scaricare. L'indice si riscrive
            // a ogni cambiamento della libreria: senza questo, ogni
            // brano scaricato faceva riscaricare tutti gli altri per
            // ricalcolare impronte identiche a quelle di un minuto
            // prima.
            //
            // Si riusa quella vecchia quando il file non e' cambiato, e
            // la dimensione e' il testimone: e' nei metadati e non costa
            // niente.
            val vecchie: Map<String, Pair<Long, String>> = runCatching {
                val esistente = (root ?: return@runCatching emptyMap()).listFiles()
                    .firstOrNull { it.name == BackupIndex.NOME_FILE }
                    ?: return@runCatching emptyMap()
                val testo = context.contentResolver.openInputStream(esistente.uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@runCatching emptyMap()
                BackupFormat.json.decodeFromString(BackupIndex.serializer(), testo)
                    .brani.mapNotNull { b -> b.frammento?.let { b.videoId to (b.byte to it) } }
                    .toMap()
            }.getOrDefault(emptyMap())

            val brani = tracks.all()
                // Quelli che stanno nella cartella, piu' quelli che non
                // hanno un file affatto.
                //
                // I secondi si scrivono perche' fanno parte della
                // libreria e delle playlist: tacerli farebbe arrivare
                // una playlist monca su un telefono nuovo, senza che
                // niente spieghi dove sono finiti tre brani su dieci.
                // Un brano nello SPAZIO PRIVATO invece resta fuori: li'
                // il file c'e' ma non e' nella cartella, e prometterlo
                // sarebbe promettere qualcosa che il backup non contiene.
                // Solo la libreria vera: quello che si e' messo in una
                // playlist o si e' scelto di tenere. Un brano ascoltato
                // di passaggio sta nella cache di questo telefono e non
                // ha niente da fare in una cartella che descrive un
                // backup.
                .filter { it.salvato }
                .map { t ->
                    // Un file che non sta nella cartella scelta, per un
                    // indice, e' come non averlo: nessun altro telefono
                    // potra' raggiungerlo. Si scrive il brano senza
                    // percorso, e al ripristino si riscarica.
                    if (!t.haFile || !t.path.startsWith("content://") || root == null) {
                        // Niente percorso e niente impronta: non c'e' un
                        // file di cui dirlo.
                        return@map BackupFormat.toBackup(t, file = "", copertina = null, frammento = null)
                    }
                    val nota = vecchie[t.videoId]
                    val frammento =
                        if (nota != null && t.sizeBytes > 0 && nota.first == t.sizeBytes) nota.second
                        else fingerprintOf(Uri.parse(t.path))
                    BackupFormat.toBackup(
                        t = t,
                        file = relativePath(root!!, t.path) ?: t.path,
                        copertina = t.coverPath?.let { "${MusicStorage.COPERTINE}/${t.videoId}.jpg" },
                        frammento = frammento,
                    )
                }

            val liste = playlists.allWithTracks().map { (p, ids) ->
                BackupFormat.toPlaylist(p, ids)
            }

            val indice = BackupIndex(
                creatoIl = System.currentTimeMillis(),
                brani = brani,
                playlist = liste,
            ).let { it.copy(impronta = BackupFormat.fingerprint(it)) }

            val testo = BackupFormat.json.encodeToString(BackupIndex.serializer(), indice)

            // Si riusa il file che c'e' invece di cancellarlo e rifarlo.
            //
            // Google Drive permette due file con lo stesso nome nella
            // stessa cartella: createFile non sovrascrive, aggiunge. E
            // findFile torna solo il primo, quindi il giro
            // «cancella-e-ricrea» su una cartella che ne ha gia' due ne
            // cancella uno e ne crea un altro — restano due per sempre.
            copiaDiScorta(testo)
            if (root == null) return@runCatching brani.size

            val esistenti = root.listFiles().filter { it.name == BackupIndex.NOME_FILE }
            val doc = esistenti.firstOrNull()
                ?: root.createFile("application/json", BackupIndex.NOME_FILE)
                ?: throw IllegalStateException("non riesco a scrivere l'indice")

            // Gli eventuali doppioni gia' presenti se ne vanno qui: la
            // cartella si ripulisce da sola alla prima scrittura.
            esistenti.drop(1).forEach { runCatching { it.delete() } }

            // "wt" e non "w": senza la t il file viene sovrascritto ma
            // non accorciato, e un indice piu' corto del precedente
            // lascia in coda i byte vecchi — JSON valido fino a meta' e
            // spazzatura dopo.
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
                it.write(testo.toByteArray())
            } ?: throw IllegalStateException("non riesco a scrivere l'indice")

            brani.size
        }
        }
    }

    /**
     * Una copia dell'indice nello spazio privato dell'app.
     *
     * ## A cosa serve davvero
     *
     * A non perdere l'elenco quando non c'e' una cartella scelta. Senza
     * cartella i file stanno nello spazio privato e se ne vanno con
     * l'app — ma sapere **cosa** c'era e' quasi tutto: i brani si
     * riscaricano, le playlist ricostruite a mano no.
     *
     * ## Cosa non fa
     *
     * Non sopravvive alla disinstallazione, perche' niente che scriviamo
     * noi ci sopravvive. Sopravvive invece a una cache svuotata, a un
     * riavvio e a un aggiornamento dell'app — e alla copia automatica di
     * Android, che e' l'unica strada che porta un file oltre la
     * disinstallazione senza chiedere una cartella all'utente.
     */
    private fun copiaDiScorta(testo: String) = runCatching {
        File(context.filesDir, BackupIndex.NOME_FILE).writeText(testo)
    }

    /** L'ultimo indice conosciuto, anche senza cartella. */
    fun copiaDiScorta(): File? =
        File(context.filesDir, BackupIndex.NOME_FILE).takeIf { it.length() > 0 }

    // ------------------------------------------------------------- lettura

    /**
     * Legge una cartella e dice cosa contiene, senza importare niente.
     *
     * Separato dall'importazione di proposito: l'utente deve poter
     * vedere quanti brani ci sono e cosa non torna prima di far entrare
     * qualcosa nella sua libreria.
     */
    suspend fun inspect(tree: Uri): Result<ImportPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val albero = DocumentFile.fromTreeUri(context, tree)
                ?: throw ImportException(ImportProblem.NonRiconosciuta)

            // Si accetta sia la cartella che contiene SoSound sia la
            // cartella SoSound stessa: chi sceglie a mano fa l'uno o
            // l'altro, e distinguere sarebbe una trappola inutile.
            // La forma si pretende: o la cartella contiene SoSound/, o
            // e' lei la cartella SoSound. Niente altro viene scambiato
            // per una nostra libreria — una cartella qualsiasi scelta
            // come destinazione e' una destinazione, non un backup da
            // importare.
            val root = albero.findFile(MusicStorage.RADICE)?.takeIf { it.isDirectory }
                ?: albero.takeIf {
                    it.findFile(BackupIndex.NOME_FILE) != null ||
                        it.findFile(MusicStorage.BRANI)?.isDirectory == true
                }
                ?: throw ImportException(ImportProblem.NonRiconosciuta)

            // Senza indice non si rinuncia: i nomi dei file bastano a
            // rimettere in piedi i brani. E' la differenza fra «non e'
            // una cartella di SoSound» e una cartella piena di musica
            // che torna al suo posto.
            val file = root.findFile(BackupIndex.NOME_FILE)
                ?: return@runCatching scansiona(root).also {
                    if (it.validi.isEmpty()) throw ImportException(ImportProblem.NonRiconosciuta)
                }

            val testo = context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@runCatching scansiona(root)

            val indice = runCatching {
                BackupFormat.json.decodeFromString(BackupIndex.serializer(), testo)
            }.getOrNull() ?: return@runCatching scansiona(root)

            // Un indice che si legge si usa, anche se l'impronta non torna.
            //
            // Prima qui si ripiegava sulla ricostruzione dai nomi dei
            // file, che ritrova i brani e **perde le playlist**: nessun
            // nome di file ricorda a quale elenco apparteneva un brano.
            // E siccome l'impronta cambiava a ogni campo aggiunto al
            // formato, quel ripiego scattava su ogni cartella scritta da
            // una versione precedente — cioe' sempre.
            //
            // Adesso si ripiega solo se l'indice non e' proprio
            // utilizzabile. Se lo e', si usa e si dice che non era
            // verificabile: e' un'informazione, non un motivo per
            // buttare via il lavoro di qualcuno.
            val problema = BackupFormat.validate(indice)
            if (!BackupFormat.usabile(indice)) {
                val recuperato = scansiona(root)
                if (recuperato.validi.isNotEmpty()) return@runCatching recuperato
                throw ImportException(problema ?: ImportProblem.NonRiconosciuta)
            }

            val validi = mutableListOf<BackupTrack>()
            val mancanti = mutableListOf<BackupTrack>()
            val corrotti = mutableListOf<BackupTrack>()
            val sospetti = mutableListOf<BackupTrack>()

            val strada = Strada(root)
            for (b in indice.brani) {
                if (!b.conFile) {
                    // Non manca: non c'era. Rientra come riferimento, e
                    // si ascolta dalla rete come prima di partire.
                    validi += b
                    continue
                }
                val doc = strada.file(b.file)
                when {
                    doc == null -> mancanti += b
                    // La dimensione e' il controllo che costa zero e
                    // riconosce il guasto piu' comune: un file copiato a
                    // meta' da una sincronizzazione interrotta.
                    b.byte > 0 && doc.length() != b.byte -> corrotti += b
                    // L'impronta NON si verifica qui, ed e' il motivo
                    // per cui l'import era lento.
                    //
                    // Calcolarla vuol dire leggere il file fino in fondo
                    // per arrivare agli ultimi 64 KB — e su Drive
                    // leggere un file vuol dire scaricarlo. Verificare
                    // una libreria di cinquecento brani significava
                    // scaricarla tutta, per poi importarla comunque:
                    // un'impronta diversa non escludeva il brano.
                    //
                    // La dimensione basta, si legge dai metadati e costa
                    // zero: riconosce il guasto vero, il file copiato a
                    // meta'. L'impronta resta nell'indice e serve dov'e'
                    // utile — a dire se un file e' cambiato — non a
                    // sbarrare la strada al recupero.
                    else -> validi += b
                }
            }

            // Se l'indice non produce niente di utilizzabile ma i file
            // ci sono, si ricomincia dai nomi — tenendosi pero' le
            // playlist che l'indice conosceva.
            //
            // I nomi dei file dicono quali brani ci sono; solo l'indice
            // sa come erano raggruppati. Ripartire da zero butterebbe
            // via proprio la parte che non si puo' ricostruire.
            if (validi.isEmpty()) {
                val daiNomi = scansiona(root)
                if (daiNomi.validi.isNotEmpty()) {
                    return@runCatching daiNomi.copy(
                        index = daiNomi.index.copy(playlist = indice.playlist),
                    )
                }
            }

            ImportPreview(
                indice, validi, mancanti, corrotti,
                sospetti = sospetti,
                nonVerificato = problema != null,
            )
        }
    }


    /**
     * Ricostruisce i brani dai nomi dei file, dentro `Brani/`.
     *
     * ## Solo li'
     *
     * Per un po' questa funzione ha frugato ricorsivamente in tutta la
     * cartella scelta, per far tornare indietro la musica comunque fosse
     * disposta. Era troppo: su una cartella di Drive vuol dire chiedere
     * l'elenco di ogni sottocartella dell'albero, e una cartella scelta
     * per sbaglio veniva scambiata per nostra. La forma giusta e' una, e
     * si pretende: `SoSound/Brani/<Artista>/<file>`.
     *
     * Quello che resta e' il recupero che serve davvero — l'indice puo'
     * mancare, i file no — dentro una struttura che sappiamo essere la
     * nostra. Le playlist non si recuperano: stanno solo nell'indice.
     */
    private fun scansiona(root: DocumentFile): ImportPreview {
        val brani = root.findFile(MusicStorage.BRANI)?.takeIf { it.isDirectory }
            ?: return ImportPreview(BackupIndex(), emptyList(), emptyList(), emptyList(), daIndice = false)

        // Le copertine si elencano una volta sola: chiederle a SAF una
        // per brano vuol dire una chiamata al sistema per brano.
        val copertine = root.findFile(MusicStorage.COPERTINE)
            ?.takeIf { it.isDirectory }
            ?.listFiles().orEmpty()
            .mapNotNull { f -> f.name?.let { it.substringBeforeLast('.') to it } }
            .toMap()

        val trovati = LinkedHashMap<String, BackupTrack>()

        fun raccogli(cartella: DocumentFile, relativo: String, profondita: Int) {
            // Artista/file e' un livello; due bastano e avanzano.
            if (profondita > 2) return
            for (f in cartella.listFiles()) {
                val nome = f.name ?: continue
                val percorso = "$relativo/$nome"
                if (f.isDirectory) { raccogli(f, percorso, profondita + 1); continue }
                val letto = MusicStorage.leggiNome(nome) ?: continue
                trovati.getOrPut(letto.videoId) {
                    BackupTrack(
                        videoId = letto.videoId,
                        titolo = letto.titolo,
                        artista = letto.artista,
                        album = null,
                        // La durata NON si legge qui.
                        //
                        // Leggerla vuol dire aprire il file, e su Drive
                        // aprire un file vuol dire scaricarlo: su
                        // cinquecento brani l'import scaricherebbe
                        // l'intera libreria per sapere quanto durano.
                        // La si impara alla prima riproduzione.
                        durata = null,
                        file = percorso,
                        copertina = copertine[letto.videoId]
                            ?.let { "${MusicStorage.COPERTINE}/$it" },
                        byte = f.length(),
                        frammento = null,
                        aggiuntoIl = f.lastModified(),
                    )
                }
            }
        }

        raccogli(brani, MusicStorage.BRANI, 0)

        val lista = trovati.values.toList()
        return ImportPreview(
            index = BackupIndex(brani = lista, playlist = emptyList()),
            validi = lista,
            mancanti = emptyList(),
            corrotti = emptyList(),
            daIndice = false,
        )
    }


    /**
     * La durata, letta dal file.
     *
     * Nel nome non c'e', e senza, ogni brano recuperato mostrerebbe
     * 0:00 e la barra di avanzamento non saprebbe dove fermarsi. Costa
     * un'apertura per file, una volta sola.
     */
    private fun durataDi(uri: Uri): Int? = runCatching {
        // release() e non use(): MediaMetadataRetriever e' diventato
        // chiudibile solo da Android 10, e su un telefono piu' vecchio
        // use() compila e poi va in errore quando prova a chiuderlo.
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { (it / 1000).toInt() }
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    /**
     * Importa quello che e' risultato valido.
     *
     * I file restano dove sono: la libreria ci punta con l'indirizzo
     * della cartella. Copiarli vorrebbe dire raddoppiare lo spazio
     * occupato per niente.
     */
    suspend fun import(
        tree: Uri,
        preview: ImportPreview,
        /** Chiamata a ogni brano, per far vedere che si sta muovendo. */
        avanzamento: (Int, Int) -> Unit = { _, _ -> },
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val albero = DocumentFile.fromTreeUri(context, tree)!!
                val root = albero.findFile(MusicStorage.RADICE)?.takeIf { it.isDirectory } ?: albero

                val strada = Strada(root)
                var entrati = 0
                for (b in preview.validi) {
                    if (!b.conFile) {
                        tracks.upsert(
                            TrackEntity(
                                videoId = b.videoId, title = b.titolo, artist = b.artista,
                                album = b.album, durationSeconds = b.durata,
                                path = "", coverPath = null, sizeBytes = 0, salvato = true,
                                addedAt = b.aggiuntoIl.takeIf { it > 0 } ?: System.currentTimeMillis(),
                                showId = b.showId,
                            )
                        )
                        entrati++
                        avanzamento(entrati, preview.validi.size)
                        continue
                    }
                    val doc = strada.file(b.file) ?: continue
                    // La copertina si riporta nello spazio privato: e' li'
                    // che l'app la cerca, e leggerla dalla cartella a ogni
                    // disegno di lista sarebbe lento.
                    val copertina = b.copertina
                        ?.let { strada.file(it) }
                        ?.let { salvaCopertinaLocale(b.videoId, it.uri) }

                    tracks.upsert(
                        TrackEntity(
                            videoId = b.videoId,
                            title = b.titolo,
                            artist = b.artista,
                            album = b.album,
                            durationSeconds = b.durata,
                            path = doc.uri.toString(),
                            coverPath = copertina,
                            sizeBytes = b.byte,
                            addedAt = b.aggiuntoIl.takeIf { it > 0 } ?: System.currentTimeMillis(),
                            showId = b.showId,
                        )
                    )
                    entrati++
                    avanzamento(entrati, preview.validi.size)
                }

                // Le playlist dopo i brani: prima, la chiave esterna le
                // rifiuterebbe una per una.
                val presenti = tracks.all().map { it.videoId }.toSet()
                for (p in preview.index.playlist) {
                    val dentro = p.brani.filter { it in presenti }
                    if (dentro.isEmpty()) continue
                    val id = playlists.insert(
                        com.sosound.app.data.library.PlaylistEntity(
                            name = p.nome,
                            createdAt = p.creataIl.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        )
                    )
                    dentro.forEach { playlists.addTrack(id, it) }
                }
                entrati
            }
        }


    /**
     * Racconta cosa l'app vede dentro una cartella.
     *
     * Esiste perche' l'import puo' fallire per ragioni che da fuori sono
     * indistinguibili: la cartella sbagliata, i file con un nome che non
     * riconosciamo, un indice illeggibile, un permesso che non c'e'. Il
     * sintomo e' sempre lo stesso — «non importa niente» — e senza
     * questo si va a tentativi.
     *
     * Elenca anche i file NON riconosciuti, che sono la risposta piu'
     * utile di tutte: se sono musica ma non li riconosciamo, il difetto
     * e' nostro ed e' li' che si vede.
     */
    suspend fun diagnostica(
        tree: Uri,
        /** L'esito gia' calcolato, se c'e': rifarlo costerebbe un secondo giro. */
        gia: ImportPreview? = null,
    ): String = withContext(Dispatchers.IO) {
        val r = StringBuilder()
        fun riga(s: String = "") = r.append(s).append('\n')

        val albero = DocumentFile.fromTreeUri(context, tree)
        if (albero == null) {
            riga("La cartella non si apre: il permesso non è arrivato.")
            return@withContext r.toString()
        }

        riga("Cartella scelta: ${albero.name ?: "(senza nome)"}")
        riga("Leggibile: ${albero.canRead()}   Esiste: ${albero.exists()}")
        riga()

        val figli = runCatching { albero.listFiles().toList() }.getOrElse {
            riga("Non riesco a elencarne il contenuto: ${it.message}")
            return@withContext r.toString()
        }
        riga("Contiene ${figli.size} elementi:")
        figli.take(12).forEach {
            riga("  ${if (it.isDirectory) "[cartella]" else "[file]    "} ${it.name}")
        }
        if (figli.size > 12) riga("  … e altri ${figli.size - 12}")
        riga()

        val root = albero.findFile(MusicStorage.RADICE)?.takeIf { it.isDirectory } ?: albero
        riga("Parto da: ${root.name ?: "(la cartella scelta)"}")

        // Il giro vero, con il conto di cosa si riconosce e cosa no.
        var riconosciuti = 0
        val esempiSi = mutableListOf<String>()
        val esempiNo = mutableListOf<String>()
        var cartelle = 0

        fun gira(d: DocumentFile, profondita: Int) {
            if (profondita > 6) return
            for (f in runCatching { d.listFiles() }.getOrDefault(emptyArray())) {
                val nome = f.name ?: continue
                if (f.isDirectory) { cartelle++; gira(f, profondita + 1); continue }
                if (MusicStorage.leggiNome(nome) != null) {
                    riconosciuti++
                    if (esempiSi.size < 2) esempiSi += nome
                } else if (esempiNo.size < 6 && nome != BackupIndex.NOME_FILE) {
                    esempiNo += nome
                }
            }
        }
        gira(root, 0)

        riga("Sottocartelle esplorate: $cartelle")
        riga("Brani riconosciuti dal nome: $riconosciuti")
        esempiSi.forEach { riga("  sì: $it") }
        if (esempiNo.isNotEmpty()) {
            riga("File non riconosciuti (i primi ${esempiNo.size}):")
            esempiNo.forEach { riga("  no: $it") }
            riga("  (un brano nostro si chiama «Artista - Titolo [identificativo].m4a»)")
        }
        riga()

        val indice = root.findFile(BackupIndex.NOME_FILE)
        if (indice == null) {
            riga("Indice ${BackupIndex.NOME_FILE}: non c'è.")
        } else {
            riga("Indice ${BackupIndex.NOME_FILE}: ${indice.length()} byte")
            val testo = runCatching {
                context.contentResolver.openInputStream(indice.uri)
                    ?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (testo == null) riga("  non si riesce a leggerlo")
            else {
                val letto = runCatching {
                    BackupFormat.json.decodeFromString(BackupIndex.serializer(), testo)
                }
                letto.fold(
                    onSuccess = { i ->
                        riga("  si legge: ${i.brani.size} brani, ${i.playlist.size} playlist")
                        riga("  verifica: ${BackupFormat.validate(i)?.toString() ?: "in ordine"}")
                    },
                    onFailure = { riga("  non si interpreta: ${it.message?.take(120)}") },
                )
            }
        }
        riga()

        riga("Esito finale:")
        if (gia != null) {
            riga("  ${gia.validi.size} importabili, ${gia.mancanti.size} senza file, " +
                "${gia.corrotti.size} di dimensione diversa")
            riga("  origine: ${if (gia.daIndice) "indice" else "nomi dei file"}")
            return@withContext r.toString()
        }
        inspect(tree).fold(
            onSuccess = {
                riga("  ${it.validi.size} importabili, ${it.mancanti.size} senza file, " +
                    "${it.corrotti.size} di dimensione diversa")
                riga("  origine: ${if (it.daIndice) "indice" else "nomi dei file"}")
            },
            onFailure = { riga("  fallito: ${it::class.simpleName} — ${it.message}") },
        )

        r.toString()
    }

    // ------------------------------------------------------------ dettagli

    private fun salvaCopertinaLocale(videoId: String, uri: Uri): String? = runCatching {
        val out = File(storage.coversDir, "$videoId.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.absolutePath.takeIf { out.length() > 0 }
    }.getOrNull()

    /** Segue un percorso relativo dentro la cartella. */
    /**
     * Segue un percorso relativo dentro la cartella, ricordandosi la strada.
     *
     * `findFile` non cerca un nome: chiede al sistema l'elenco completo
     * della cartella e poi ci scorre dentro. Su Drive quell'elenco
     * arriva dalla rete. Risolvere `Brani/Tizio/brano.m4a` per ogni
     * brano vuol dire richiedere l'elenco di `Brani/` — che ha una voce
     * per artista — una volta per brano, e poi buttarlo via.
     *
     * Qui le cartelle gia' aperte restano aperte per il tempo
     * dell'operazione: su una libreria di duecento brani sono centinaia
     * di richieste di rete che non si fanno.
     */
    private class Strada(private val root: DocumentFile) {
        private val cartelle = HashMap<String, DocumentFile?>()

        fun cartella(percorso: String): DocumentFile? = cartelle.getOrPut(percorso) {
            if (percorso.isEmpty()) return@getOrPut root
            val taglio = percorso.lastIndexOf('/')
            val padre = if (taglio < 0) cartella("") else cartella(percorso.substring(0, taglio))
            val nome = if (taglio < 0) percorso else percorso.substring(taglio + 1)
            padre?.findFile(nome)?.takeIf { it.isDirectory }
        }

        fun file(percorso: String): DocumentFile? {
            val pezzi = percorso.split('/').filter { it.isNotBlank() }
            if (pezzi.isEmpty()) return null
            val dir = cartella(pezzi.dropLast(1).joinToString("/")) ?: return null
            return dir.findFile(pezzi.last())?.takeIf { it.isFile }
        }
    }

    private fun resolve(root: DocumentFile, path: String): DocumentFile? =
        Strada(root).file(path)

    /** Il percorso di un file rispetto alla cartella SoSound. */
    private fun relativePath(root: DocumentFile, uri: String): String? = runCatching {
        val doc = DocumentFile.fromSingleUri(context, Uri.parse(uri)) ?: return null
        val nome = doc.name ?: return null
        // DocumentFile non offre il percorso: si ricostruisce cercando il
        // file nella struttura che sappiamo di aver creato noi.
        val brani = root.findFile(MusicStorage.BRANI) ?: return null
        brani.listFiles().forEach { cartella ->
            if (cartella.isDirectory && cartella.findFile(nome) != null) {
                return "${MusicStorage.BRANI}/${cartella.name}/$nome"
            }
        }
        if (brani.findFile(nome) != null) "${MusicStorage.BRANI}/$nome" else null
    }.getOrNull()

    /**
     * Impronta parziale: dimensione, primi e ultimi 64 KB.
     *
     * Leggere l'intero file a ogni verifica vorrebbe dire scorrere
     * gigabyte; questa costa qualche millisecondo e riconosce comunque i
     * due guasti veri — il file troncato e il file sostituito.
     */
    private fun fingerprintOf(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { Impronta.di(it) }
    }.getOrNull()

    class ImportException(val problem: ImportProblem) : Exception(problem.toString())

    private companion object {
        const val FRAMMENTO = 64 * 1024
    }
}
