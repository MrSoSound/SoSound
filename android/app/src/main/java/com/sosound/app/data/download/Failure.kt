package com.sosound.app.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Perche' un download e' fallito.
 *
 * La distinzione che conta e' fra «la rete non c'e'» e «yt-dlp e'
 * disallineato da YouTube»: producono errori che si somigliano, ma la
 * prima non si risolve aggiornando niente, e provarci sarebbe solo un
 * modo per girare a vuoto.
 */
enum class FailureKind {
    /** Il telefono non ha rete. Aggiornare non serve a niente. */
    RETE_ASSENTE,

    /** La rete c'e' ma la richiesta e' fallita, o l'estrazione non riesce.
     *  E' il caso in cui vale la pena provare ad aggiornare yt-dlp. */
    MOTORE_DISALLINEATO,

    /** YouTube chiede una conferma anti-bot. Aggiornare non risolve: e'
     *  una decisione loro sulla connessione, non un difetto del codice. */
    ANTIBOT,

    /** Il brano non c'e', e' privato, o ha restrizioni. Riprovare e'
     *  inutile: il problema non passa da solo. */
    CONTENUTO,
}

class DownloadError(
    message: String,
    val kind: FailureKind,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Se ha senso ritentare dopo aver aggiornato il motore. */
    val worthUpdating: Boolean get() = kind == FailureKind.MOTORE_DISALLINEATO
}

/** C'e' una rete utilizzabile in questo momento? */
fun Context.hasNetwork(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true      // nel dubbio si prova: meglio un errore vero che un falso allarme
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
