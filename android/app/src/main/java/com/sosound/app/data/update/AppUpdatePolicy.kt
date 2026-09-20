package com.sosound.app.data.update

import android.content.Context

/**
 * Quando controllare se c'e' una versione nuova, e quali versioni non
 * riproporre.
 *
 * Molto piu' semplice del freno di [com.sosound.app.data.download.EngineUpdatePolicy]:
 * li' un aggiornamento che fallisce si ripete a ogni brano, qui il
 * controllo parte al massimo una volta per apertura e non ha un ciclo
 * da spezzare. Serve solo a non controllare la rete a ogni avvio.
 */
class AppUpdatePolicy(context: Context) {

    private val prefs = context.getSharedPreferences("aggiornamenti_app", Context.MODE_PRIVATE)

    /** Una volta per processo: non ricontrolla passando da una schermata
     *  all'altra, solo alla prima apertura. */
    @Volatile private var checkedThisSession = false

    fun shouldCheckOnStart(): Boolean {
        if (checkedThisSession) return false
        return System.currentTimeMillis() - prefs.getLong(KEY_LAST, 0L) > GIORNO
    }

    fun markChecked() {
        checkedThisSession = true
        prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    /** L'utente ha scelto di non installare questa versione ora: il
     *  badge non deve ripresentargliela a ogni apertura, ma sparisce da
     *  solo appena ne esce una piu' nuova, perche' il confronto e' sul
     *  tag esatto. */
    fun skip(tagName: String) {
        prefs.edit().putString(KEY_SKIPPED, tagName).apply()
    }

    fun isSkipped(tagName: String): Boolean = prefs.getString(KEY_SKIPPED, null) == tagName

    private companion object {
        const val KEY_LAST = "ultimo_controllo"
        const val KEY_SKIPPED = "versione_saltata"
        const val GIORNO = 24 * 60 * 60 * 1000L
    }
}
