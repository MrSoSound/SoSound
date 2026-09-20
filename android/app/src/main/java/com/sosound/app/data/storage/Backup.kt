package com.sosound.app.data.storage

import com.sosound.app.data.library.PlaylistEntity
import com.sosound.app.data.library.TrackEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * L'indice che rende una cartella ricostruibile.
 *
 * Senza, una cartella di file audio e' solo una cartella di file audio:
 * si ritroverebbero i brani ma non gli album, non le playlist, non
 * l'ordine. Con questo, reinstallare l'app e riscegliere la cartella
 * rimette tutto com'era.
 */
@Serializable
data class BackupIndex(
    /** Marchio di formato: serve a riconoscere la cartella giusta. */
    val formato: String = FORMATO,
    val versione: Int = VERSIONE,
    @SerialName("creato_il") val creatoIl: Long = 0,
    val brani: List<BackupTrack> = emptyList(),
    val playlist: List<BackupPlaylist> = emptyList(),
    /**
     * Impronta del contenuto, calcolata senza questo campo.
     *
     * Non dimostra la provenienza — una chiave dentro l'app sarebbe
     * estraibile da chiunque, e una firma del genere sembrerebbe una
     * garanzia senza esserlo. Dimostra che l'indice non e' stato
     * troncato ne' modificato dopo la scrittura, che e' il guasto vero:
     * una sincronizzazione interrotta a meta'.
     */
    val impronta: String? = null,
) {
    companion object {
        const val FORMATO = "sosound-backup"
        const val VERSIONE = 1
        const val NOME_FILE = "sosound.json"
    }
}

@Serializable
data class BackupTrack(
    @SerialName("video_id") val videoId: String,
    val titolo: String,
    val artista: String,
    val album: String? = null,
    @SerialName("show_id") val showId: String? = null,
    val durata: Int? = null,
    /** Percorso relativo alla cartella SoSound. */
    val file: String,
    val copertina: String? = null,
    val byte: Long = 0,
    /**
     * Falso se di questo brano abbiamo solo il nome.
     *
     * Fa parte della libreria e delle sue playlist, ma il file non c'e':
     * si ascolta dalla rete. Va scritto lo stesso, se no una playlist
     * ripresa su un telefono nuovo arriva monca senza che niente spieghi
     * perche' — e i brani mancanti non sono persi, sono solo da
     * riscaricare o da sentire in streaming.
     *
     * Cosa invece NON si scrive: quali brani stanno nella cache. Quello
     * e' un fatto di questo telefono in questo momento, e in una
     * cartella che descrive un backup sarebbe una promessa che scade da
     * sola.
     */
    @SerialName("con_file") val conFile: Boolean = true,
    /**
     * Impronta parziale: dimensione piu' i primi e gli ultimi 64 KB.
     *
     * Non l'intero file di proposito. Su una libreria di cinquecento
     * brani il calcolo completo vorrebbe leggere due gigabyte a ogni
     * verifica; questa versione costa qualche millisecondo e riconosce
     * comunque i due guasti che capitano davvero, il file troncato e il
     * file sostituito.
     */
    val frammento: String? = null,
    @SerialName("aggiunto_il") val aggiuntoIl: Long = 0,
)

@Serializable
data class BackupPlaylist(
    val nome: String,
    @SerialName("creata_il") val creataIl: Long = 0,
    /** Gli identificativi, nell'ordine scelto dall'utente. */
    val brani: List<String> = emptyList(),
)

/** Cosa e' andato storto leggendo una cartella. */
sealed interface ImportProblem {
    data object NonRiconosciuta : ImportProblem
    data class VersioneFutura(val trovata: Int) : ImportProblem
    data object IndiceAlterato : ImportProblem
    data class Vuoto(val motivo: String) : ImportProblem
}

