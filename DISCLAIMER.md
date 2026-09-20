# Disclaimer

## Cos'è, e cosa non è

SoSound è un lettore musicale che cerca brani tramite l'API interna di
YouTube Music (InnerTube) e li scarica localmente per l'ascolto offline,
usando [yt-dlp](https://github.com/yt-dlp/yt-dlp) impacchettato tramite
[youtubedl-android](https://github.com/junkfood02/youtubedl-android).

**Non è un servizio.** Non esiste alcun server dell'autore in mezzo: ogni
ricerca e ogni scaricamento è una connessione diretta fra il dispositivo di
chi usa l'app e i server di YouTube, con le stesse richieste che farebbe un
browser. L'autore non ospita, non trasmette, non conserva e non ha accesso
a nessun file audio scaricato da chi usa l'app.

**Non è affiliato con YouTube.** SoSound non è un prodotto di Google,
YouTube, Alphabet Inc. o Spotify AB, non è da loro sponsorizzato,
approvato o in alcun modo associato. "YouTube", "YouTube Music" e
"Spotify" sono marchi dei rispettivi titolari, citati qui solo per
descrivere con cosa l'app interagisce.

L'API InnerTube usata per la ricerca non è un'interfaccia pubblica
documentata: è quella che il sito e l'app ufficiali usano internamente, ed
è stata osservata e replicata (lo stesso vale, più in generale, per il modo
in cui yt-dlp risolve gli indirizzi audio). Può cambiare in qualsiasi
momento senza preavviso, e a quel punto l'app può smettere di funzionare
finché non viene aggiornata — non c'è alcuna garanzia di continuità.

## Nessuna garanzia

Il software è distribuito secondo i termini della licenza **GNU GPL v3.0**
(vedi [`LICENSE`](LICENSE)), che include esplicitamente, alle sezioni 15 e
16, l'assenza di qualunque garanzia: SENZA ALCUNA GARANZIA, nemmeno quella
implicita di commerciabilità o idoneità per uno scopo particolare. Nella
misura massima permessa dalla legge applicabile, l'autore non è
responsabile per danni derivanti dall'uso o dall'impossibilità di usare
il software.

## Responsabilità di chi usa l'app

SoSound è uno strumento general-purpose: permette di cercare, scaricare e
ascoltare offline brani accessibili tramite l'API di YouTube Music, cosa
che comprende sia contenuti liberamente distribuibili sia contenuti
protetti da copyright. Come per qualunque strumento di questo tipo
(un browser, un masterizzatore, una fotocamera), è **chi lo usa** —
non chi lo scrive — a dover verificare di avere il diritto di scaricare e
conservare quel contenuto secondo le leggi sul diritto d'autore della
propria giurisdizione e i Termini di Servizio di YouTube. L'autore non
controlla, non modera e non può controllare l'uso che ne viene fatto da
terzi.

## Il precedente di riferimento

Nel 2020 GitHub rimosse temporaneamente il repository di **youtube-dl**
in seguito a una segnalazione DMCA della RIAA — non per violazione diretta
di copyright, ma per una presunta elusione di una misura tecnica di
protezione (DMCA §1201, il "rolling cipher" di YouTube). L'
[Electronic Frontier Foundation](https://www.eff.org/deeplinks/2020/11/riaa-abuses-dmca-take-down-youtube-dl)
intervenne e il repository fu ripristinato, sulla base del fatto che uno
strumento capace di usi legittimi sostanziali — youtube-dl permette di
scaricare anche contenuti liberamente distribuibili, di pubblico dominio o
con licenza dell'autore — non elude di per sé una protezione efficace e non
è per questo illegale. SoSound si trova nella stessa area: uno strumento
general-purpose, non un servizio che distribuisce contenuti protetti.

## Non c'è supporto commerciale

Questo è un progetto personale, pubblicato così com'è (*as-is*), senza
alcun impegno di manutenzione, assistenza o compatibilità futura.
