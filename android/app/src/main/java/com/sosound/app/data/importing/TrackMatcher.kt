package com.sosound.app.data.importing

import com.sosound.app.data.catalog.CatalogTrack
import java.text.Normalizer

/** Quanto ci fidiamo dell'abbinamento trovato. */
enum class Confidence { SICURO, INCERTO, NON_TROVATO }

data class MatchResult(
    val row: ImportRow,
    val track: CatalogTrack?,
    val score: Double,
    val confidence: Confidence,
    /** Le altre possibilita', se l'utente vuole correggere. */
    val alternatives: List<CatalogTrack> = emptyList(),
)

/**
 * Decide quale risultato del catalogo corrisponde a una riga importata.
 *
 * Il problema non e' cercare — e' che la stessa canzone si chiama in modi
 * diversi da una parte e dall'altra: «Song (Remastered 2011)» contro
 * «Song», «Artist feat. Tizio» contro «Artist, Tizio», maiuscole e
 * accenti diversi. Un confronto letterale fallirebbe quasi sempre.
 *
 * Per questo si confrontano **insiemi di parole normalizzate**, e non
 * stringhe: cosi' l'ordine non conta, le parole in piu' pesano poco, e
 * quello che resta e' se si sta parlando della stessa canzone.
 */
object TrackMatcher {

    const val SOGLIA_SICURO = 0.78
    const val SOGLIA_INCERTO = 0.45

    /**
     * Parole che compaiono da una parte e non dall'altra senza cambiare
     * di quale canzone si parla. Toglierle evita di penalizzare
     * abbinamenti giusti.
     */
    private val RUMORE = setOf(
        "remaster", "remastered", "remasterizzato", "version", "versione",
        "official", "ufficiale", "audio", "video", "lyrics", "hd", "hq", "4k",
        "feat", "ft", "featuring", "con", "with",
        "radio", "edit", "single", "album", "mix", "original", "originale",
        "explicit", "clean", "bonus", "track", "deluxe", "anniversary",
        "live", "stereo", "mono",
    )

    /**
     * Parole che invece indicano una registrazione DIVERSA.
     *
     * Non sono rumore: un remix o una cover sono un'altra cosa rispetto
     * a quello che c'era nella playlist esportata. Senza questa lista,
     * cercando «Get Lucky» vinceva «Get Lucky (Daft Punk Remix)» con il
     * punteggio pieno — tutte le parole cercate c'erano.
     */
    private val ALTRA_VERSIONE = setOf(
        "remix", "cover", "karaoke", "strumentale", "instrumental",
        "tribute", "tributo", "nightcore", "8d", "sped", "slowed",
        "reverb", "mashup", "medley", "parodia", "parody", "reaction",
    )

    /** Toglie accenti, punteggiatura e maiuscole. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val ANNO = Regex("^(19|20)\\d{2}$")

    fun tokens(s: String): Set<String> {
        val grezzi = normalize(s).split(" ").filter { it.isNotBlank() }
        // Un anno e' identita' del brano in «1999» di Prince, ma e'
        // un'etichetta di versione in «Remastered 2011». Si butta via
        // solo quando accanto c'e' una parola di rumore, che e' il
        // segnale che stiamo leggendo un'etichetta.
        val etichetta = grezzi.any { it in RUMORE }
        return grezzi
            .filter { it !in RUMORE }
            .filterNot { etichetta && ANNO.matches(it) }
            .toSet()
    }

    /** Parole del risultato che dicono «questa e' un'altra incisione». */
    fun altraVersione(candidate: CatalogTrack, row: ImportRow): Boolean {
        val volute = normalize(row.title).split(" ").toSet()
        return normalize(candidate.title).split(" ")
            // Se era la playlist a chiedere un remix, allora il remix va
            // benissimo: la penalita' scatta solo se non l'abbiamo chiesto.
            .any { it in ALTRA_VERSIONE && it !in volute }
    }

    /**
     * Quanto di [atteso] si ritrova in [trovato], da 0 a 1.
     *
     * Asimmetrica di proposito: contano le parole che cercavamo e non
     * abbiamo trovato. Che il risultato ne abbia in piu' — «(Live at
     * Wembley)» — e' normale e non deve penalizzarlo.
     */
    fun coverage(atteso: Set<String>, trovato: Set<String>): Double {
        if (atteso.isEmpty()) return 0.0
        return atteso.count { it in trovato }.toDouble() / atteso.size
    }

