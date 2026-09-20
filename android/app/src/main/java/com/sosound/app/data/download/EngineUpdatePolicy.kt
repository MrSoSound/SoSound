package com.sosound.app.data.download

import android.content.Context

/**
 * Decide se conviene tentare un aggiornamento automatico di yt-dlp.
 *
 * Il rischio che questa classe esiste per scongiurare: un errore che si
 * ripresenta a ogni brano, e a ogni brano fa partire un aggiornamento.
 * Con la rete lenta o assente diventerebbe un ciclo che consuma batteria
 * e dati senza mai concludere niente.
 *
 * Tre freni, e servono tutti e tre:
 *
 *  1. **uno per sessione** — se l'aggiornamento e' gia' stato tentato da
 *     quando l'app e' aperta, non si ritenta. Copre il caso di dieci
 *     brani in coda che falliscono tutti per lo stesso motivo.
 *  2. **una volta ogni sei ore** — sopravvive alla chiusura dell'app.
 *     Senza, basterebbe riaprirla per ricominciare da capo.
 *  3. **un solo ritentativo per brano** — anche se l'aggiornamento
 *     riesce, il brano si riprova una volta sola. Se fallisce ancora, il
 *     problema e' un altro.
 */
class EngineUpdatePolicy(context: Context) {

    private val prefs = context.getSharedPreferences("motore", Context.MODE_PRIVATE)

    /** Freno 1: vale finche' il processo vive. */
    @Volatile private var triedThisSession = false

    fun shouldTryUpdate(): Boolean {
        if (triedThisSession) return false
        val last = prefs.getLong(KEY_LAST, 0L)
        return System.currentTimeMillis() - last > COOLDOWN_MS
    }

    /** Da chiamare appena si tenta, non quando riesce: un aggiornamento
     *  che fallisce e' esattamente quello che non va ripetuto in ciclo. */
    fun markAttempt() {
        triedThisSession = true
        prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    /**
     * Se tocca fare l'aggiornamento d'avvio.
     *
     * Una volta al giorno: yt-dlp non esce piu' spesso di cosi', e
     * ricontrollare a ogni apertura vorrebbe dire una richiesta di rete
     * ogni volta che si apre l'app per far partire una canzone.
     */
    fun shouldUpdateOnStart(): Boolean =
        System.currentTimeMillis() - prefs.getLong(KEY_AVVIO, 0L) > GIORNO

    fun markStartUpdate() {
        prefs.edit().putLong(KEY_AVVIO, System.currentTimeMillis()).apply()
    }

    /** Quando l'utente aggiorna a mano, il contatore riparte: ha appena
     *  dimostrato di volerlo, e il freno automatico non deve intralciarlo. */
    fun reset() {
        triedThisSession = false
        prefs.edit().remove(KEY_LAST).apply()
    }

    private companion object {
        const val KEY_LAST = "ultimo_tentativo"
        const val KEY_AVVIO = "ultimo_avvio"
        const val GIORNO = 24 * 60 * 60 * 1000L
        const val COOLDOWN_MS = 6 * 60 * 60 * 1000L   // sei ore
    }
}
