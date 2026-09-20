package com.sosound.app.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sosound.app.data.library.TrackEntity
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dove finiscono i file audio.
 *
 * Due possibilita':
 *
 *  - **lo spazio privato dell'app** (di serie): nessun permesso, nessuna
 *    configurazione, ma i file li vede solo SoSound e spariscono
 *    disinstallandola;
 *  - **una cartella scelta dall'utente**, via Storage Access Framework:
 *    i file restano anche dopo la disinstallazione, si vedono dagli altri
 *    lettori, e — il motivo per cui questa funzione esiste — possono
 *    stare dentro una cartella che un'app di sincronizzazione copia
 *    altrove.
 *
 * Le copertine restano sempre nello spazio privato anche quando l'audio
 * va fuori: sono file di servizio, e riempire di `.jpg` la cartella
 * musicale di qualcuno e' un modo di rovinargliela.
 */
class MusicStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences("archivio", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _tree = MutableStateFlow(readTree())
    /** La cartella scelta, oppure null se si usa quella di serie. */
    val tree: StateFlow<Uri?> = _tree

    private fun readTree(): Uri? =
        prefs.getString(KEY_TREE, null)?.let(Uri::parse)?.takeIf { stillUsable(it) }

    /**
     * Il permesso su una cartella si puo' perdere: l'utente lo revoca
     * dalle impostazioni di sistema, o la scheda SD viene tolta. Meglio
     * accorgersene qui che al primo download fallito.
     */
    private fun stillUsable(uri: Uri): Boolean = runCatching {
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        } && DocumentFile.fromTreeUri(context, uri)?.canWrite() == true
    }.getOrDefault(false)

    /** Prende il permesso duraturo sulla cartella e la memorizza. */
    fun adopt(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
        _tree.value = uri
    }

    // ------------------------------------------------- tenere o non tenere

    private val _sempreOffline = MutableStateFlow(
        prefs.getBoolean(KEY_SEMPRE_OFFLINE, false)
    )

    /**
     * Se tutto quello che finisce in una playlist va tenuto senza rete.
     *
     * Spento, una playlist e' un elenco: i brani si sentono, e restano
     * in cache finche' c'e' posto. Acceso, ogni brano aggiunto viene
     * scaricato per davvero nella cartella scelta — piu' spazio, ma la
     * playlist funziona in aereo.
     */
    val sempreOffline: StateFlow<Boolean> = _sempreOffline

    private val _sentiNotifiche = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICHE, true)
    )

    /**
     * Se le notifiche devono farsi sentire mentre suona la musica.
     *
     * Acceso, la musica cala per un istante e il bip passa sopra.
     * Spento, non succede niente: la notifica arriva sullo schermo e il
     * suo suono si perde sotto la canzone.
     */
    val sentiNotifiche: StateFlow<Boolean> = _sentiNotifiche

    fun impostaSentiNotifiche(valore: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICHE, valore).apply()
        _sentiNotifiche.value = valore
    }

    fun impostaSempreOffline(valore: Boolean) {
        prefs.edit().putBoolean(KEY_SEMPRE_OFFLINE, valore).apply()
        _sempreOffline.value = valore
    }

    /**
     * Vero se quello che si salva sparisce disinstallando l'app.
     *
     * Senza una cartella scelta i file stanno nello spazio privato: non
     * si cancellano da soli, ma se ne vanno con l'app e non si possono
     * riprendere. Va detto prima, non dopo.
     */
    val destinazioneFragile: StateFlow<Boolean> = _tree
        .map { it == null }
        .stateIn(scope, SharingStarted.Eagerly, _tree.value == null)

    /** Torna allo spazio privato dell'app. */
    fun useDefault() {
        _tree.value?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove(KEY_TREE).apply()
        _tree.value = null
    }

    /** Nome leggibile della destinazione corrente. */
    fun describe(): String {
        val uri = _tree.value ?: return "Spazio privato dell'app"
        return runCatching { DocumentFile.fromTreeUri(context, uri)?.name }
            .getOrNull() ?: "Cartella scelta"
    }

    /**
     * Scarica una copertina dalla rete e la tiene nello spazio privato.
     *
     * Serve ai brani che si ascoltano senza scaricarli: l'audio arriva
     * dal flusso, ma l'immagine la vuole la schermata di blocco, e
     * chiederla alla rete a ogni disegno di lista sarebbe assurdo.
     */
    suspend fun salvaCopertinaDaRete(videoId: String, url: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val out = File(coversDir, "$videoId.jpg")
                if (out.length() > 0) return@runCatching out.absolutePath
                java.net.URL(url).openStream().use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                out.absolutePath.takeIf { out.length() > 0 }
            }.getOrNull()
        }

    // ------------------------------------------------------------ scrittura

    private val privateDir: File
        get() = File(context.filesDir, "musica").apply { mkdirs() }

    val coversDir: File
        get() = File(context.filesDir, "copertine").apply { mkdirs() }

    /**
     * La cartella SoSound dentro l'albero scelto, creandola se manca.
     *
     * Tutto sta li' dentro invece che sparso nella cartella dell'utente:
     * cosi' si puo' scegliere una cartella qualsiasi — anche una gia'
     * piena di altra roba — senza mescolarcisi, e soprattutto si puo'
     * riconoscere una cartella SoSound quando la si reimporta.
     */
    /**
     * La cartella SoSound dentro la destinazione scelta.
     *
     * ## Il controllo che mancava
     *
     * Se la cartella scelta **e' gia'** una cartella SoSound, si usa
     * quella e non se ne crea un'altra dentro. Senza questo controllo
     * chi sceglieva la propria cartella SoSound come destinazione si
     * ritrovava `SoSound/SoSound`, con la musica divisa fra le due e
     * l'indice in una sola — ed e' esattamente cosi' che le playlist
     * finiscono in un posto che nessuno guarda piu'.
     *
     * Il riconoscimento non e' solo il nome: una cartella si chiama
     * SoSound se si chiama cosi', oppure se dentro ha quello che ci
     * mettiamo noi.
     */
    private fun radice(tree: Uri): DocumentFile? {
        val albero = DocumentFile.fromTreeUri(context, tree) ?: return null
        if (eGiaRadice(albero)) return albero
        return albero.findFile(RADICE)?.takeIf { it.isDirectory }
            ?: albero.createDirectory(RADICE)
    }

    private fun eGiaRadice(d: DocumentFile): Boolean =
        d.name == RADICE ||
            d.findFile(BRANI)?.isDirectory == true ||
            d.findFile("sosound.json") != null


    // ------------------------------------------- le cartelle annidate

    /** Cosa e' stato trovato, o sistemato. */
    data class Annidate(val quante: Int, val spostati: Int = 0, val falliti: Int = 0)

    /**
     * Cerca una cartella SoSound dentro un'altra cartella SoSound.
     *
     * Nasceva quando si sceglieva la propria cartella SoSound come
     * destinazione: l'app ne creava una dentro, e da quel momento la
     * musica stava divisa fra le due con l'indice in una sola. Adesso
     * non succede piu', ma chi ce l'ha gia' se la ritrova.
     */
    suspend fun trovaAnnidate(): Annidate = withContext(Dispatchers.IO) {
        val root = _tree.value?.let { radice(it) } ?: return@withContext Annidate(0)
        val dentro = root.findFile(RADICE)?.takeIf { it.isDirectory }
            ?: return@withContext Annidate(0)
        Annidate(quante = 1, spostati = conta(dentro))
    }

    private fun conta(d: DocumentFile): Int =
        d.listFiles().sumOf { if (it.isDirectory) conta(it) else 1 }

    /**
     * Porta il contenuto della cartella interna in quella esterna.
     *
     * ## Come sposta
     *
     * Prova prima `moveDocument`, che su un provider che lo sostiene
     * non copia un byte. Google Drive spesso non lo sostiene, e allora
     * si ripiega su copia-e-cancella — piu' lento, ma l'alternativa e'
     * lasciare le cose com'erano.
     *
     * ## Cosa fa con i doppioni
     *
     * Li tiene tutti e due, rinominando quello che arriva. Decidere
     * quale sia «quello buono» richiederebbe di aprirli, e un file
     * cancellato per sbaglio non torna indietro. Il doppione si vede e
     * si cancella a mano; un brano sparito no.
     */
    suspend fun unisciAnnidate(avanzamento: (Int, Int) -> Unit = { _, _ -> }): Annidate =
        withContext(Dispatchers.IO) {
            val root = _tree.value?.let { radice(it) } ?: return@withContext Annidate(0)
            val dentro = root.findFile(RADICE)?.takeIf { it.isDirectory }
                ?: return@withContext Annidate(0)

            val totale = conta(dentro)
            var fatti = 0
            var falliti = 0

            fun porta(da: DocumentFile, a: DocumentFile) {
                for (f in da.listFiles()) {
                    val nome = f.name ?: continue
                    if (f.isDirectory) {
                        val sotto = a.findFile(nome)?.takeIf { it.isDirectory }
                            ?: a.createDirectory(nome)
                        if (sotto == null) { falliti += conta(f); continue }
                        porta(f, sotto)
                        // La cartella svuotata se ne va; se e' rimasto
                        // qualcosa resta, e si vede.
                        if (f.listFiles().isEmpty()) runCatching { f.delete() }
                    } else {
                        val destinazione = if (a.findFile(nome) != null) nomeLibero(a, nome) else nome
                        val ok = spostaUno(f, a, destinazione)
                        if (ok) fatti++ else falliti++
                        avanzamento(fatti + falliti, totale)
                    }
                }
            }

            porta(dentro, root)
            if (dentro.listFiles().isEmpty()) runCatching { dentro.delete() }
            Annidate(quante = 1, spostati = fatti, falliti = falliti)
        }

    private fun nomeLibero(dove: DocumentFile, nome: String): String {
        val base = nome.substringBeforeLast('.', nome)
        val est = nome.substringAfterLast('.', "")
        for (i in 2..99) {
            val prova = if (est.isEmpty()) "$base ($i)" else "$base ($i).$est"
            if (dove.findFile(prova) == null) return prova
        }
        return nome
    }

    private fun spostaUno(f: DocumentFile, verso: DocumentFile, nome: String): Boolean {
        // La strada veloce: il provider sposta senza copiare.
        val mosso = runCatching {
            android.provider.DocumentsContract.moveDocument(
                context.contentResolver, f.uri, f.parentFile!!.uri, verso.uri,
            )
        }.getOrNull()
        if (mosso != null) {
            if (nome != f.name) runCatching {
                android.provider.DocumentsContract.renameDocument(context.contentResolver, mosso, nome)
            }
            return true
        }
        // La strada lenta, per i provider che non sanno spostare.
        return runCatching {
            val nuovo = verso.createFile(f.type ?: "application/octet-stream", nome) ?: return false
            context.contentResolver.openInputStream(f.uri)!!.use { input ->
                context.contentResolver.openOutputStream(nuovo.uri, "wt")!!.use { input.copyTo(it) }
            }
            // Si cancella solo dopo che la copia c'e' davvero.
            if (nuovo.length() > 0 || f.length() == 0L) f.delete() else false
        }.getOrDefault(false)
    }

    private fun sottocartella(padre: DocumentFile, nome: String): DocumentFile? =
        padre.findFile(nome)?.takeIf { it.isDirectory } ?: padre.createDirectory(nome)

    /**
     * Sposta il file scaricato nella destinazione scelta.
     *
     * Torna la posizione finale: un percorso assoluto se siamo nello
     * spazio privato, un `content://` se l'utente ha scelto una cartella.
     */
    suspend fun publish(
        videoId: String,
        artist: String,
        title: String,
        audio: File,
    ): String = withContext(Dispatchers.IO) {
        val ext = audio.extension.ifEmpty { "m4a" }
        val name = fileName(artist, title, videoId, ext)
        val destTree = _tree.value

        if (destTree == null) {
            val dir = File(privateDir, videoId).apply { mkdirs() }
            val out = File(dir, "$videoId.$ext")
            audio.copyTo(out, overwrite = true)
            audio.delete()
            return@withContext out.absolutePath
        }

        val root = radice(destTree)
            ?: throw IllegalStateException("cartella non più raggiungibile")
        val brani = sottocartella(root, BRANI)
            ?: throw IllegalStateException("non riesco a creare la cartella dei brani")
        // Una cartella per artista: e' il modo in cui una persona si
        // aspetta di trovare la musica aprendo la cartella da un computer.
        val folder = sottocartella(brani, sanitize(artist, "Artista sconosciuto"))
            ?: brani

        // Un omonimo lasciato da un tentativo precedente va tolto, se no
        // il sistema aggiunge «(1)» e ci ritroviamo due copie.
        folder.findFile(name)?.delete()

        val doc = folder.createFile(mimeFor(ext), name)
            ?: throw IllegalStateException("non riesco a scrivere nella cartella scelta")

        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            audio.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("non riesco a scrivere il file")

        audio.delete()
        doc.uri.toString()
    }

    /**
     * La copertina va sempre nello spazio privato — e' li' che la legge
     * l'app — ma se c'e' una cartella scelta se ne tiene una copia anche
     * li', perche' senza copertine un ripristino restituirebbe una
     * libreria di rettangoli vuoti.
     */
    suspend fun publishCover(videoId: String, cover: File): String =
        withContext(Dispatchers.IO) {
            val out = File(coversDir, "$videoId.jpg")
            cover.copyTo(out, overwrite = true)
            cover.delete()
            _tree.value?.let { copiaCopertinaNelBackup(it, videoId, out) }
            out.absolutePath
        }

    private fun copiaCopertinaNelBackup(tree: Uri, videoId: String, file: File) {
        runCatching {
            val root = radice(tree) ?: return
            val cartella = sottocartella(root, COPERTINE) ?: return
            cartella.findFile("$videoId.jpg")?.delete()
            val doc = cartella.createFile("image/jpeg", "$videoId.jpg") ?: return
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        }
    }

    /** Cancella i file di un brano, ovunque siano finiti. */
    suspend fun remove(track: TrackEntity) = withContext(Dispatchers.IO) {
        runCatching {
            if (track.path.startsWith("content://")) {
                DocumentsContract.deleteDocument(
                    context.contentResolver, Uri.parse(track.path),
                )
            } else {
                val f = File(track.path)
                // Nello spazio privato ogni brano ha la sua cartella.
                f.parentFile?.takeIf { it.name == track.videoId }?.deleteRecursively()
                    ?: f.delete()
            }
        }
        track.coverPath?.let { runCatching { File(it).delete() } }
        Unit
    }

    /** Dove sono finiti audio e copertina dopo uno spostamento. */
    data class Relocated(val path: String, val coverPath: String?)

    /**
     * Sposta un brano gia' scaricato nella destinazione corrente.
     * Torna null se era gia' al posto giusto.
     */
    suspend fun relocate(track: TrackEntity): Relocated? = withContext(Dispatchers.IO) {
        val destTree = _tree.value
        val isRemote = track.path.startsWith("content://")
        if ((destTree == null) == !isRemote) return@withContext null   // gia' a posto

        // La copertina si mette in salvo per prima: se sta ancora accanto
        // all'audio, quello che segue la cancellerebbe. E il percorso
        // nuovo va restituito, se no il database punta al vuoto.
        val copertina = ensureCoverSafe(track.videoId, track.coverPath)

        val tmp = File(context.cacheDir, "sposta-${track.videoId}")
        try {
            openRead(track.path).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            val nuovo = publish(track.videoId, track.artist, track.title, tmp)
            // Solo ora che la copia e' al sicuro si cancella l'originale.
            removeAudioOnly(track.path)
            Relocated(nuovo, copertina)
        } finally {
            tmp.delete()
        }
    }

    private fun openRead(location: String) =
        if (location.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(location))
                ?: throw IllegalStateException("file non leggibile")
        } else {
            File(location).inputStream()
        }

    /**
     * Cancella SOLO il file audio.
     *
     * ⚠️ Qui c'era `parentFile.deleteRecursively()`, e ha distrutto delle
     * copertine: i brani scaricati prima della riorganizzazione tenevano
     * il `cover.jpg` nella stessa cartella dell'audio, e spostandoli la
     * cancellazione del genitore se lo portava via. Adesso si toglie il
     * file e la cartella solo se rimane vuota.
     */
    private fun removeAudioOnly(location: String) {
        runCatching {
            if (location.startsWith("content://")) {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(location))
            } else {
                val f = File(location)
                f.delete()
                f.parentFile?.takeIf { it.isDirectory && it.list()?.isEmpty() == true }?.delete()
            }
        }
    }

    /**
     * Porta la copertina fuori dalla cartella dell'audio, se e' ancora
     * li' dentro. Torna il percorso nuovo, o quello vecchio se non c'era
     * niente da spostare.
     *
     * Serve ai brani scaricati prima che le copertine avessero una
     * cartella loro: finche' restano accanto all'audio, ogni operazione
     * sull'audio le mette a rischio.
     */
    suspend fun ensureCoverSafe(videoId: String, coverPath: String?): String? =
        withContext(Dispatchers.IO) {
            if (coverPath == null || coverPath.startsWith("content://")) return@withContext coverPath
            val vecchia = File(coverPath)
            if (!vecchia.exists()) return@withContext null
            if (vecchia.parentFile == coversDir) return@withContext coverPath

            val nuova = File(coversDir, "$videoId.jpg")
            runCatching {
                vecchia.copyTo(nuova, overwrite = true)
                vecchia.delete()
            }.getOrElse { return@withContext coverPath }
            nuova.absolutePath
        }

    /** Dove salvare una copertina recuperata. */
    suspend fun saveCover(videoId: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val out = File(coversDir, "$videoId.jpg")
                out.writeBytes(bytes)
                out.absolutePath
            }.getOrNull()
        }

    /** Se il file della copertina esiste ancora. */
    fun coverExists(coverPath: String?): Boolean =
        coverPath != null && !coverPath.startsWith("content://") && File(coverPath).exists()

    companion object {
        /** Il nome non cambia mai: e' cosi' che si riconosce la cartella. */
        const val KEY_SEMPRE_OFFLINE = "sempre_offline"
        const val KEY_NOTIFICHE = "sentire_notifiche"
        const val RADICE = "SoSound"
        const val BRANI = "Brani"
        const val COPERTINE = "Copertine"

        private const val KEY_TREE = "cartella"

        /** Caratteri che i filesystem e i servizi di sincronizzazione rifiutano. */
        private val ILLEGALI = Regex("""[/\\:*?"<>|\x00-\x1f]""")

        /** Rende un nome sicuro per una cartella o un file. */
        fun sanitize(nome: String, fallback: String, max: Int = 60): String {
            val pulito = ILLEGALI.replace(nome, "").trim().trimEnd('.', ' ').take(max)
            return pulito.ifEmpty { fallback }
        }

        /** Solo per i test: [fileName] e' privata e deve restarlo. */
        fun nomeFileDiProva(artist: String, title: String, videoId: String, ext: String) =
            fileName(artist, title, videoId, ext)

        private fun fileName(artist: String, title: String, videoId: String, ext: String): String {
            fun pulisci(s: String) = ILLEGALI.replace(s, "").trim().take(60).ifEmpty { "—" }
            // Il videoId in coda tiene i nomi unici senza che il sistema
            // debba aggiungere «(1)», e permette di ritrovare un brano
            // anche guardando la cartella da un computer.
            return "${pulisci(artist)} - ${pulisci(title)} [$videoId].$ext"
        }

        /** I pezzi che stanno nel nome di un file di brano. */
        data class NomeBrano(
            val artista: String,
            val titolo: String,
            val videoId: String,
            val estensione: String,
        )

        /**
         * Rilegge il nome di un file prodotto da [fileName].
         *
         * Serve a ricostruire la libreria da una cartella che ha perso
         * l'indice: `Artista - Titolo [videoId].m4a` contiene gia' tutto
         * quello che serve per rimettere un brano al suo posto.
         *
         * Torna null se il nome non ha questa forma — un mp3 copiato
         * dentro a mano non e' un nostro brano, e indovinarne
         * l'identificativo non si puo'.
         */
        fun leggiNome(nome: String): NomeBrano? {
            val estensione = nome.substringAfterLast('.', "")
            if (estensione.isEmpty() || estensione.length > 5) return null

            val senzaEstensione = nome.substringBeforeLast('.')
            if (!senzaEstensione.endsWith("]")) return null
            val apertura = senzaEstensione.lastIndexOf('[')
            if (apertura <= 0) return null

            val videoId = senzaEstensione.substring(apertura + 1, senzaEstensione.length - 1)
            if (!ID_YT.matches(videoId)) return null

            val resto = senzaEstensione.substring(0, apertura).trim()
            // Il separatore giusto e' il PRIMO: un titolo puo' contenere
            // un trattino («Live - 1999»), un nome d'artista quasi mai.
            val taglio = resto.indexOf(SEPARATORE)
            if (taglio < 0) {
                return NomeBrano(SCONOSCIUTO, resto.ifEmpty { videoId }, videoId, estensione)
            }
            val artista = resto.substring(0, taglio).trim()
            val titolo = resto.substring(taglio + SEPARATORE.length).trim()
            return NomeBrano(
                artista.ifEmpty { SCONOSCIUTO },
                titolo.ifEmpty { videoId },
                videoId,
                estensione,
            )
        }

        private const val SEPARATORE = " - "
        private const val SCONOSCIUTO = "Artista sconosciuto"
        private val ID_YT = Regex("^[A-Za-z0-9_-]{11}$")

        private fun mimeFor(ext: String) = when (ext.lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "webm" -> "audio/webm"
            "opus" -> "audio/opus"
            "mp3" -> "audio/mpeg"
            else -> "audio/*"
        }
    }
}
