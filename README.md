# SoSound

Un lettore musicale per Android che sta tutto dentro il telefono. Cerchi un
brano, lo scarichi, lo ascolti offline con lo schermo spento. Nessun server,
niente da configurare.

## Come è fatto

```
  App Android
  ├── Cerca       →  API interna di YouTube Music (InnerTube), chiamata diretta
  ├── Scarica     →  yt-dlp, che gira DENTRO l'app (Python impacchettato)
  ├── Libreria    →  database Room + file nello spazio privato dell'app
  └── Riproduce   →  Media3 / ExoPlayer su file locali
```

Tre scelte che vale la pena spiegare:

**La ricerca non usa librerie.** È una POST JSON all'API interna di YouTube
Music. I risultati arrivano con artista, album e durata già separati —
molto meglio della ricerca generica di YouTube, dove ti ritrovi titoli come
`ARTISTA - Canzone (Official Video) [4K]` da ripulire a indovinare.

**Non c'è ffmpeg.** Il modulo pesa 132 MB e servirebbe solo a ricodificare.
Invece si prende il flusso audio che YouTube serve già pronto (opus o AAC)
e lo si salva com'è: ExoPlayer li riproduce entrambi. Niente ricodifica
significa anche nessuna perdita di qualità e nessuna attesa.

**I metadati stanno nel database, non nei tag.** Senza ffmpeg non potremmo
scriverli nei file, ma non servono: è l'app l'unica cosa che legge questi
file, e un database si interroga meglio di cinquecento tag.

## Al primo avvio

Quattro schermate, saltabili in qualunque momento e mai più riviste:
benvenuto, **dove tenere la musica**, come funziona l'app, e l'import
delle playlist da Spotify — l'ultimo tasto ci porta dritti.

La cartella viene per prima di proposito: è l'unico momento in cui
sceglierla non costa niente, perché la libreria è vuota e non c'è nulla
da spostare né da fondere. Chiederlo dopo, con duecento brani già
scaricati, vorrebbe dire proporre un travaso.

## Installazione

L'APK non passa dal Play Store: si installa a mano.

1. Prendi **`SoSound-arm64.apk`** (va bene per qualunque telefono degli
   ultimi dieci anni). Se dà errore di architettura, usa `SoSound-universal.apk`
2. Aprilo dal gestore file e concedi «installa app sconosciute»
3. Aprila. Non c'è niente da configurare

Al primo avvio l'app estrae il runtime Python: qualche secondo in cui la
scheda «Cerca» dice *preparo il motore di scaricamento*. Succede una volta sola.

## L'aspetto

L'app segue la direzione **Vetro**: fondo **ardesia** (`#0E1116`, grigio con
una deriva fredda appena percettibile) con tre macchie molto larghe e molto
desaturate, e liste che galleggiano sopra su pannelli smerigliati.

Le macchie restano come variazione di luminosità più che come colore: è
quello che evita l'effetto «schermo spento» senza tornare a tingere
l'interfaccia.

**Il fondo non cambia mai.** A prendere colore dal brano in ascolto sono
solo il tondo del play e la barra di avanzamento. È una scelta: un fondo
che si ritinge a ogni canzone è la cosa più vistosa dell'interfaccia e
quella che stanca prima, oltre a mettere il testo sopra una superficie di
luminosità imprevedibile. Sui controlli invece il colore dice qualcosa —
quale brano stai ascoltando.

**La tinta estratta non si usa com'è.** Una copertina può essere di
qualunque colore, compresi quelli che sul fondo scuro sparirebbero. Quindi
si prende la tinta più viva (non la più frequente: le copertine sono per lo
più grigie) e la si schiarisce finché soddisfa due contrasti insieme — la
barra rispetto al fondo, e l'icona rispetto al tondo su cui sta.

Servono entrambi perché esiste una fascia stretta di luminosità in cui la
tinta si vede benissimo sul fondo ma *nessun* inchiostro, né bianco né nero,
arriva a 4.5:1 sopra di essa. Il rosso saturo e il blu notte ci cadevano
dentro. `AccentTest` verifica che nessuna copertina ci finisca.

Si schiarisce e non si satura di proposito: schiarire un marrone dà un
marrone chiaro, riconoscibile come il colore di quella copertina; saturarlo
darebbe un arancione che nell'immagine non c'era.

