package com.sosound.app.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.common.util.UnstableApi
import java.io.File

/**
 * Quello che si e' ascoltato resta, finche' c'e' posto.
 *
 * ## Come si comporta
 *
 * Ascoltando un brano in streaming i byte passano di qui e ci restano:
 * la seconda volta parte subito e senza rete. Quando lo spazio finisce
 * se ne va quello che non si sente da piu' tempo — non e' un archivio,
 * e' una comodita' che si sfoltisce da sola.
 *
 * ## Perche' la chiave e' il videoId
 *
 * L'indirizzo di un flusso YouTube contiene una firma che scade e cambia
 * a ogni richiesta. Usandolo come chiave, lo stesso brano finirebbe in
 * cache due volte e la prima copia non verrebbe mai riusata. La chiave
 * e' quindi l'identificativo del brano, che non cambia mai.
 */
@UnstableApi
object CacheAudio {

    /** Il tetto. Oltre, si butta il meno ascoltato. */
    const val TETTO_BYTE = 2L * 1024 * 1024 * 1024

    private var istanza: SimpleCache? = null

    fun cartella(context: Context) = File(context.cacheDir, "audio")

    @Synchronized
    fun di(context: Context): SimpleCache =
        istanza ?: SimpleCache(
            cartella(context).apply { mkdirs() },
            LeastRecentlyUsedCacheEvictor(TETTO_BYTE),
            StandaloneDatabaseProvider(context),
        ).also { istanza = it }

    /** Quanto occupa adesso. */
    fun occupati(context: Context): Long =
        istanza?.cacheSpace ?: pesaCartella(cartella(context))

    /**
     * Svuota.
     *
     * Passa dalla cache aperta e non cancella i file a mano: SimpleCache
     * tiene un indice di cosa c'e' dentro, e togliere i file sotto i
     * suoi piedi lo lascerebbe a promettere pezzi che non esistono piu'.
     */
    @Synchronized
    fun svuota(context: Context, risparmia: String? = null): Int {
        val c = di(context)
        var tolti = 0
        for (chiave in c.keys.toList()) {
            // Il brano in riproduzione si lascia stare.
            //
            // Togliergli i pezzi sotto i piedi mentre suona lo fa
            // interrompere a meta': chi preme «svuota» in impostazioni
            // vuole liberare spazio, non zittire la musica. Quello resta,
            // e si dice.
            if (chiave == risparmia) continue
            for (pezzo in c.getCachedSpans(chiave)) {
                runCatching { c.removeSpan(pezzo); tolti++ }
            }
        }
        return tolti
    }

    /** Chiude la cache. Da chiamare quando il lettore se ne va. */
    @Synchronized
    fun chiudi() {
        runCatching { istanza?.release() }
        istanza = null
    }

    /**
     * Tira fuori dalla cache un brano che c'e' tutto, in un file solo.
     *
     * ## A cosa serve
     *
     * A non riscaricare quello che si ha gia'. Un brano ascoltato per
     * intero e' tutto qui dentro: «tienilo anche senza rete» puo' essere
     * una copia da disco invece di un giro in rete da qualche megabyte.
     *
     * ## Quando torna null
     *
     * Quando manca anche un solo pezzo. La cache tiene frammenti — se si
     * salta avanti in un brano, in mezzo resta un buco — e un file con
     * un buco dentro e' peggio di nessun file: si apre, suona, e si
     * interrompe a meta' senza che niente lo spieghi.
     */
    fun estrai(context: Context, videoId: String, destinazione: File): String? {
        val c = di(context)
        val lunghezza = c.getContentMetadata(videoId).get("exo_len", -1L)
        if (lunghezza <= 0) return null

        val pezzi = c.getCachedSpans(videoId)
            .filter { it.isCached && it.file != null }
            .sortedBy { it.position }
        if (pezzi.isEmpty()) return null

        // I pezzi devono coprire tutto, dall'inizio alla fine, senza
        // buchi e senza affidarsi al fatto che siano gia' in ordine.
        var arrivato = 0L
        for (p in pezzi) {
            if (p.position > arrivato) return null
            arrivato = maxOf(arrivato, p.position + p.length)
        }
        if (arrivato < lunghezza) return null

        return runCatching {
            destinazione.outputStream().use { out ->
                var scritto = 0L
                for (p in pezzi) {
                    if (p.position + p.length <= scritto) continue
                    val salta = scritto - p.position
                    p.file!!.inputStream().use { input ->
                        if (salta > 0) input.skip(salta)
                        scritto += input.copyTo(out)
                    }
                }
            }
            val testa = ByteArray(16)
            destinazione.inputStream().use { it.read(testa) }
            Formato.daiPrimiByte(testa)
        }.getOrNull()
    }

    private fun pesaCartella(f: File): Long =
        if (!f.exists()) 0
        else if (f.isFile) f.length()
        else f.listFiles()?.sumOf { pesaCartella(it) } ?: 0
}
