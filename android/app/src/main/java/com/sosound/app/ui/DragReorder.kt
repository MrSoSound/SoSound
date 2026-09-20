package com.sosound.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Trascinamento per riordinare una lista.
 *
 * Compose non lo offre, e le implementazioni che si trovano in giro
 * sbagliano quasi sempre la stessa cosa: confrontano il bordo
 * dell'elemento trascinato con il bordo di quelli fermi. Con elementi di
 * altezza diversa l'ordine «sfarfalla», perche' la condizione di scambio
 * si accende e si spegne a ogni pixel.
 *
 * Qui si confrontano i **centri**: l'elemento trascinato scavalca un
 * altro solo quando il suo centro ha superato il centro dell'altro. È una
 * soglia stabile, che non puo' oscillare.
 */
class DragReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    /** L'indice dell'elemento in mano, o null se non si sta trascinando. */
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    /** Di quanto e' stato spostato rispetto alla sua posizione di riposo. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var startOffset = 0f
    private var autoScroll: Job? = null

    private val info: LazyListItemInfo?
        get() = draggingIndex?.let { i ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
        }

    fun onStart(offsetY: Float) {
        val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            offsetY.toInt() in it.offset..(it.offset + it.size)
        } ?: return
        draggingIndex = hit.index
        startOffset = offsetY - hit.offset
        offset = 0f
    }

    fun onDrag(deltaY: Float) {
        offset += deltaY
        val corrente = info ?: return

        // Bordi dell'elemento trascinato nella sua posizione attuale.
        val inizio = corrente.offset + offset
        val centro = inizio + corrente.size / 2f

        val bersaglio = listState.layoutInfo.visibleItemsInfo.firstOrNull { altro ->
            altro.index != corrente.index &&
                centro.toInt() in altro.offset..(altro.offset + altro.size)
        }

        if (bersaglio != null) {
            val centroBersaglio = bersaglio.offset + bersaglio.size / 2f
            val scavalcato =
                (centro > centroBersaglio && bersaglio.index > corrente.index) ||
                    (centro < centroBersaglio && bersaglio.index < corrente.index)

            if (scavalcato) {
                onMove(corrente.index, bersaglio.index)
                // L'elemento adesso occupa il posto dell'altro: lo
                // scostamento va ridotto della distanza percorsa, se no
                // salterebbe in avanti di un'altezza intera.
                offset -= (bersaglio.offset - corrente.offset)
                draggingIndex = bersaglio.index
            }
        }

        scorriSeAiBordi(inizio, corrente.size)
    }

    /**
     * Quando si trascina contro il bordo, la lista scorre da sola:
     * altrimenti non si potrebbe spostare un brano oltre lo schermo.
     */
    private fun scorriSeAiBordi(inizio: Float, altezza: Int) {
        val viewport = listState.layoutInfo.viewportEndOffset
        val margine = altezza.coerceAtMost(120)
        val velocita = when {
            inizio < margine -> -8f
            inizio + altezza > viewport - margine -> 8f
            else -> 0f
        }
        if (velocita == 0f) {
            autoScroll?.cancel(); autoScroll = null
            return
        }
        if (autoScroll?.isActive == true) return
        autoScroll = scope.launch {
            while (true) {
                listState.scrollBy(velocita)
                kotlinx.coroutines.delay(16)
            }
        }
    }

    fun onStop() {
        draggingIndex = null
        offset = 0f
        autoScroll?.cancel()
        autoScroll = null
    }
}

@Composable
fun rememberDragReorder(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
): DragReorderState {
    val scope = rememberCoroutineScope()
    return remember(listState) { DragReorderState(listState, scope, onMove) }
}

/**
 * Si aggancia alla LazyColumn. Il trascinamento parte con una pressione
 * lunga: con un tocco normale non si potrebbe piu' scorrere la lista.
 */
fun Modifier.dragReorder(state: DragReorderState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { state.onStart(it.y) },
        onDrag = { change, amount ->
            change.consume()
            state.onDrag(amount.y)
        },
        onDragEnd = { state.onStop() },
        onDragCancel = { state.onStop() },
    )
}