## Cercare

La ricerca ha quattro filtri: **Brani**, **Album**, **Artisti**,
**Podcast**.

- **Album** — apri la scaletta, scarichi tutto l'album con un tasto, o lo
  mandi in una playlist
- **Artisti** — i brani più ascoltati, gli album e i singoli; da lì si
  entra in ogni album
- **Podcast** — cerca **programmi e puntate insieme**, con due
  interrogazioni: il filtro delle puntate non restituisce i programmi, e
  cercando «supernova» si vuole vedere il podcast, non la sua terza
  puntata. I programmi stanno sopra, le puntate sotto

**Tutto è collegato.** Dai tre puntini di un brano si va al suo artista o
al suo album; da una puntata, al programma che la pubblica. Il catalogo
porta già questi riferimenti dentro ogni risultato: raccoglierli evita di
dover ricercare l'artista per nome e sperare di ritrovare quello giusto.
Il tasto indietro risale la pila una pagina per volta.

**A campo di ricerca vuoto** ogni filtro propone la sua roba, invece di
un invito a scrivere:

| Filtro | Cosa mostra |
|---|---|
| Brani | *Di tendenza adesso*, numerati |
| Artisti | *Artisti più ascoltati*, numerati |
| Album | *Usciti da poco* — **non** numerati |
| Podcast | niente, e lo dice |

Le prime tre arrivano da `FEmusic_explore` e `FEmusic_charts`, che
rispondono senza account.

Gli album non sono numerati perché **non è una classifica**: YouTube
Music pubblica le uscite recenti, non gli album più ascoltati.
Numerarli farebbe credere a un ordine che non esiste.

Per i podcast non c'è proprio niente da proporre — le pagine che
conterrebbero una classifica rispondono 404. L'app lo dice invece di
riempire lo spazio con i risultati di una ricerca travestiti da
classifica.

Nella libreria c'è una vista **Album** che raggruppa i brani scaricati.
Non esiste una tabella «album»: il nome sta già su ogni brano e
raggrupparlo basta. Una tabella in più andrebbe tenuta allineata a mano,
e si disallineerebbe al primo brano cancellato.

## Come si usa

**Toccare un brano lo fa partire, ovunque si trovi.** Se ce l'hai già parte
subito; se non ce l'hai viene scaricato e parte da solo appena pronto. Non
serve sapere se un brano è sul telefono o no.

- **Cerca** — tocca un risultato e parte. Il `+` a destra scarica soltanto,
  senza interrompere quello che stai ascoltando
- **Libreria** — due viste: *Brani*, tutto quello che hai, e *Playlist*
- **Tre puntini** su un brano — la scheda con titolo e artista per intero
  (nelle liste vengono troncati), album, formato, peso, e le azioni:
  riproduci, aggiungi a una playlist, togli dal telefono
- **Impostazioni** — spazio occupato e il tasto che aggiorna yt-dlp

Il brano in ascolto è marcato nelle liste con l'icona dell'equalizzatore
nella tinta del momento, così si riconosce senza guardare la barra in fondo.

La musica continua a schermo spento, i controlli stanno sulla schermata di
blocco, e i tasti degli auricolari Bluetooth funzionano.

## Il fuoco audio

La musica si ferma quando arriva una telefonata, e riparte quando
finisce. Sembra ovvio, ma richiede di non usare la gestione automatica di
Media3.

ExoPlayer chiede il fuoco audio con `setWillPauseWhenDucked(false)` per
qualunque contenuto che non sia parlato — e la musica non lo è. Con quel
flag Android **abbassa il volume per conto suo e non avvisa l'app**: la
musica continua, più piano, sotto la telefonata.

`AudioFocusHandler` chiede il fuoco a mano con
`setWillPauseWhenDucked(true)`, così il sistema ci avvisa sempre e la
decisione resta nostra. Si mette in pausa su qualunque perdita, anche
quella «abbassabile» che di solito è una notifica: per un bip è una
reazione brusca, ma è il prezzo per non parlare sopra una chiamata.

## Quando smette di funzionare

Succederà: YouTube cambia qualcosa e i download cominciano a fallire.
**Non c'è niente da premere.** yt-dlp si aggiorna da sé all'avvio, una
volta al giorno, e di nuovo quando un download fallisce.

