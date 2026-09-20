package com.sosound.app.cast.dlna

import java.io.StringReader
import java.net.URI

/**
 * Un apparecchio che sa riprodurre audio ricevuto dalla rete.
 *
 * In gergo UPnP si chiama MediaRenderer: e' quello che fanno le TV
 * Samsung e LG, i ricevitori AV, molti impianti e i box multimediali —
 * tutta la fascia che non parla Cast.
 */
data class DlnaDevice(
    val udn: String,
    val nome: String,
    val costruttore: String? = null,
    /** L'indirizzo a cui mandare i comandi di riproduzione. */
    val controlUrl: String,
    val indirizzo: String,
)

/**
 * Il minimo indispensabile del protocollo UPnP, scritto a mano.
 *
 * ## Perche' non una libreria
 *
 * Le librerie UPnP complete implementano anche l'**ospitare** dispositivi,
 * la registrazione, gli eventi GENA e un ciclo di vita pensato per i
 * server. A noi servono due cose: trovare chi sa suonare, e mandargli sei
 * comandi. Il resto sarebbe quasi un megabyte di codice mai eseguito.
 *
 * ## Come funziona
 *
 * 1. si grida sulla rete locale «chi e' un MediaRenderer?» (SSDP, UDP
 *    multicast su 239.255.255.250:1900);
 * 2. chi risponde manda l'indirizzo di una scheda XML che lo descrive;
 * 3. dentro la scheda c'e' l'indirizzo del servizio AVTransport, a cui
 *    si mandano i comandi come buste SOAP.
 */
object Dlna {

    const val MULTICAST = "239.255.255.250"
    const val PORTA = 1900
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

