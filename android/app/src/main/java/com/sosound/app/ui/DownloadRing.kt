package com.sosound.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sosound.app.data.download.QueueItem

/**
 * A che punto e' un brano che sta scaricando, per disegnare l'anello
 * sopra la sua copertina.
 *
 * Non e' lo stesso di [QueueItem.State]: qui interessa solo la
 * differenza fra «non sappiamo ancora quanto manca» (in coda, o si sta
 * aggiornando yt-dlp prima di ritentare) e «sappiamo a che punto siamo»
 * (in corso, con una percentuale vera). Finito o fallito non hanno un
 * anello — il primo sparisce da solo dalla coda, il secondo ha gia' il
 * suo linguaggio, il rosso dell'errore.
 */
sealed interface CoverDownload {
    /** In coda, o mentre si aggiorna il motore: nessuna percentuale vera. */
    data object InAttesa : CoverDownload

    /** In corso, con la percentuale che yt-dlp riporta davvero. */
    data class InCorso(val progress: Float) : CoverDownload

    companion object {
        /**
         * Da una voce della coda al suo anello, o null se questo brano
         * non ne ha bisogno — non e' in coda, oppure ha gia' finito o
         * e' fallito.
         */
        fun da(item: QueueItem?): CoverDownload? = when (item?.state) {
            QueueItem.State.IN_ATTESA -> InAttesa
            QueueItem.State.IN_CORSO, QueueItem.State.AGGIORNO -> InCorso(item.progress)
            else -> null
        }
    }
}

/**
 * L'anello di scaricamento sopra una copertina — lo stesso linguaggio
 * dell'anello di installazione di Android: indeterminato finche' non si
 * sa quanto manca, poi si riempie con la percentuale vera.
 *
 * Va messo come ultimo figlio dentro lo stesso `Box` della copertina:
 * disegna anche una scrim scura sotto di se', cosi' l'anello si legge
 * anche sopra una copertina chiara o ancora vuota.
 */
@Composable
fun CoverDownloadOverlay(
    state: CoverDownload,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // Restava troppo poco visibile: un anello sottile, nella tinta
    // dinamica della copertina, poteva mimetizzarsi proprio con lei —
    // il caso peggiore possibile. Ora ha un contorno chiaro dietro
    // all'arco colorato (la "traccia" del componente, che di suo
    // Material3 non disegna) e un tratto piu' spesso: si legge sopra
    // qualunque copertina, chiara o scura che sia.
    Box(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            CoverDownload.InAttesa -> CircularProgressIndicator(
                color = accent,
                trackColor = Color.White.copy(alpha = 0.35f),
                strokeWidth = 3.dp,
                modifier = Modifier.fillMaxSize(0.62f),
            )
            is CoverDownload.InCorso -> CircularProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                color = accent,
                trackColor = Color.White.copy(alpha = 0.35f),
                strokeWidth = 3.dp,
                modifier = Modifier.fillMaxSize(0.62f),
            )
        }
    }
}