Un caso a parte è l'errore **403 Forbidden** su singoli brani: lì il
brano è stato trovato, è l'indirizzo audio a essere rifiutato. YouTube
espone liste di formati diverse a seconda del client che le chiede, e su
certi brani quella predefinita dà indirizzi che poi non funzionano. Prima
di arrendersi, SoSound rifà il giro chiedendo la lista ad altri client —
`tv`, `ios`, `web_safari`. Si paga solo sui brani che falliscono: quelli
che vanno al primo colpo non costano nulla in più.

Se anche così non passa, la voce fallita ha un tasto **riprova**.

Se compare *«YouTube chiede una conferma anti-bot»*, la connessione gli è
sembrata sospetta. Di solito passa da sola; cambiare rete (dati mobili
invece del Wi-Fi, o viceversa) spesso basta.

Se dopo un aggiornamento continua a non funzionare, il problema è nella
libreria che impacchetta yt-dlp e serve un APK nuovo. È il prezzo di non
avere un server: prima bastava riavviare un container.

## Dove finiscono i file

Di serie nello spazio privato dell'app: nessun permesso da concedere, ma
i file li vede solo SoSound e spariscono disinstallandola.

## Legale

SoSound non è affiliato con YouTube, Google o Spotify, non ospita alcun
contenuto e non ha un server: ogni scaricamento è una connessione diretta
fra il telefono e YouTube. È distribuito sotto **GPL-3.0**, senza alcuna
garanzia, ed è responsabilità di chi lo usa rispettare le leggi sul
copyright della propria giurisdizione. Dettagli in
[`DISCLAIMER.md`](DISCLAIMER.md), licenza in [`LICENSE`](LICENSE),
dipendenze in [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md).

Da **Impostazioni → Dove finiscono i file** si può scegliere una cartella
qualsiasi del telefono o della scheda SD. Lì i file restano anche dopo la
disinstallazione e si vedono dagli altri lettori. I brani già scaricati
non si spostano da soli — sarebbe un travaso di centinaia di megabyte
deciso dall'app — ma compare un tasto per farlo.

Dentro la cartella scelta SoSound crea una sua struttura, sempre con lo
stesso nome:

```
SoSound/
  Brani/
    Daft Punk/
      Daft Punk - Get Lucky [4D7u5KF7SP8].m4a
  Copertine/
    4D7u5KF7SP8.jpg
  sosound.json
```

Così si può scegliere anche una cartella già piena di altra roba senza
mescolarcisi — e soprattutto la cartella si **riconosce** quando la si
reimporta.

### Ripristinare da una cartella

`sosound.json` è l'indice: metadati, album, e le playlist con il loro
ordine. Si riscrive da sé quando la libreria cambia. Reinstallando
l'app, **Impostazioni → Importa cartella** rimette tutto com'era — i file
restano dove sono, la libreria ci punta.

**Cosa il backup garantisce, e cosa no.** Non si può dimostrare che una
cartella venga da questa app: una chiave per firmarla starebbe dentro
l'APK, e estrarla è banale. Una firma del genere sembrerebbe una
garanzia senza esserlo.

Quello che si garantisce davvero:

- **riconoscimento** — marchio di formato e versione; una versione più
  nuova di quella che l'app conosce viene rifiutata invece di letta a metà
- **integrità dell'indice** — un'impronta SHA-256 del contenuto: se
  qualcuno lo modifica o la copia si interrompe, l'app se ne accorge
- **integrità dei file** — dimensione e impronta parziale (primi e ultimi
  64 KB) per ogni brano. Non il file intero: su cinquecento brani
  vorrebbe dire rileggere due gigabyte a ogni verifica, e i guasti veri —
  file troncato, file sostituito — questa li riconosce lo stesso
- **completezza** — quello che manca o non corrisponde viene mostrato
  prima di importare, e saltato

Per il rischio reale — la cartella sbagliata, o una sincronizzazione a
metà — è il livello giusto.

### Tenere una copia su Drive

**Google Drive non si può scegliere come destinazione.** Su Android
espone i suoi file in sola lettura e non offre una cartella in cui altre
app possano scrivere: non è un limite di SoSound.

La strada che funziona è in due passi: scegli una cartella normale del
telefono, poi usa un'app di sincronizzazione — *Autosync per Google
Drive*, *FolderSync*, *Syncthing* — che tiene quella cartella allineata
al cloud. Da quel momento ogni brano scaricato finisce anche nel backup,
senza altri gesti.

