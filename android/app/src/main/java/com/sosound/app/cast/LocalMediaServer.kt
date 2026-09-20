package com.sosound.app.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import com.sosound.app.data.library.TrackDao
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Un server HTTP minuscolo che gira sul telefono.
 *
 * ## Perche' serve
 *
 * Un ricevitore Cast — la TV, l'altoparlante — **non legge i file del
 * telefono**: riceve un indirizzo e lo scarica da se'. I nostri brani
 * stanno nello spazio privato dell'app o dentro un `content://`, e
 * nessuno dei due e' raggiungibile da un altro apparecchio.
 *
 * Quindi finche' si trasmette, il telefono fa da sorgente: pubblica
 * ogni brano su `http://<ip-del-telefono>:8474/t/<videoId>`, sulla rete
 * locale e solo mentre serve.
 *
 * ## Chi puo' chiedere
 *
 * Non e' esposto a Internet — ascolta su un indirizzo privato, che
 * esiste solo dentro la propria rete — ma «dentro la propria rete» non
 * vuol dire «solo io». Su un Wi-Fi d'albergo, d'ufficio o di un bar
 * c'e' dentro chiunque altro, e i nomi dei brani non sono segreti: sono
 * gli stessi identificativi pubblici di YouTube, quindi indovinabili.
 * Senza un controllo, per il tempo della trasmissione la propria
 * libreria sarebbe scaricabile da ogni apparecchio collegato.
 *
 * Quindi ogni indirizzo porta con se' una parola d'ordine di 128 bit,
 * sorteggiata a ogni accensione: `/t/<parola>/<brano>`. Chi la conosce
 * e' chi l'ha ricevuta da noi — il ricevitore Cast o la TV — e nessun
 * altro. Non e' crittografia: e' la differenza fra una porta chiusa e
 * una porta aperta.
 */
class LocalMediaServer(
    private val context: Context,
    private val tracks: TrackDao,
) : NanoHTTPD(PORTA) {

    /**
     * Sorteggiata a ogni accensione, quindi nuova a ogni trasmissione:
     * un indirizzo copiato ieri non vale piu'.
     */
    @Volatile
    private var parola: String = nuovaParola()

    override fun serve(session: IHTTPSession): Response {
        val percorso = session.uri.orEmpty()
        if (!percorso.startsWith("/t/")) return notFound()

        val pezzi = percorso.removePrefix("/t/").substringBefore('?').split('/')
        if (pezzi.size != 2) return notFound()
        val (dato, videoId) = pezzi

        // Confronto a tempo costante: con `==` il tempo di risposta
        // dipende da quante lettere iniziali sono giuste, e da li' la
        // parola si ricostruisce una lettera per volta.
        if (!ugualiATempoCostante(dato, parola)) return notFound()
        if (!videoIdValido(videoId)) return notFound()

        val track = runCatching { runBlocking { tracks.byId(videoId) } }.getOrNull()
            ?: return notFound()

        val lunghezza = track.sizeBytes.takeIf { it > 0 } ?: dimensione(track.path)
        val mime = mimeDi(track.path)

        // L'intervallo va rispettato: senza, spostarsi dentro un brano
        // dal telecomando della TV non funziona, perche' il ricevitore
        // chiede «dammi da questo byte in poi» e noi gli ridaremmo tutto
        // dall'inizio.
        val range = session.headers["range"]
        val inizio = range?.let { RANGE.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L

        val stream = apri(track.path) ?: return notFound()
        if (inizio > 0) stream.skip(inizio)

        val restante = (lunghezza - inizio).coerceAtLeast(0)
        val risposta = newFixedLengthResponse(
            if (inizio > 0) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            mime, stream, restante,
        )
        risposta.addHeader("Accept-Ranges", "bytes")
        if (inizio > 0) {
            risposta.addHeader("Content-Range", "bytes $inizio-${lunghezza - 1}/$lunghezza")
        }
        return risposta
    }

    private fun notFound() =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")

    private fun apri(posizione: String): InputStream? = runCatching {
        if (posizione.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(posizione))
        } else {
            File(posizione).inputStream()
        }
    }.getOrNull()

    private fun dimensione(posizione: String): Long = runCatching {
        if (posizione.startsWith("content://")) {
            context.contentResolver.openFileDescriptor(Uri.parse(posizione), "r")
                ?.use { it.statSize } ?: 0L
        } else {
            File(posizione).length()
        }
    }.getOrDefault(0L)

    fun avvia(): Boolean = runCatching {
        if (!isAlive) {
            parola = nuovaParola()
            start(SOCKET_READ_TIMEOUT, false)
        }
        true
    }.getOrElse {
        Log.e(TAG, "server non avviato", it)
        false
    }

    fun ferma() = runCatching { stop() }.getOrDefault(Unit)

    /** L'indirizzo da dare al ricevitore per un brano. */
    fun urlDi(videoId: String): String? =
        if (!videoIdValido(videoId)) null
        else indirizzoLocale(context)?.let { "http://$it:$PORTA/t/$parola/$videoId" }

    companion object {
        /** Una porta alta e poco frequentata: sotto 1024 servirebbero permessi. */
        const val PORTA = 8474
        private const val TAG = "LocalMediaServer"
        private val RANGE = Regex("""bytes=(\d+)-""")

        fun mimeDi(posizione: String) = when (posizione.substringAfterLast('.', "").lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "webm" -> "audio/webm"
            "opus" -> "audio/ogg"
            "mp3" -> "audio/mpeg"
            else -> "audio/*"
        }

        /**
         * L'indirizzo del telefono sulla rete locale.
         *
         * Si scorrono le interfacce invece di chiedere al WifiManager:
         * quello risponde solo per il Wi-Fi, e un telefono collegato via
         * Ethernet o che fa da hotspot resterebbe senza indirizzo.
         */
        fun indirizzoLocale(context: Context): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        fun nuovaParola(): String {
            // SecureRandom e non Random: il secondo parte da un seme
            // prevedibile, e una parola d'ordine indovinabile non e' una
            // parola d'ordine.
            val b = ByteArray(16)
            java.security.SecureRandom().nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }

        fun ugualiATempoCostante(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var diff = 0
            for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
            return diff == 0
        }

        /**
         * Gli identificativi di YouTube sono undici caratteri e nient'altro.
         *
         * Il controllo vale poco qui — la ricerca nel database fallirebbe
         * comunque — e molto altrove: lo stesso valore finisce dentro un
         * indirizzo passato a yt-dlp, e li' un contenuto inatteso vuol
         * dire far scaricare qualcosa di diverso da quello che si e'
         * chiesto.
         */
        fun videoIdValido(id: String) = ID.matches(id)

        private val ID = Regex("""^[A-Za-z0-9_-]{11}$""")
    }
}
