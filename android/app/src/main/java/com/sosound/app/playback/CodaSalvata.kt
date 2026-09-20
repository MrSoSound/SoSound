package com.sosound.app.playback

import android.content.Context

/**
 * La coda di ascolto, scritta su disco per ritrovarla domani.
 *
 * ## Cosa si salva, e cosa no
 *
 * Gli identificativi dei brani, quale si stava ascoltando e a che
 * punto. Non i brani interi: quelli stanno gia' nel database, e
 * duplicarli qui vorrebbe dire tenerne due copie che prima o poi
 * dicono cose diverse.
 *
 * ## Perche' non riparte da sola
 *
 * Ritrovare la coda dov'era e' comodo; ritrovarsi la musica che parte
 * aprendo l'app non lo e'. Si rimette in fila tutto, in pausa, sul
 * brano e sul secondo giusti — poi si decide.
 */
object CodaSalvata {

    private const val ARCHIVIO = "archivio"
    private const val K_BRANI = "coda_brani"
    private const val K_INDICE = "coda_indice"
    private const val K_POSIZIONE = "coda_posizione"
    private const val K_QUANDO = "coda_quando"

    /** Oltre questo, la coda di ieri non interessa piu' a nessuno. */
    private const val SCADENZA_MS = 30L * 24 * 60 * 60 * 1000

    data class Coda(val brani: List<String>, val indice: Int, val posizioneMs: Long)

    private fun prefs(context: Context) =
        context.getSharedPreferences(ARCHIVIO, Context.MODE_PRIVATE)

    fun salva(context: Context, brani: List<String>, indice: Int, posizioneMs: Long) {
        val e = prefs(context).edit()
        if (brani.isEmpty()) {
            // Una coda svuotata e' una decisione, non un'assenza di dati:
            // va scritta, se no domani tornerebbe quella di prima.
            e.remove(K_BRANI).remove(K_INDICE).remove(K_POSIZIONE).remove(K_QUANDO)
        } else {
            e.putString(K_BRANI, brani.joinToString("\n"))
                .putInt(K_INDICE, indice.coerceAtLeast(0))
                .putLong(K_POSIZIONE, posizioneMs.coerceAtLeast(0))
                .putLong(K_QUANDO, System.currentTimeMillis())
        }
        e.apply()
    }

    /**
     * Dove punta l'indice quando qualche brano nel frattempo e' sparito.
     *
     * Se fra ieri e oggi si sono cancellati dei brani, la coda si
     * accorcia e il numero salvato non indica piu' quello giusto:
     * riprenderebbe da un'altra canzone senza che niente lo spieghi.
     * Si conta quanti dei brani fino a quello corrente sono
     * sopravvissuti — quello e' il suo posto nuovo.
     */
    fun indiceDopoLaPotatura(
        salvati: List<String>,
        sopravvissuti: Set<String>,
        indice: Int,
    ): Int {
        if (salvati.isEmpty() || sopravvissuti.isEmpty()) return 0
        val fino = indice.coerceIn(0, salvati.size - 1)
        val quanti = salvati.take(fino + 1).count { it in sopravvissuti }
        // Se e' sparito proprio quello corrente, si riprende dal
        // precedente rimasto: e' l'unico posto sensato.
        return (quanti - 1).coerceAtLeast(0)
    }

    fun leggi(context: Context): Coda? {
        val p = prefs(context)
        val testo = p.getString(K_BRANI, null)?.takeIf { it.isNotBlank() } ?: return null
        val quando = p.getLong(K_QUANDO, 0)
        if (quando > 0 && System.currentTimeMillis() - quando > SCADENZA_MS) return null
        val brani = testo.split("\n").filter { it.isNotBlank() }
        if (brani.isEmpty()) return null
        return Coda(
            brani = brani,
            indice = p.getInt(K_INDICE, 0).coerceIn(0, brani.size - 1),
            posizioneMs = p.getLong(K_POSIZIONE, 0),
        )
    }
}