## Suonare su un altro apparecchio

Due strade, dal tasto in alto nella schermata di ascolto.

**Google Cast** — Chromecast, Google TV, Android TV, altoparlanti Nest,
TV con Chromecast integrato. Usa `CastPlayer` di Media3, che implementa
la stessa interfaccia `Player` di ExoPlayer: si passa dal telefono
all'altoparlante scambiando un oggetto, non riscrivendo la riproduzione.

**DLNA** — TV Samsung e LG, ricevitori AV, impianti: tutta la fascia che
non parla Cast. Il protocollo è scritto a mano, perché servono la
scoperta e sei comandi SOAP: una libreria UPnP completa implementa anche
l'*ospitare* dispositivi, la registrazione e gli eventi, quasi un
megabyte di codice mai eseguito.

### Il vincolo che vale per entrambi

**Nessuno dei due legge i file del telefono.** Ricevono un indirizzo e lo
scaricano da soli, e i nostri brani stanno nello spazio privato o dietro
un `content://` — da fuori non esistono.

Quindi finché si trasmette il telefono fa da sorgente: un server di
50 KB pubblica i brani sulla rete locale e si spegne appena si smette.
Rispetta gli intervalli HTTP, altrimenti spostarsi dentro un brano dal
telecomando della TV non funzionerebbe. È anche il motivo per cui
aggiungere DLNA dopo Cast è costato poco: il pezzo difficile era già lì.

### Chi puo' chiedere i brani

Il server a bordo non e' esposto a Internet, ma «rete locale» non vuol
dire «solo io»: su un Wi-Fi d'albergo o d'ufficio c'e' dentro chiunque
altro, e gli identificativi dei brani sono quelli pubblici di YouTube,
quindi indovinabili.

Ogni indirizzo porta percio' una parola d'ordine di 128 bit, sorteggiata
a ogni accensione e confrontata a tempo costante. La conosce chi l'ha
ricevuta da noi — il ricevitore Cast o la TV — e nessun altro.

Nella direzione opposta vale lo stesso sospetto: la scoperta si fida di
chi risponde, e la risposta arriva da chiunque sia collegato alla rete.
Dentro ci puo' essere l'indirizzo che vuole, compreso un sito esterno o
`127.0.0.1`, che per il telefono vuol dire se stesso. Si accettano solo
indirizzi privati veri, controllati prima di ogni richiesta.

### Cosa resta fuori

Apple TV e HomePod usano AirPlay, proprietario e senza una libreria
utilizzabile su Android. Sonos parla un protocollo suo. Il **Bluetooth**
funziona già senza che l'app faccia niente: lo instrada Android.

## Come si costruisce

```bash
cd android && ./gradlew assembleRelease
```

L'APK esce firmato se `android/keystore.properties` esiste — indica il
file della chiave e la sua password, e sta fuori da git. Senza, la build
funziona lo stesso e produce un APK non firmato: su un'altra macchina
non fallisce in modo oscuro, semplicemente non firma.

**La chiave non si rigenera.** Android riconosce un aggiornamento come
«la stessa app» solo se ha la stessa firma: perderla vuol dire che ogni
telefono deve disinstallare e reinstallare.

Accorciare il codice (R8) resta spento di proposito. Quasi tutto il peso
dell'APK e' Python e yt-dlp dentro gli asset, che R8 non tocca: si
guadagnerebbero briciole e si rischierebbe il modo peggiore di rompere
qualcosa — compila, si installa, e sbaglia al terzo brano perche' una
classe cercata per riflessione non c'e' piu'.

## Ascoltare senza scaricare

Si tocca un risultato e parte. yt-dlp fa due cose distinte — capire
**dove** sta l'audio, e portarselo via: la prima costa un paio di
secondi, la seconda quanto pesa il file. Fermandosi alla prima si
ottiene un indirizzo da dare al lettore, e l'audio arriva mentre suona.

Quello che arriva resta: la seconda volta il brano parte da fermo e
senza rete. Quando lo spazio finisce se ne va quello che non si sente da
più tempo — non è un archivio, è una comodità che si sfoltisce da sola.
In impostazioni c'è quanto occupa e il tasto per svuotarla.

### Tre stati, non due

