package com.sosound.app.ui

import com.sosound.app.data.catalog.CatalogTrack
import com.sosound.app.data.importing.Confidence
import com.sosound.app.data.importing.ImportRow

/** Una riga importata, con l'abbinamento trovato e la scelta dell'utente. */
data class ImportEntry(
    val row: ImportRow,
    val match: CatalogTrack?,
    val confidence: Confidence,
    val score: Double,
    val alternatives: List<CatalogTrack>,
    /** Se finira' nella playlist. Le righe incerte partono escluse. */
    val selected: Boolean,
    /** Ce l'hai gia': non va riscaricato, solo aggiunto. */
    val alreadyOwned: Boolean = false,
)

/** Dove siamo nella procedura di importazione. */
sealed interface ImportState {
    /** Scegli un file o incolla un elenco. */
    data object Idle : ImportState

    /** Sto cercando i brani sul catalogo. */
    data class Working(val done: Int, val total: Int) : ImportState

    /** Ecco cosa ho trovato: controlla e conferma. */
    data class Review(
        val name: String,
        val entries: List<ImportEntry>,
    ) : ImportState {
        val selectedCount: Int get() = entries.count { it.selected }
        val sure: Int get() = entries.count { it.confidence == Confidence.SICURO }
        val unsure: Int get() = entries.count { it.confidence == Confidence.INCERTO }
        val missing: Int get() = entries.count { it.confidence == Confidence.NON_TROVATO }

        /**
         * Righe il cui abbinamento e' l'audio di un video, non
         * un'incisione pensata per l'ascolto.
         *
         * Capita solo importando da un link vero (YouTube, YouTube Music),
         * dove il brano non e' cercato ma gia' identificato con certezza:
         * li' non si puo' scartarlo in partenza come fa la ricerca, ma
         * l'utente va avvisato e messo in condizione di sostituirlo.
         */
        val daVideo: List<Int> get() = entries.mapIndexedNotNull { i, e ->
            i.takeIf { e.match?.audioDiVideo == true }
        }

        /** Fra le righe segnate [daVideo], quante hanno gia' pronta un'alternativa pulita. */
        val daVideoSostituibili: Int get() = daVideo.count { i ->
            entries[i].alternatives.any { !it.audioDiVideo }
        }
    }

    /** Fatto: la playlist esiste e i brani sono in coda. */
    data class Done(val playlistName: String, val queued: Int) : ImportState

    data class Failed(val message: String) : ImportState
}

/**
 * Quanto urge guardare una riga con questa confidenza: piu' basso, piu'
 * urgente. Serve a mettere in cima le righe che hanno bisogno di una
 * persona, invece di lasciarle sparse nell'ordine del file originale.
 */
fun Confidence.priorita(): Int = when (this) {
    Confidence.NON_TROVATO -> 0
    Confidence.INCERTO -> 1
    Confidence.SICURO -> 2
}

/**
 * Rimette in ordine le righe dell'import: prima quelle non trovate, poi
 * quelle incerte, infine quelle sicure — cosi' chi importa vede subito
 * cosa va rivisto, senza scorrere fino in fondo.
 *
 * `sortedBy` di Kotlin e' stabile: dentro ogni gruppo di confidenza
 * uguale, l'ordine relativo del file originale non cambia.
 */
fun List<ImportEntry>.ordinataPerRevisione(): List<ImportEntry> =
    sortedBy { it.confidence.priorita() }
