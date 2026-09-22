package com.sosound.app.data.catalog

import com.sosound.app.data.importing.TrackMatcher

/**
 * Fra due risultati duplicati — stesso brano, uno "pulito" e uno
 * esplicito — porta avanti quello esplicito.
 *
 * Capita perche' YouTube Music indicizza entrambe le incisioni quando
 * esistono: una censurata per radio e negozi, l'altra quella vera. Chi
 * cerca si aspetta la seconda, e trovarla in fondo alla lista (o fuori
 * dal limite dei risultati mostrati) e' un fastidio piccolo ma inutile,
 * visto che sappiamo gia' quale delle due preferire.
 *
 * Non e' un riordino globale: individua le coppie che sono davvero lo
 * stesso brano (titolo e artista normalizzati uguali — le stesse regole
 * di [TrackMatcher], per non reinventare cosa conta come "rumore" nel
 * titolo) e dentro ciascuna sposta l'esplicito nella posizione piu' alta
 * fra quelle occupate dal gruppo, con uno stable-sort mirato: l'ordine
 * fra brani che non sono duplicati fra loro non cambia.
 */
fun prioritizeExplicitDuplicates(risultati: List<CatalogTrack>): List<CatalogTrack> {
    fun chiave(t: CatalogTrack) = TrackMatcher.tokens(t.title) to TrackMatcher.tokens(t.artist)

    val gruppi = risultati.indices.groupBy { chiave(risultati[it]) }
    val out = risultati.toMutableList()

    gruppi.values.forEach { indici ->
        if (indici.size < 2) return@forEach
        val posizioni = indici.sorted()
        val originali = posizioni.map { out[it] }

        // Si tocca il gruppo solo se e' un vero mix di esplicito e non:
        // tutti uguali (entrambi espliciti, entrambi no, o non lo
        // sappiamo) vuol dire che non c'e' niente da decidere qui.
        val haEsplicito = originali.any { it.explicit == true }
        val haNonEsplicito = originali.any { it.explicit != true }
        if (!haEsplicito || !haNonEsplicito) return@forEach

        // sortedByDescending e' stabile: a parita' di stato (fra i due
        // espliciti, o fra i due non espliciti) l'ordine originale non
        // si muove — cambia solo chi sta sopra chi.
        val riordinati = originali.sortedByDescending { it.explicit == true }
        posizioni.forEachIndexed { i, pos -> out[pos] = riordinati[i] }
    }

    return out
}