/** L'esito di una lettura: cosa si puo' importare e cosa non torna. */
data class ImportPreview(
    val index: BackupIndex,
    /** Brani il cui file c'e' e corrisponde. */
    val validi: List<BackupTrack>,
    /** Elencati ma il file non c'e' piu'. */
    val mancanti: List<BackupTrack>,
    /** Il file c'e' ma non corrisponde: sincronizzazione a meta', o alterato. */
    val corrotti: List<BackupTrack>,
    /**
     * Falso se la cartella e' stata ricostruita leggendo i nomi dei file.
     *
     * Cambia cosa si puo' promettere: senza indice i brani tornano, le
     * playlist no, e non c'e' niente con cui verificare che i file siano
     * integri. Va detto a chi sta per premere «importa».
     */
    val daIndice: Boolean = true,
    /**
     * Importati, ma con l'impronta che non torna.
     *
     * La dimensione combacia — quindi il file c'e' ed e' lungo quello
     * che deve — ma il frammento no. Capita a ogni indice scritto da una
     * versione precedente, e non e' un motivo per rifiutare la musica di
     * qualcuno: si dice quanti sono e si va avanti.
     */
    val sospetti: List<BackupTrack> = emptyList(),
    /**
     * Vero se l'impronta dell'indice non tornava.
     *
     * Non impedisce niente: l'indice e' leggibile e si usa. Serve solo
     * a dirlo, perche' «viene da una versione precedente» e «qualcuno
     * l'ha modificato a mano» hanno lo stesso aspetto.
     */
    val nonVerificato: Boolean = false,
) {
    val totale: Int get() = validi.size + mancanti.size + corrotti.size
}

object BackupFormat {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * L'impronta dell'indice: cosa dice, non com'e' scritto.
     *
     * ## Perche' non e' il SHA del file
     *
     * Lo era, e questo l'ha resa una mina. Con `encodeDefaults = true`
     * ogni campo nuovo compare nella serializzazione, quindi lo stesso
     * identico contenuto si scrive in modo diverso da una versione
     * all'altra: aggiungendo `con_file` **tutti** gli indici gia'
     * esistenti sono diventati «alterati» di colpo. E chi leggeva, non
     * fidandosene, ripiegava sulla ricostruzione dai nomi dei file — che
     * i brani li ritrova e le playlist no.
     *
     * Adesso si calcola su una proiezione esplicita: l'elenco di cosa
     * c'e' dentro. Un campo nuovo non la sposta; un brano in piu', un
     * titolo diverso o una playlist cambiata si'.
     */
    fun fingerprint(index: BackupIndex): String = sha256(
        buildString {
            append(index.formato).append('|').append(index.versione).append('\n')
            for (b in index.brani) {
                append(b.videoId).append('|').append(b.titolo).append('|')
                    .append(b.artista).append('|').append(b.album ?: "").append('|')
                    .append(b.file).append('\n')
            }
            for (p in index.playlist) {
                append(p.nome).append('|').append(p.brani.joinToString(",")).append('\n')
            }
        }.toByteArray()
    )

    /**
     * Se di questo indice ci si puo' comunque servire.
     *
     * Diverso da [validate]: quella dice se e' verificabile, questa se
     * e' **leggibile**. Un'impronta che non torna non rende illeggibile
     * niente — e buttare via le playlist di qualcuno per un dettaglio
     * del nostro formato e' il rimedio peggiore del male.
     */
    fun usabile(index: BackupIndex): Boolean =
        index.formato == BackupIndex.FORMATO &&
            index.versione <= BackupIndex.VERSIONE &&
            (index.brani.isNotEmpty() || index.playlist.isNotEmpty())

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * Verifica che l'indice sia leggibile e coerente.
     * Torna null se va bene, altrimenti cosa non torna.
     */
    fun validate(index: BackupIndex): ImportProblem? = when {
        index.formato != BackupIndex.FORMATO -> ImportProblem.NonRiconosciuta
        index.versione > BackupIndex.VERSIONE -> ImportProblem.VersioneFutura(index.versione)
        // L'impronta manca solo se l'indice e' stato riscritto a mano:
        // non e' un errore fatale, ma non e' piu' verificabile.
        index.impronta != null && index.impronta != fingerprint(index) ->
            ImportProblem.IndiceAlterato
        index.brani.isEmpty() -> ImportProblem.Vuoto("l'indice non elenca nessun brano")
        else -> null
    }

    fun toBackup(t: TrackEntity, file: String, copertina: String?, frammento: String?) =
        BackupTrack(
            videoId = t.videoId,
            titolo = t.title,
            artista = t.artist,
            album = t.album,
            showId = t.showId,
            durata = t.durationSeconds,
            file = file,
            copertina = copertina,
            byte = t.sizeBytes,
            frammento = frammento,
            aggiuntoIl = t.addedAt,
            conFile = t.haFile,
        )

    fun toPlaylist(p: PlaylistEntity, brani: List<String>) =
        BackupPlaylist(nome = p.name, creataIl = p.createdAt, brani = brani)
}
