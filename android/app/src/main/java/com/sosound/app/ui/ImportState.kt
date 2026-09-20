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
    }

    /** Fatto: la playlist esiste e i brani sono in coda. */
    data class Done(val playlistName: String, val queued: Int) : ImportState

    data class Failed(val message: String) : ImportState
}
