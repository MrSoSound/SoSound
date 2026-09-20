package com.sosound.app.cast.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket

/**
 * Trova i dispositivi DLNA sulla rete e ci parla.
 *
 * La scoperta e' un grido e un'attesa: si manda un M-SEARCH
 * all'indirizzo multicast e si raccoglie chi risponde per qualche
 * secondo. Non c'e' un elenco da interrogare — in UPnP sono i
 * dispositivi a farsi vivi.
 */
class DlnaNetwork(private val context: Context? = null) {

    private val http = HttpClient(OkHttp) { expectSuccess = false }

    /**
     * Sblocca la ricezione dei pacchetti multicast per il tempo della
     * ricerca, e la richiude subito dopo.
     *
     * Per risparmiare batteria il Wi-Fi di Android scarta i pacchetti non
     * indirizzati al telefono, e la scoperta UPnP vive esattamente di
     * quelli. Il lucchetto va rilasciato: tenerlo aperto tiene sveglia
     * la radio.
     */
    private fun <T> conMulticast(blocco: () -> T): T {
        val lock = runCatching {
            (context?.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createMulticastLock("sosound-dlna")
                ?.apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        return try { blocco() } finally { runCatching { lock?.release() } }
    }

    /**
     * Cerca per [secondi] e torna quello che ha trovato.
     *
     * Il messaggio si manda piu' volte: UDP non garantisce la consegna, e
     * su una rete Wi-Fi affollata il primo pacchetto si perde spesso.
     * Tre tentativi sono il compromesso che usano tutte le app di questo
     * tipo.
     */
    suspend fun cerca(secondi: Int = 4): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val trovati = LinkedHashMap<String, DlnaDevice>()
        val indirizzi = mutableSetOf<String>()

        conMulticast {
          runCatching {
            MulticastSocket().use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = 900
                val gruppo = InetAddress.getByName(Dlna.MULTICAST)
                val messaggio = Dlna.messaggioRicerca(secondi - 1).toByteArray()

                repeat(3) {
                    runCatching {
                        socket.send(DatagramPacket(messaggio, messaggio.size,
                            InetSocketAddress(gruppo, Dlna.PORTA)))
                    }
                }

                val fine = System.currentTimeMillis() + secondi * 1000L
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() < fine) {
                    val pacchetto = DatagramPacket(buffer, buffer.size)
                    val ok = runCatching { socket.receive(pacchetto); true }.getOrDefault(false)
                    if (!ok) continue
                    val testo = String(pacchetto.data, 0, pacchetto.length)
                    Dlna.locationDiRisposta(testo)?.let { indirizzi += it }
                }
            }
          }.onFailure { Log.w(TAG, "ricerca fallita: ${it.message}") }
        }

        // Le schede si scaricano dopo, non dentro il ciclo di ascolto:
        // fermarsi a fare una richiesta HTTP mentre arrivano i pacchetti
        // vuol dire perdere quelli che arrivano nel frattempo.
        for (url in indirizzi) {
            // Chi ha risposto puo' aver scritto qualunque indirizzo: si
            // esce dalla rete locale solo se ce lo si lascia fare.
            if (!Dlna.eLocale(url)) {
                Log.w(TAG, "risposta con indirizzo non locale, ignorata: $url")
                continue
            }
            val xml = withTimeoutOrNull(3000) {
                runCatching { http.get(url).bodyAsText() }.getOrNull()
            } ?: continue
            Dlna.leggiDescrizione(xml, url)?.let { trovati[it.udn] = it }
        }

        trovati.values.toList()
    }

    /** Manda un comando e torna la risposta, o null se non ha funzionato. */
    suspend fun comando(device: DlnaDevice, azione: String, busta: String): String? =
        withContext(Dispatchers.IO) {
            if (!Dlna.eLocale(device.controlUrl)) return@withContext null
            runCatching {
                val r = http.post(device.controlUrl) {
                    header("SOAPAction", Dlna.azioneSoap(azione))
                    header("Content-Type", "text/xml; charset=\"utf-8\"")
                    // Alcuni apparecchi rifiutano le richieste senza
                    // Connection esplicito, ed e' un rifiuto muto.
                    header("Connection", "close")
                    setBody(busta)
                }
                val corpo = r.bodyAsText()
                if (r.status.value >= 400) {
                    Log.w(TAG, "$azione -> ${r.status.value}: ${corpo.take(180)}")
                    null
                } else corpo
            }.getOrElse {
                Log.w(TAG, "$azione non riuscita: ${it.message}")
                null
            }
        }

    suspend fun riproduci(d: DlnaDevice, url: String, titolo: String, artista: String, mime: String): Boolean {
        // L'ordine conta: prima si dice cosa, poi si dice di suonare.
        // Invertendoli il renderer suona quello di prima, o niente.
        val impostato = comando(d, "SetAVTransportURI",
            Dlna.setUri(url, Dlna.didl(titolo, artista, url, mime))) != null
        if (!impostato) return false
        return comando(d, "Play", Dlna.play()) != null
    }

    suspend fun pausa(d: DlnaDevice) = comando(d, "Pause", Dlna.pausa()) != null
    suspend fun riprendi(d: DlnaDevice) = comando(d, "Play", Dlna.play()) != null
    suspend fun ferma(d: DlnaDevice) = comando(d, "Stop", Dlna.stop()) != null
    suspend fun vaiA(d: DlnaDevice, secondi: Long) =
        comando(d, "Seek", Dlna.seek(secondi)) != null

    /** Posizione e durata, in secondi. -1 dove l'apparecchio non risponde. */
    suspend fun posizione(d: DlnaDevice): Pair<Long, Long> {
        val r = comando(d, "GetPositionInfo", Dlna.posizione()) ?: return -1L to -1L
        return Dlna.secondiDa(Dlna.campo(r, "RelTime")) to
            Dlna.secondiDa(Dlna.campo(r, "TrackDuration"))
    }

    /** PLAYING, PAUSED_PLAYBACK, STOPPED, TRANSITIONING… */
    suspend fun stato(d: DlnaDevice): String? =
        comando(d, "GetTransportInfo", Dlna.statoTrasporto())
            ?.let { Dlna.campo(it, "CurrentTransportState") }

    fun chiudi() = http.close()

    private companion object {
        const val TAG = "DlnaNetwork"
    }
}