Un brano può essere **salvato** (file suo nella cartella, suona sempre),
**in cache** (scaricato ascoltandolo, sparisce quando serve spazio) o un
**riferimento** (solo il nome). Nelle liste un riferimento porta una
nuvoletta: «ce l'ho» e «lo conosco» non devono avere lo stesso aspetto,
o in aereo ci si accorge della differenza nel momento peggiore.

«Tieni anche senza rete», dalla scheda del brano, lo fa diventare
salvato.

### Perché l'indirizzo non si salva

Scade. YouTube lo firma per qualche ora e poi smette di rispondere, e
per questo un brano in streaming non è un brano che si possiede: è un
riferimento da risolvere ogni volta. L'ordine conta — la cache sta
davanti alla risoluzione, se no si aspetterebbero due secondi anche per
un brano che si ha già in casa.

### Cosa finisce nell'indice, e cosa no

I riferimenti **sì**: fanno parte della libreria e delle playlist, e
tacerli farebbe arrivare una playlist monca su un telefono nuovo senza
che niente spieghi dove sono finiti tre brani su dieci.

Cosa c'è in cache **no**: è un fatto di questo telefono in questo
momento, e in una cartella che descrive un backup sarebbe una promessa
che scade da sola.

## Quanto occupa

Un brano di 4 minuti in opus sta sui **4 MB**. Cento brani ≈ 400 MB,
cinquecento ≈ 2 GB. I file stanno nello spazio privato dell'app: nessun
altro programma li vede, e **disinstallando l'app spariscono**.

## Le cose da sapere

**Sul piano legale.** Scaricare musica protetta da YouTube viola i termini
di servizio della piattaforma e, salvo contenuti liberi, il diritto
d'autore. Questo è uno strumento personale, a utente singolo, sul proprio
dispositivo. Non c'è niente qui dentro per aggirare i controlli anti-bot, e
non va aggiunto: è l'unica differenza fra un attrezzo e un problema.

## Sviluppo

```bash
cd android
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # collauda il parser contro l'API vera
```

Serve JDK 17 o 21 e l'SDK Android (platform 35); il percorso va in
`android/local.properties`.

⚠️ I test della ricerca interrogano **l'API vera** di proposito. Quello che
si rompe non è la logica, è la forma della risposta di InnerTube — un test
con una risposta registrata continuerebbe a passare mentre l'app sul
telefono non trova più niente. Se falliscono senza che il codice sia
cambiato, vuol dire che YouTube ha cambiato struttura e il parser va
aggiornato.

## Storia

Questo progetto è nato come architettura client/server: Navidrome su un NAS
più un servizio Python che scaricava. Funzionava ed era più robusto —
quando yt-dlp si rompeva bastava riavviare il container — ma richiedeva un
NAS acceso. La versione attuale mette tutto nel telefono.

Il codice della versione con server è nella cronologia git, fino al commit
`bc2780c`.

## Importare una playlist

**Libreria → Playlist → Importa.** Tre strade, nessun account da
collegare:

- **Il link di condivisione** di una playlist o di un album Spotify,
  incollato. Legge la *pagina di anteprima*, che è pubblica e porta con
  sé l'elenco dei brani. ⚠️ È una pagina web, non un contratto: se
  Spotify ne cambia la struttura questa strada si chiude, e
  `SpotifyLinkTest` fallisce per dirlo prima che se ne accorga chi usa
  l'app
- **Un CSV esportato** con Exportify o TuneMyMusic — non dipende da come
  è fatta una pagina, quindi è la strada che invecchia meglio
- **Un elenco incollato**, una riga per brano «Artista - Titolo»

Ogni riga viene cercata sul catalogo e l'abbinamento riceve un punteggio.
Solo i risultati **sicuri** partono già selezionati; gli incerti vanno
guardati, con le alternative a un tocco. Altrimenti marcarli come incerti
non servirebbe a niente.

Due cose che il punteggio deve sapere e che non sono ovvie:

- «Remastered 2011», «feat.», «Official Video» sono **rumore**: la
  canzone è la stessa. Un anno viene ignorato solo se accanto c'è una di
  queste parole — in «1999» di Prince l'anno *è* il titolo
