package com.sosound.app.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import com.sosound.app.data.stream.Flusso

/**
 * Da dove leggere un brano: dal telefono, o dalla rete tenendone copia.
 *
 * ## Perche' due strade e non una
 *
 * Un brano salvato sta gia' su disco: leggerlo attraverso la cache
 * vorrebbe dire copiarlo una seconda volta dentro la cache, cioe'
 * raddoppiare lo spazio occupato da tutta la libreria per niente.
 *
 * Un brano in streaming invece ha bisogno di tutt'e due i pezzi: la
 * cache davanti, che risponde da sola quando il brano c'e' gia', e la
 * risoluzione dietro, che chiede a yt-dlp dove guardare — **solo se la
 * cache non basta**. L'ordine e' il punto: risolvendo per primo si
 * aspetterebbero due secondi anche per un brano che si ha gia' in casa.
 *
 * Quale delle due serve si sa solo aprendo, perche' e' l'indirizzo a
 * dirlo. Per questo la scelta sta qui dentro e non nella fabbrica.
 */
@UnstableApi
class SorgenteAudio(
    private val locale: DataSource,
    private val rete: DataSource,
) : DataSource {

    private var scelta: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val d = if (dataSpec.uri.scheme == Flusso.SCHEMA) rete else locale
        scelta = d
        return d.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        scelta?.read(buffer, offset, length) ?: -1

    override fun getUri(): Uri? = scelta?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        scelta?.responseHeaders ?: emptyMap()

    override fun close() {
        try { scelta?.close() } finally { scelta = null }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        locale.addTransferListener(transferListener)
        rete.addTransferListener(transferListener)
    }

    class Factory(private val context: Context) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            val http = DefaultHttpDataSource.Factory()
                // YouTube manda altrove, e piu' di una volta.
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(20_000)

            // Dentro: l'indirizzo finto diventa quello vero. Succede sul
            // thread di caricamento del lettore, non sull'interfaccia.
            val risolvente = ResolvingDataSource.Factory(
                DefaultDataSource.Factory(context, http),
            ) { dataSpec ->
                val videoId = Flusso.videoIdDi(dataSpec.uri)
                    ?: return@Factory dataSpec
                val vero = Flusso.indirizzo(videoId)
                    ?: throw java.io.IOException("Non trovo il flusso di $videoId")
                dataSpec.buildUpon()
                    .setUri(Uri.parse(vero))
                    // La chiave resta il brano: l'indirizzo vero cambia a
                    // ogni richiesta e in cache non lo ritroveremmo mai.
                    .setKey(videoId)
                    .build()
            }

            val conCache = CacheDataSource.Factory()
                .setCache(CacheAudio.di(context))
                .setUpstreamDataSourceFactory(risolvente)
                // Se la rete cade a meta', si continua con quello che c'e'
                // in cache invece di fermare tutto.
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            return SorgenteAudio(
                locale = DefaultDataSource.Factory(context, http).createDataSource(),
                rete = conCache.createDataSource(),
            )
        }
    }
}
