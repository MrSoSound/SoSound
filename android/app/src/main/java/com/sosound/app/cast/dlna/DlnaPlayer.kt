package com.sosound.app.cast.dlna

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Un lettore che suona su un apparecchio DLNA.
 *
 * Estende [SimpleBasePlayer], che esiste apposta: implementando `getState`
 * e i comandi si ottiene un [Player] completo, utilizzabile dalla sessione
 * media esattamente come ExoPlayer. Senza, ci sarebbero sessanta metodi da
 * scrivere a mano.
 *
 * ## Come si comporta davvero
 *
 * Un renderer DLNA riproduce **un brano per volta**: non ha una coda. La
 * coda resta quindi qui, e quando il brano finisce siamo noi a mandare il
 * successivo. Per accorgercene bisogna chiedere all'apparecchio come sta,
 * perche' UPnP non avvisa: c'e' un meccanismo di notifiche (GENA) ma
 * richiede di fare da server e meta' degli apparecchi lo implementa male.
 * Chiedere ogni secondo e' meno elegante e molto piu' affidabile.
 */
@UnstableApi
class DlnaPlayer(
    private val rete: DlnaNetwork,
    private val device: DlnaDevice,
    /** Da videoId all'indirizzo servito dal telefono. */
    private val urlDi: (String) -> String?,
    /** Da videoId al tipo del file. */
    private val mimeDi: (String) -> String,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sonda: Job? = null

    private var coda: List<MediaItem> = emptyList()
    private var indice = 0
    private var inRiproduzione = false
    private var posizioneMs = 0L
    private var durataMs = androidx.media3.common.C.TIME_UNSET

    override fun getState(): State {
        val elementi = coda.mapIndexed { i, item ->
            MediaItemData.Builder(item.mediaId.ifEmpty { "i$i" })
                .setMediaItem(item)
                .setMediaMetadata(item.mediaMetadata)
                .setDurationUs(
                    if (i == indice && durataMs > 0) durataMs * 1000
                    else androidx.media3.common.C.TIME_UNSET
                )
                .setIsSeekable(true)
                .setIsDynamic(false)
                .build()
        }

        return State.Builder()
            .setAvailableCommands(COMANDI)
            .setPlaybackState(
                if (coda.isEmpty()) Player.STATE_IDLE else Player.STATE_READY
            )
            .setPlayWhenReady(inRiproduzione, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(elementi)
            .setCurrentMediaItemIndex(indice.coerceIn(0, maxOf(0, coda.size - 1)))
            .setContentPositionMs { posizioneMs }
            .build()
    }

    // ------------------------------------------------------------ comandi

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        coda = mediaItems.toList()
        indice = if (startIndex == C_INDEX_UNSET) 0 else startIndex.coerceIn(0, maxOf(0, coda.size - 1))
        posizioneMs = startPositionMs.coerceAtLeast(0)
        mandaBranoCorrente(daMs = posizioneMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        coda = coda.toMutableList().apply { addAll(index.coerceIn(0, size), mediaItems) }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        coda = coda.toMutableList().apply {
            subList(fromIndex.coerceIn(0, size), toIndex.coerceIn(0, size)).clear()
        }
        if (indice >= coda.size) indice = maxOf(0, coda.size - 1)
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val lista = coda.toMutableList()
        if (fromIndex in lista.indices && newIndex in lista.indices) {
            lista.add(newIndex, lista.removeAt(fromIndex))
            coda = lista
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        inRiproduzione = playWhenReady
        scope.launch {
            if (playWhenReady) rete.riprendi(device) else rete.pausa(device)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val nuovo = if (mediaItemIndex == C_INDEX_UNSET) indice else mediaItemIndex
        if (nuovo != indice) {
            indice = nuovo.coerceIn(0, maxOf(0, coda.size - 1))
            posizioneMs = positionMs.coerceAtLeast(0)
            mandaBranoCorrente(daMs = posizioneMs)
        } else {
            posizioneMs = positionMs.coerceAtLeast(0)
            scope.launch { rete.vaiA(device, posizioneMs / 1000) }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        inRiproduzione = false
        scope.launch { rete.ferma(device) }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        sonda?.cancel()
        scope.launch { rete.ferma(device) }
        return Futures.immediateVoidFuture()
    }

    // ------------------------------------------------------------ interno

    private fun mandaBranoCorrente(daMs: Long = 0) {
        val item = coda.getOrNull(indice) ?: return
        val url = urlDi(item.mediaId) ?: return
        inRiproduzione = true
        durataMs = androidx.media3.common.C.TIME_UNSET
        scope.launch {
            val ok = rete.riproduci(
                device, url,
                titolo = item.mediaMetadata.title?.toString() ?: "Brano",
                artista = item.mediaMetadata.artist?.toString() ?: "",
                mime = mimeDi(item.mediaId),
            )
            if (ok && daMs > 1000) rete.vaiA(device, daMs / 1000)
            avviaSonda()
            invalidateState()
        }
    }

    /**
     * Chiede all'apparecchio come sta, una volta al secondo.
     *
     * Serve a due cose: far avanzare la barra di posizione, e accorgersi
     * che il brano e' finito per mandare il successivo. Un renderer DLNA
     * non ha una coda e non avvisa quando ha finito.
     */
    private fun avviaSonda() {
        if (sonda?.isActive == true) return
        sonda = scope.launch {
            var fermoDa = 0
            while (true) {
                delay(1000)
                val (pos, dur) = rete.posizione(device)
                if (pos >= 0) posizioneMs = pos * 1000
                if (dur > 0) durataMs = dur * 1000

                val stato = rete.stato(device)
                when {
                    stato == "PLAYING" -> { inRiproduzione = true; fermoDa = 0 }
                    stato == "PAUSED_PLAYBACK" -> { inRiproduzione = false; fermoDa = 0 }
                    stato == "STOPPED" && inRiproduzione -> {
                        // Due letture prima di concludere: durante il
                        // passaggio da un brano all'altro molti apparecchi
                        // dicono STOPPED per un istante, e fidarsi della
                        // prima farebbe saltare una canzone.
                        fermoDa++
                        if (fermoDa >= 2) { fermoDa = 0; avanti() }
                    }
                    else -> Unit
                }
                invalidateState()
            }
        }
    }

    private fun avanti() {
        if (indice + 1 < coda.size) {
            indice++
            posizioneMs = 0
            mandaBranoCorrente()
        } else {
            inRiproduzione = false
        }
    }

    private companion object {
        const val C_INDEX_UNSET = androidx.media3.common.C.INDEX_UNSET

        val COMANDI: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
