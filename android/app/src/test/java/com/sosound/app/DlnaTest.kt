package com.sosound.app

import com.sosound.app.cast.LocalMediaServer
import com.sosound.app.cast.dlna.Dlna
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il protocollo UPnP, scritto a mano e quindi da verificare a mano.
 *
 * Le schede descrittive qui sotto sono ricalcate su quelle vere: una TV
 * Samsung espone piu' servizi e mette il controlURL relativo, una LG usa
 * maiuscole diverse nelle intestazioni. Sono esattamente i punti dove un
 * lettore ingenuo sbaglia — e dove sbagliare vuol dire «il dispositivo
 * non compare» senza nessun messaggio d'errore.
 */
class DlnaTest {

    // Una scheda realistica: piu' servizi, AVTransport NON per primo.
    private val schedaSamsung = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
 <device>
  <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
  <friendlyName>[TV] Samsung 7 Series</friendlyName>
  <manufacturer>Samsung Electronics</manufacturer>
  <UDN>uuid:0d1b4a20-ee1c-11e7-8c3f-9a214cf093ae</UDN>
  <serviceList>
   <service>
    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
    <controlURL>/upnp/control/RenderingControl1</controlURL>
   </service>
   <service>
    <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
    <controlURL>/upnp/control/ConnectionManager1</controlURL>
   </service>
   <service>
    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
    <controlURL>/upnp/control/AVTransport1</controlURL>
   </service>
  </serviceList>
 </device>
</root>"""

    @Test
    fun `dalla scheda si ricava il servizio giusto, non il primo`() {
        val d = Dlna.leggiDescrizione(schedaSamsung, "http://192.168.1.40:7676/smp_15_")
        assertNotNull("scheda non letta", d)
        d!!
        println("--- ${d.nome} (${d.costruttore}) -> ${d.controlUrl} ---")

        assertEquals("[TV] Samsung 7 Series", d.nome)
        assertEquals("Samsung Electronics", d.costruttore)
        // Il controlURL di AVTransport, non quello del volume che viene
        // prima nella lista: mandare Play al RenderingControl non fa
        // niente e non produce nemmeno un errore.
        assertTrue("servizio sbagliato: ${d.controlUrl}", d.controlUrl.endsWith("/AVTransport1"))
        // Ed e' assoluto: nella scheda era relativo.
        assertTrue("indirizzo non risolto", d.controlUrl.startsWith("http://192.168.1.40:7676/"))
    }

    @Test
    fun `un apparecchio che non sa riprodurre viene scartato`() {
        // Stampanti, router e telecamere rispondono alla ricerca ma non
        // hanno AVTransport: mostrarli fra i dispositivi sarebbe una
        // promessa che al primo tocco non si mantiene.
        val stampante = """<?xml version="1.0"?>
<root><device>
 <friendlyName>HP LaserJet</friendlyName>
 <serviceList>
  <service>
   <serviceType>urn:schemas-upnp-org:service:Printer:1</serviceType>
   <controlURL>/ctl/print</controlURL>
  </service>
 </serviceList>
</device></root>"""
        assertNull(Dlna.leggiDescrizione(stampante, "http://192.168.1.99:8080/desc.xml"))
    }

    @Test
    fun `una scheda malformata non fa esplodere niente`() {
        assertNull(Dlna.leggiDescrizione("non sono xml", "http://192.168.1.1/x"))
        assertNull(Dlna.leggiDescrizione("", "http://192.168.1.1/x"))
    }

    @Test
    fun `l'intestazione LOCATION si legge con qualunque maiuscola`() {
        // Ogni costruttore le scrive a modo suo. Confrontarle senza
        // normalizzare fa sparire meta' dei dispositivi di casa.
        val varianti = listOf(
            "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.40:7676/desc.xml\r\n\r\n",
            "HTTP/1.1 200 OK\r\nLocation: http://192.168.1.40:7676/desc.xml\r\n\r\n",
            "HTTP/1.1 200 OK\r\nlocation:http://192.168.1.40:7676/desc.xml\r\n\r\n",
        )
        for (v in varianti) {
            assertEquals(
                "http://192.168.1.40:7676/desc.xml",
                Dlna.locationDiRisposta(v),
            )
        }
        assertNull(Dlna.locationDiRisposta("HTTP/1.1 200 OK\r\nSERVER: boh\r\n\r\n"))
    }

    @Test
    fun `il messaggio di ricerca ha le righe come le vuole il protocollo`() {
        val m = Dlna.messaggioRicerca(3)
        println("--- M-SEARCH ---\n$m")
        // CRLF e non solo LF: con le sole interruzioni di riga molti
        // apparecchi ignorano il messaggio senza dire niente.
        assertTrue("manca il CRLF", m.contains("\r\n"))
        assertTrue(m.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(m.contains("""MAN: "ssdp:discover""""))
        assertTrue(m.contains("ST: ${Dlna.MEDIA_RENDERER}"))
        // La busta finisce con una riga vuota: senza, resta in attesa.
        assertTrue("manca la riga vuota finale", m.endsWith("\r\n\r\n"))
    }

    @Test
    fun `i metadati sono XML dentro XML, e vanno protetti due volte`() {
        val didl = Dlna.didl(
            titolo = "Rock & Roll <Live>",
            artista = "Tizio \"il Grande\"",
            url = "http://192.168.1.5:8474/t/abc?x=1&y=2",
            mime = "audio/mp4",
        )
        println("--- DIDL ---\n${didl.take(200)}")
        // Il DIDL viaggia come TESTO dentro la busta SOAP: se i suoi
        // segni di minore non sono protetti, il renderer legge una busta
        // rotta e rifiuta il brano.
        assertTrue("il DIDL non e' protetto", didl.contains("&lt;DIDL-Lite"))
        assertTrue("la e commerciale non e' protetta", didl.contains("&amp;"))
        assertTrue("manca il titolo", didl.contains("Rock"))
        assertTrue("manca il tipo", didl.contains("audio/mp4"))
    }

    @Test
    fun `i tempi si scrivono e si rileggono nel formato UPnP`() {
        assertEquals("0:00:00", Dlna.orologio(0))
        assertEquals("0:03:42", Dlna.orologio(222))
        assertEquals("1:14:05", Dlna.orologio(4445))

        assertEquals(222, Dlna.secondiDa("0:03:42"))
        assertEquals(4445, Dlna.secondiDa("1:14:05"))
        // Alcuni apparecchi aggiungono i millesimi.
        assertEquals(222, Dlna.secondiDa("00:03:42.000"))
        // E altri rispondono cosi' quando non sanno la durata.
        assertEquals(-1, Dlna.secondiDa("NOT_IMPLEMENTED"))
        assertEquals(-1, Dlna.secondiDa(null))
    }

    @Test
    fun `dalla risposta SOAP si estraggono i campi`() {
        val risposta = """<?xml version="1.0"?>
<s:Envelope><s:Body><u:GetPositionInfoResponse>
<Track>1</Track><TrackDuration>0:03:42</TrackDuration>
<RelTime>0:01:07</RelTime><AbsTime>0:01:07</AbsTime>
</u:GetPositionInfoResponse></s:Body></s:Envelope>"""
        assertEquals("0:03:42", Dlna.campo(risposta, "TrackDuration"))
        assertEquals("0:01:07", Dlna.campo(risposta, "RelTime"))
        assertNull(Dlna.campo(risposta, "NonEsiste"))
        assertEquals(67, Dlna.secondiDa(Dlna.campo(risposta, "RelTime")))
    }

    @Test
    fun `la busta di un comando e' ben formata`() {
        val b = Dlna.setUri("http://192.168.1.5:8474/t/abc", "METADATI")
        println("--- SetAVTransportURI ---\n${b.take(260)}")
        assertTrue(b.contains("<u:SetAVTransportURI"))
        assertTrue(b.contains("<InstanceID>0</InstanceID>"))
        assertTrue(b.contains("<CurrentURI>http://192.168.1.5:8474/t/abc</CurrentURI>"))
        // L'intestazione SOAPAction deve essere fra virgolette: senza,
        // molti apparecchi rispondono 401.
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            Dlna.azioneSoap("Play"),
        )
    }

    @Test
    fun `si parla solo con la rete locale`() {
        // Chi risponde alla ricerca scrive lui l'indirizzo da cui
        // scaricare la scheda: e' l'unico punto in cui un estraneo sulla
        // stessa rete decide dove va a bussare l'app.
        for (buono in listOf(
            "http://192.168.1.40:7676/desc.xml",
            "http://10.0.0.5:8200/rootDesc.xml",
            "http://172.16.3.9:49152/x",
            "http://172.31.255.254/x",
            "http://169.254.4.4:1400/x",
        )) assertTrue("rifiutato un indirizzo di casa: $buono", Dlna.eLocale(buono))

        for (cattivo in listOf(
            // Fuori: la scoperta non deve poter portare traffico su Internet.
            "http://93.184.216.34/desc.xml",
            "http://esempio.it/desc.xml",
            // 172.15 e 172.32 stanno FUORI dall'intervallo privato, che
            // va da 16 a 31: e' l'errore classico di chi controlla solo
            // il primo numero.
            "http://172.15.0.1/x",
            "http://172.32.0.1/x",
            // Il telefono stesso: da qui si arriverebbe a servizi che
            // ascoltano solo in locale e che nessuno raggiunge da fuori.
            "http://127.0.0.1:8474/t/x",
            "http://localhost:8474/x",
            // Scritture che sembrano un indirizzo e non lo sono.
            "http://192.168.1.999/x",
            "http://192.168.1/x",
            "non un indirizzo",
            "",
        )) assertTrue("accettato un indirizzo da rifiutare: $cattivo", !Dlna.eLocale(cattivo))
    }

    @Test
    fun `la parola d'ordine del server e' sorteggiata e si confronta a tempo costante`() {
        val a = LocalMediaServer.nuovaParola()
        val b = LocalMediaServer.nuovaParola()
        println("--- parola: $a ---")
        // 16 byte in esadecimale: indovinarla a tentativi non e' una strada.
        assertEquals(32, a.length)
        assertTrue("due accensioni, stessa parola", a != b)

        assertTrue(LocalMediaServer.ugualiATempoCostante(a, a))
        assertTrue(!LocalMediaServer.ugualiATempoCostante(a, b))
        // Lunghezze diverse, e una quasi giusta: il confronto deve
        // guardare tutto e non fermarsi al primo carattere buono.
        assertTrue(!LocalMediaServer.ugualiATempoCostante(a, a.dropLast(1)))
        assertTrue(!LocalMediaServer.ugualiATempoCostante(a, a.dropLast(1) + "z"))
        assertTrue(!LocalMediaServer.ugualiATempoCostante("", a))
    }

    @Test
    fun `un identificativo di brano fuori formato viene rifiutato`() {
        assertTrue(LocalMediaServer.videoIdValido("dQw4w9WgXcQ"))
        assertTrue(LocalMediaServer.videoIdValido("_-Ab12cd34E"))
        // Lo stesso valore finisce dentro un indirizzo passato a yt-dlp.
        for (brutto in listOf(
            "../../etc/passwd", "dQw4w9WgXcQ/../x", "corto",
            "dQw4w9WgXcQextra", "dQw4 w9WgXc", "", "dQw4w9WgXc?",
        )) assertTrue("accettato: $brutto", !LocalMediaServer.videoIdValido(brutto))
    }
}