- «Remix», «Cover», «Karaoke», «Nightcore» sono un'**altra incisione**, e
  prendono una penalità pesante. Senza, cercando «Get Lucky» vinceva
  *Get Lucky (Daft Punk Remix)* col punteggio pieno: tutte le parole
  cercate c'erano

`MatchQualityTest` misura l'affidabilità contro il catalogo vero, su un
campione scritto come lo scriverebbe un export. Se scende sotto l'80% il
test fallisce: vuol dire che il punteggio va ritarato o che il catalogo è
cambiato sotto.

## Riproduzione casuale e ripetizione

«Ascolta tutto a caso» in cima ai brani e dentro ogni playlist. Nella
schermata di ascolto ci sono i due interruttori: casuale, e ripetizione
che gira fra niente → ripeti la coda → ripeti il brano.

Il casuale usa quello di ExoPlayer invece di mescolare la lista: così
spegnendolo si torna all'ordine vero, che mescolando davvero sarebbe
perso. La coda che si vede resta nell'ordine originale — è l'ordine, non
la sequenza di ascolto.

## Da fare

### Prossime

- **Importare da YouTube Music.** Un link con `list=` restituisce i
  brani con il loro videoId: nessuna ricerca per titolo, nessun brano
  «non trovato». Riusa il lettore che il catalogo ha gia'. Provato: 71
  brani da una playlist vera, stesso renderer degli album
- **Importare da Deezer.** API pubblica senza chiave, e da un link di
  profilo arrivano tutte le playlist pubbliche insieme. Provato: 89
  playlist da un profilo
- **Spotify oltre i cento brani.** La pagina di anteprima si ferma a
  cento e non dichiara il totale. Nella stessa pagina c'e' un token
  anonimo con cui l'API vera pagina oltre, ma e' un appiglio non
  documentato e va provato su un telefono: da un indirizzo di
  datacenter risponde 429
- Riprendere un download interrotto invece di ricominciarlo

### Piu' avanti

- **Cercare a voce, con un modello piccolo sul telefono.**

  L'idea e' dire «metti quella dei Daft Punk che fa tu-tu-tun» e
  trovarla. Il pezzo facile e' il riconoscimento del parlato: Android
  lo fa gia' da solo, gratis e offline sui telefoni recenti
  (`SpeechRecognizer`), e non serve nessun modello nostro.

  Il pezzo che vorrebbe un modello e' il passo dopo: da una frase
  storta a una ricerca sensata. «quella canzone dell'estate scorsa con
  il fischio» non e' una query, e un modello piccolo — Gemma 3 270M o
  1B, via MediaPipe LLM Inference o litert-lm — potrebbe trasformarla
  in qualcosa che il catalogo capisce.

  **Il costo e' il punto da valutare prima di scrivere una riga:** un
  modello da 270M quantizzato sono ~300 MB di APK contro i 37 di
  adesso, e su un telefono di fascia media qualche secondo per
  risposta. Un'app di musica che pesa dieci volte tanto per capire le
  frasi storte e' uno scambio da fare solo dopo aver provato che il
  giro semplice — parlato di Android, testo dritto nella ricerca — non
  basta. Quindi: prima quello, che costa due giorni, e il modello solo
  se si vede che serve davvero.

- **Riconoscere una canzone che sta suonando.**

  Il meccanismo di Shazam non e' segreto: si prende lo spettrogramma,
  si tengono i picchi piu' forti, si costruiscono coppie di picchi
  vicini e ogni coppia diventa un numero. Quei numeri si cercano in un
  archivio, e la canzone giusta e' quella che ne ha tanti **con lo
  stesso scarto di tempo**. Sul telefono costa poco: qualche secondo di
  audio e una FFT.
  
  **Il problema non e' calcolare l'impronta, e' avere l'archivio con
  cui confrontarla.** Shazam ne ha uno da decine di milioni di brani
  costruito in vent'anni; noi avremmo solo quello che c'e' in libreria.

  Il che pero' non e' inutile, ed e' la versione che vale la pena
  fare: **«che cosa e' questa, fra le mie?»**. Serve a ritrovare in
  mezzo a cinquecento brani quello che sta suonando da un'altra parte,
  e a riconoscere un doppione con due titoli diversi. Per il resto del
  mondo l'unica strada onesta e' un servizio esterno (AudD, ACRCloud),
  che vuole una chiave e un abbonamento — cioe' esattamente quello che
  questa app esiste per non avere.
