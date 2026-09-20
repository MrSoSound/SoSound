package com.sosound.app.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * La configurazione richiesta dal Cast SDK, dichiarata nel manifest.
 *
 * `CC1AD845` e' il ricevitore predefinito di Google: sa riprodurre
 * audio e video senza che si debba registrare e pubblicare una propria
 * applicazione ricevente, cosa che richiederebbe un account sviluppatore
 * Cast e l'approvazione di Google per ogni modifica.
 */
class SoSoundCastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId("CC1AD845")
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    // La notifica la gestiamo noi con la sessione media
                    // che gia' esiste: due notifiche per la stessa
                    // canzone sarebbero una di troppo.
                    .setMediaSessionEnabled(false)
                    .setNotificationOptions(null)
                    .build()
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** Com'e' messa la trasmissione in questo momento. */
enum class StatoCast { NON_DISPONIBILE, NESSUN_DISPOSITIVO, DISPONIBILE, IN_CONNESSIONE, CONNESSO }

/**
 * Tiene d'occhio la trasmissione.
 *
 * Il Cast SDK vive sui Google Play Services: su un telefono che non li
 * ha — o dove sono disattivati — `getSharedInstance` lancia un'eccezione.
 * Va gestita invece che lasciata esplodere: l'app deve funzionare lo
 * stesso, semplicemente senza questa possibilita'.
 */
class CastManager(private val context: Context) {

    private val _stato = MutableStateFlow(StatoCast.NON_DISPONIBILE)
    val stato: StateFlow<StatoCast> = _stato

    private val _dispositivo = MutableStateFlow<String?>(null)
    /** Il nome dell'apparecchio a cui si sta trasmettendo. */
    val dispositivo: StateFlow<String?> = _dispositivo

    var castContext: CastContext? = null
        private set

    /** Chiamato quando la sessione si apre o si chiude. */
    var onSessione: ((CastSession?) -> Unit)? = null

    private val ascoltatore = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = connesso(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = connesso(session)
        override fun onSessionStarting(session: CastSession) {
            _stato.value = StatoCast.IN_CONNESSIONE
        }
        override fun onSessionEnded(session: CastSession, error: Int) = disconnesso()
        override fun onSessionSuspended(session: CastSession, reason: Int) = disconnesso()
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.w(TAG, "connessione fallita: $error")
            disconnesso()
        }
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = disconnesso()
    }

    private fun connesso(session: CastSession) {
        _stato.value = StatoCast.CONNESSO
        _dispositivo.value = session.castDevice?.friendlyName
        onSessione?.invoke(session)
    }

    private fun disconnesso() {
        _stato.value = if (castContext == null) StatoCast.NON_DISPONIBILE
        else aggiornaDaStato(castContext!!.castState)
        _dispositivo.value = null
        onSessione?.invoke(null)
    }

    private fun aggiornaDaStato(s: Int) = when (s) {
        CastState.NO_DEVICES_AVAILABLE -> StatoCast.NESSUN_DISPOSITIVO
        CastState.NOT_CONNECTED -> StatoCast.DISPONIBILE
        CastState.CONNECTING -> StatoCast.IN_CONNESSIONE
        CastState.CONNECTED -> StatoCast.CONNESSO
        else -> StatoCast.NON_DISPONIBILE
    }

    fun inizializza() {
        val ctx = runCatching { CastContext.getSharedInstance(context) }.getOrElse {
            // Niente Play Services, o versione troppo vecchia. Non e' un
            // guasto: e' un telefono su cui la trasmissione non esiste.
            Log.i(TAG, "Cast non disponibile: ${it.message}")
            _stato.value = StatoCast.NON_DISPONIBILE
            return
        }
        castContext = ctx
        _stato.value = aggiornaDaStato(ctx.castState)
        ctx.addCastStateListener { s -> if (_stato.value != StatoCast.CONNESSO) _stato.value = aggiornaDaStato(s) }
        ctx.sessionManager.addSessionManagerListener(ascoltatore, CastSession::class.java)
        ctx.sessionManager.currentCastSession?.let { connesso(it) }
    }

    fun rilascia() {
        castContext?.sessionManager?.removeSessionManagerListener(ascoltatore, CastSession::class.java)
    }

    fun interrompi() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    private companion object {
        const val TAG = "CastManager"
    }
}
