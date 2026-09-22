package com.sosound.app.ui

import com.sosound.app.data.library.TrackEntity

/**
 * Unisce piu' playlist in un'unica coda, mischiata davvero.
 *
 * È di proposito diverso da «Ascolta tutto a caso» (vedi
 * [MainViewModel.shufflePlay] e il README, sezione «Riproduzione casuale e
 * ripetizione»): li' la lista resta nell'ordine vero e a mischiare e' lo
 * shuffle di ExoPlayer, cosi' spegnendo il casuale si torna all'ordine di
 * partenza. Qui invece non esiste un ordine di partenza da preservare:
 * l'operazione stessa e' «unisci e mischia», decisa una volta alla
 * creazione del mix. Per questo la lista nasce gia' mischiata (con
 * [shuffled]) invece di affidare il rimescolamento al player.
 *
 * Duplicati: se lo stesso brano (stesso [TrackEntity.videoId]) sta in piu'
 * playlist scelte, di default viene tenuto una sola volta — risentirlo due
 * volte in un mix "a sorpresa" e' un'esperienza peggiore che perderne una
 * copia. Chi rivede questa scelta e preferisce il contrario (tenere i
 * duplicati, che pesano implicitamente i brani che stanno in piu' playlist)
 * puo' semplicemente passare `dedupe = false`.
 */
fun mixPlaylists(
    playlists: List<List<TrackEntity>>,
    dedupe: Boolean = true,
): List<TrackEntity> {
    val unite = playlists.flatten()
    val sorgente = if (dedupe) unite.distinctBy { it.videoId } else unite
    return sorgente.shuffled()
}