    fun score(row: ImportRow, candidate: CatalogTrack): Double {
        val titoloAtteso = tokens(row.title)
        val titoloTrovato = tokens(candidate.title)
        val titolo = coverage(titoloAtteso, titoloTrovato)

        // Senza artista nella riga importata (testo incollato senza
        // separatore) si giudica sul solo titolo, ma con un tetto piu'
        // basso: la certezza non c'e'. La durata, quando c'e', resta
        // comunque un aiuto — anzi qui vale piu' che altrove, perche' non
        // c'e' nient'altro con cui distinguere due omonimi.
        if (row.artist.isBlank()) {
            return (
                titolo * 0.75 +
                    durationAdjustment(row, candidate) +
                    explicitAdjustment(row, candidate)
                ).coerceIn(0.0, 1.0)
        }

        val artistaAtteso = tokens(row.artist)
        val artistaTrovato = tokens(candidate.artist)
        val artista = coverage(artistaAtteso, artistaTrovato)

        // Il titolo pesa piu' dell'artista: un artista scritto in modo
        // diverso capita spesso, un titolo diverso quasi mai.
        var punteggio = 0.65 * titolo + 0.35 * artista

        // Un risultato molto piu' lungo del titolo cercato e' sospetto:
        // di solito e' un mix, un mashup o un'ora di musica continua.
        val extra = titoloTrovato.size - titoloAtteso.size
        if (extra > 3) punteggio -= 0.06 * (extra - 3)

        // Un'altra incisione non e' il brano che stavi importando.
        // La penalita' e' pesante di proposito: meglio farlo rivedere
        // che riempire la playlist di remix al posto degli originali.
        if (altraVersione(candidate, row)) punteggio -= 0.35

        punteggio += durationAdjustment(row, candidate)
        punteggio += explicitAdjustment(row, candidate)

        return punteggio.coerceIn(0.0, 1.0)
    }

    /**
     * Quanto la durata avvicina o allontana un candidato, quando la
     * conosciamo da entrambe le parti.
     *
     * E' l'unico parametro, oltre a titolo e artista, che riusciamo a
     * tirare fuori da un'importazione Spotify (link o CSV esportato): non
     * un identificativo — un ISRC risolverebbe la questione da solo, ma
     * Spotify lo espone solo dietro API con app registrata, e YouTube
     * Music non lo restituisce nei risultati di ricerca, quindi non ci
     * sarebbe comunque nulla da confrontarlo. La durata invece la danno
     * entrambi i cataloghi gratis, ed e' quasi quanto un'impronta: due
     * incisioni diverse della stessa canzone — radio edit, versione da
     * album, live — quasi sempre durano un tempo diverso anche quando si
     * chiamano allo stesso identico modo.
     */
    fun durationAdjustment(row: ImportRow, candidate: CatalogTrack): Double {
        val attesa = row.durationSeconds ?: return 0.0
        val trovata = candidate.durationSeconds ?: return 0.0
        val scarto = kotlin.math.abs(attesa - trovata)
        return when {
            scarto <= 2 -> 0.10
            scarto <= 5 -> 0.04
            // Fino a una decina di secondi capita anche fra due rip dello
            // stesso brano: non e' un segnale, ne' a favore ne' contro.
            scarto <= 10 -> 0.0
            scarto <= 25 -> -0.15
            else -> -0.30
        }
    }

    /**
     * Piccolo aggiustamento quando lo stato "esplicito" lo sappiamo da
     * entrambe le parti.
     *
     * Piu' leggero della durata di proposito: qui capita spesso di non
     * saperlo affatto (un CSV vecchio, un elenco incollato), e quando lo
     * sappiamo e' un indizio in piu' per scegliere fra due candidati
     * altrimenti identici — mai abbastanza forte da ribaltare da solo un
     * abbinamento chiaramente sbagliato.
     */
    fun explicitAdjustment(row: ImportRow, candidate: CatalogTrack): Double {
        val atteso = row.explicit ?: return 0.0
        val trovato = candidate.explicit ?: return 0.0
        return if (atteso == trovato) 0.05 else -0.025
    }

    fun match(row: ImportRow, candidates: List<CatalogTrack>): MatchResult {
        if (candidates.isEmpty()) {
            return MatchResult(row, null, 0.0, Confidence.NON_TROVATO)
        }
        val ordinati = candidates
            .map { it to score(row, it) }
            .sortedByDescending { it.second }

        val (migliore, punteggio) = ordinati.first()
        val confidenza = when {
            punteggio >= SOGLIA_SICURO -> Confidence.SICURO
            punteggio >= SOGLIA_INCERTO -> Confidence.INCERTO
            else -> Confidence.NON_TROVATO
        }
        return MatchResult(
            row = row,
            track = if (confidenza == Confidence.NON_TROVATO) null else migliore,
            score = punteggio,
            confidence = confidenza,
            alternatives = ordinati.drop(1).take(4).map { it.first },
        )
    }

    /** La stringa da cercare sul catalogo. */
    fun query(row: ImportRow): String =
        listOf(row.title, row.artist).filter { it.isNotBlank() }.joinToString(" ")
}