    /** Il messaggio di ricerca. Le righe finiscono con CRLF: obbligatorio. */
    fun messaggioRicerca(secondi: Int = 3): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: $MULTICAST:$PORTA\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        // MX dice al dispositivo entro quanti secondi rispondere: serve a
        // spalmare le risposte, se no in una rete piena arrivano tutte
        // insieme e qualcuna si perde.
        append("MX: $secondi\r\n")
        append("ST: $MEDIA_RENDERER\r\n")
        append("\r\n")
    }

    /**
     * L'indirizzo della scheda descrittiva da una risposta SSDP.
     *
     * Le intestazioni arrivano con maiuscole imprevedibili — «LOCATION»,
     * «Location», «location» — perche' ogni costruttore le scrive a modo
     * suo. Confrontarle senza normalizzare e' il modo piu' rapido per non
     * trovare meta' dei dispositivi in casa.
     */
    fun locationDiRisposta(risposta: String): String? =
        risposta.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.lowercase().startsWith("location:") }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.startsWith("http") }
            ?: risposta.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.lowercase().startsWith("location") }
                ?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http") }

    /**
     * Vero se l'indirizzo sta sulla rete locale.
     *
     * ## Perche' serve un controllo
     *
     * La scoperta si fida di chi risponde: si grida sulla rete e si
     * scarica la scheda dall'indirizzo che arriva indietro. Ma quel
     * messaggio lo puo' mandare chiunque sia collegato al Wi-Fi, e
     * dentro ci puo' scrivere l'indirizzo che vuole — un sito su
     * Internet, o `127.0.0.1`, che per il telefono vuol dire **se
     * stesso**. Nel secondo caso l'app diventerebbe il grimaldello per
     * bussare a servizi che ascoltano solo in locale e che nessuno da
     * fuori potrebbe raggiungere.
     *
     * Quindi si accettano solo gli indirizzi privati veri: quelli che
     * esistono dentro una rete di casa o d'ufficio e in nessun altro
     * posto. Il loopback e' escluso apposta — e' «locale» ma non e' la
     * rete: e' il telefono stesso.
     */
    fun eLocale(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        val pezzi = host.split('.')
        // Un nome invece di un indirizzo: non lo risolviamo noi, e un
        // nome che si risolve verso l'esterno e' esattamente il caso da
        // rifiutare.
        if (pezzi.size != 4) return false
        val n = pezzi.map { it.toIntOrNull() ?: return false }
        if (n.any { it !in 0..255 }) return false
        return when {
            n[0] == 10 -> true
            n[0] == 172 && n[1] in 16..31 -> true
            n[0] == 192 && n[1] == 168 -> true
            // Gli indirizzi che un apparecchio si da' da solo quando non
            // c'e' un router: capita con i collegamenti diretti.
            n[0] == 169 && n[1] == 254 -> true
            else -> false
        }
    }

    /**
     * Legge la scheda descrittiva e ne ricava il dispositivo.
     *
     * Torna null se non sa riprodurre: molti apparecchi rispondono alla
     * ricerca pur non avendo AVTransport — stampanti, router, telecamere.
     */
    fun leggiDescrizione(xml: String, urlDescrizione: String): DlnaDevice? {
        // DocumentBuilderFactory e non android.util.Xml: il primo esiste
        // anche fuori da Android, quindi questa funzione si puo' provare
        // con schede vere invece che sperare.
        val doc = runCatching {
            javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = false }
                .newDocumentBuilder()
                .parse(org.xml.sax.InputSource(StringReader(xml)))
        }.getOrElse { return null }

        fun primo(tag: String): String? =
            doc.getElementsByTagName(tag).let { l ->
                if (l.length > 0) l.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() } else null
            }

        // Il controlURL giusto e' solo quello dentro il servizio
        // AVTransport: una scheda ne contiene diversi, e prendere il primo
        // manderebbe i comandi al servizio del volume.
        var control: String? = null
        val servizi = doc.getElementsByTagName("service")
        for (i in 0 until servizi.length) {
            val nodo = servizi.item(i)
            var tipo: String? = null
            var url: String? = null
            val figli = nodo.childNodes
            for (j in 0 until figli.length) {
                val f = figli.item(j)
                when (f.nodeName.lowercase()) {
                    "servicetype" -> tipo = f.textContent?.trim()
                    "controlurl" -> url = f.textContent?.trim()
                }
            }
            if (tipo?.contains("AVTransport", true) == true && !url.isNullOrBlank()) {
                control = url
                break
            }
        }

        val c = control ?: return null
        val base = runCatching { URI(urlDescrizione) }.getOrNull() ?: return null
        // Il controlURL e' quasi sempre relativo: va risolto contro
        // l'indirizzo da cui la scheda e' arrivata.
        val assoluto = runCatching { base.resolve(c).toString() }.getOrNull() ?: return null

        return DlnaDevice(
            udn = primo("UDN") ?: assoluto,
            nome = primo("friendlyName") ?: "Dispositivo",
            costruttore = primo("manufacturer"),
            controlUrl = assoluto,
            indirizzo = "${base.host}:${base.port}",
        )
    }

    // ------------------------------------------------------------- comandi

    /** Costruisce la busta SOAP di un comando AVTransport. */
    fun busta(azione: String, corpo: String = ""): String =
        """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
 <s:Body>
  <u:$azione xmlns:u="$AV_TRANSPORT">
   <InstanceID>0</InstanceID>$corpo
  </u:$azione>
 </s:Body>
</s:Envelope>"""

    fun azioneSoap(azione: String) = "\"$AV_TRANSPORT#$azione\""

    /**
     * I metadati del brano, nel formato che i renderer si aspettano.
     *
     * Molti apparecchi rifiutano un brano senza metadati, o lo riproducono
     * mostrando «Sconosciuto». Il formato e' DIDL-Lite, e va passato come
     * testo con i caratteri speciali protetti: e' XML dentro XML.
     */
    fun didl(titolo: String, artista: String, url: String, mime: String): String {
        fun e(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
        val interno = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
            """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
            """<item id="0" parentID="-1" restricted="1">""" +
            """<dc:title>${e(titolo)}</dc:title>""" +
            """<upnp:artist>${e(artista)}</upnp:artist>""" +
            """<upnp:class>object.item.audioItem.musicTrack</upnp:class>""" +
            """<res protocolInfo="http-get:*:$mime:*">${e(url)}</res>""" +
            """</item></DIDL-Lite>"""
        return e(interno)
    }

    fun setUri(url: String, metadati: String) = busta(
        "SetAVTransportURI",
        "<CurrentURI>${url.replace("&", "&amp;")}</CurrentURI>" +
            "<CurrentURIMetaData>$metadati</CurrentURIMetaData>",
    )

    fun play() = busta("Play", "<Speed>1</Speed>")
    fun pausa() = busta("Pause")
    fun stop() = busta("Stop")
    fun seek(secondi: Long) = busta(
        "Seek",
        "<Unit>REL_TIME</Unit><Target>${orologio(secondi)}</Target>",
    )
    fun posizione() = busta("GetPositionInfo")
    fun statoTrasporto() = busta("GetTransportInfo")

    /** I tempi UPnP si scrivono h:mm:ss. */
    fun orologio(secondi: Long): String {
        val s = secondi.coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** E si rileggono allo stesso modo. Torna -1 se il campo non c'e'. */
    fun secondiDa(orologio: String?): Long {
        val pezzi = orologio?.trim()?.split(":")?.mapNotNull {
            it.substringBefore('.').toLongOrNull()
        } ?: return -1
        return when (pezzi.size) {
            3 -> pezzi[0] * 3600 + pezzi[1] * 60 + pezzi[2]
            2 -> pezzi[0] * 60 + pezzi[1]
            else -> -1
        }
    }

    /** Estrae il contenuto di un tag dalla risposta SOAP. */
    fun campo(risposta: String, nome: String): String? =
        Regex("<$nome>(.*?)</$nome>", RegexOption.DOT_MATCHES_ALL)
            .find(risposta)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}
