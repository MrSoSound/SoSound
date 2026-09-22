package com.sosound.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import androidx.media3.common.Player

/**
 * Chiede e sorveglia il fuoco audio al posto di ExoPlayer.
 *
 * ## Perche' non lasciarlo fare a Media3
 *
 * ExoPlayer sa gestire il fuoco da solo, ma costruisce la richiesta con
 * `setWillPauseWhenDucked(false)` per qualunque contenuto che non sia
 * parlato — e la musica non lo e'. Con quel flag Android, quando arriva
 * qualcosa di piu' importante, **abbassa il volume per conto suo e non
 * chiama nemmeno l'ascoltatore**: l'app non viene informata, e continua
 * a suonare piu' piano sotto la telefonata.
 *
 * Chiedendo il fuoco a mano con `setWillPauseWhenDucked(true)` il
 * sistema smette di abbassare il volume da solo e ci avvisa sempre.
 * A quel punto decidiamo noi, e per un lettore musicale la decisione e'
 * mettere in pausa.
 *
 * ## Le due perdite temporanee non sono la stessa cosa
 *
 * Android ne distingue due, e vanno trattate in modo diverso:
 *
 * - **una telefonata** chiede il silenzio. Li' si mette in pausa, e si
 *   riprende da soli quando finisce;
 * - **una notifica** chiede solo di farsi sentire. Fermare la musica per
 *   un bip di due decimi di secondo e' una reazione sproporzionata:
 *   quello che serve e' abbassare il volume, lasciar passare il suono e
 *   rialzarlo.
 *
 * Questa seconda cosa la facciamo noi, ed e' il motivo per cui la
 * richiesta resta con `setWillPauseWhenDucked(true)`: con quel flag
 * Android non abbassa piu' il volume di nascosto e ci avvisa sempre, e a
 * quel punto decidiamo noi caso per caso. Abbassando da soli sappiamo
 * anche **quando** rialzare, cosa che con l'abbassamento automatico non
 * si sa.
 */
class AudioFocusHandler(
    private val context: Context,
    private val player: Player,
) : AudioManager.OnAudioFocusChangeListener {

    /**
     * Se chi ascolta vuole sentire le notifiche.
     *
     * Si rilegge a ogni notifica invece di tenerla in memoria: cosi'
     * cambiare l'interruttore vale subito, anche a musica gia' avviata.
     */
    private fun vuoleSentirle(): Boolean =
        context.getSharedPreferences("archivio", Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICHE, true)

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // Il punto di tutta la classe.
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(this)
            .build()

    /** Se in questo momento teniamo il fuoco. */
    private var holding = false

    /**
     * Se siamo stati noi a mettere in pausa per una perdita di fuoco.
     * Serve a non far ripartire la musica che l'utente aveva messo in
     * pausa di suo, quando il fuoco torna.
     */
    private var pausedByLoss = false

    /** Torna false se il sistema nega il fuoco: allora non si parte. */
    fun acquire(): Boolean {
        if (holding) return true
        holding = audioManager.requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holding
    }

    fun release() {
        if (!holding) return
        audioManager.abandonAudioFocusRequest(request)
        holding = false
        pausedByLoss = false
    }

    override fun onAudioFocusChange(change: Int) {
        when (change) {
            // Definitiva: un'altra app ha preso il comando e non lo
            // restituira'. Ci fermiamo e lasciamo il fuoco.
            AudioManager.AUDIOFOCUS_LOSS -> {
                rialza()
                pausedByLoss = false
                holding = false
                if (player.playWhenReady) player.pause()
            }

            // Temporanea e basta: chiamata, navigatore, assistente
            // vocale. Qui serve silenzio, non volume basso.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // playWhenReady e non isPlaying: un brano che sta ancora
                // caricando non «sta suonando», ma partirebbe fra un
                // istante — sotto la telefonata.
                if (player.playWhenReady) {
                    pausedByLoss = true
                    player.pause()
                }
            }

            // Temporanea con permesso di abbassare: una notifica.
            // La musica continua, piu' piano, e il bip si sente sopra.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Chi non vuole sentirle non vuole nemmeno che la musica
                // cali: non si fa niente, e il bip passa sotto. Il fuoco
                // resta nostro, quindi non c'e' niente da rimettere a
                // posto dopo.
                if (vuoleSentirle()) abbassa()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                rialza()
                if (pausedByLoss) {
                    pausedByLoss = false
                    player.play()
                }
            }
        }
    }

    companion object {
        const val KEY_NOTIFICHE = "sentire_notifiche"

        /**
         * Gli usi che valgono un abbassamento.
         *
         * Notifiche, sveglie e suonerie: cose corte che devono farsi
         * sentire. Fuori restano media e chiamate — la prima sarebbe
         * un'altra app di musica, e abbassare la nostra perche' ne suona
         * un'altra non ha senso; la seconda passa gia' dal fuoco audio,
         * dove si mette in pausa sul serio.
         */
        /** Quanto resta giu' dopo che il suono e' finito. */
        private const val CODA_ABBASSATO = 1_200L

        /** Oltre questo si rialza comunque: un volume basso per sempre
         *  e' il guasto peggiore dei due. */
        private const val MASSIMO_ABBASSATO = 8_000L

        /** Quante chiamate di fila devono vedere un suono altrui prima
         *  di abbassare. Vedi «I due irrobustimenti» piu' sopra. */
        private const val MINIMO_VOLTE_DI_FILA = 2

        /**
         * `USAGE_ASSISTANCE_SONIFICATION` non c'e' di proposito: e' il
         * bollino che Android mette sui suoni di interfaccia — il "click"
         * di un tasto, il bip di conferma di un'app — non solo sulle
         * notifiche vere. WhatsApp lo usa per il suono del messaggio
         * inviato, e con questo bollino dentro l'insieme ogni messaggio
         * mandato abbassava la musica come se fosse arrivata una
         * notifica. Tolto: quel suono non e' qualcosa che l'utente vuole
         * sentire sopra la musica, e' un feedback per chi sta scrivendo.
         */
        private val USI_DA_SENTIRE = setOf(
            AudioAttributes.USAGE_NOTIFICATION,
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
            AudioAttributes.USAGE_NOTIFICATION_EVENT,
            AudioAttributes.USAGE_ALARM,
            AudioAttributes.USAGE_ASSISTANT,
        )
    }


    // ------------------------------------------- quando nessuno chiede

    /**
     * Abbassa anche quando nessuno ha chiesto il fuoco audio.
     *
     * ## Perche' serve
     *
     * Il fuoco audio funziona solo se l'altra app lo **chiede**. Un
     * navigatore lo chiede, l'assistente lo chiede — ma il suono di
     * una notifica quasi sempre no: il sistema lo suona sopra la musica
     * senza avvisare nessuno. Chi ascolta non lo sa e vede solo che il
     * volume non cala.
     *
     * Qui si guarda la cosa dall'altro lato: invece di aspettare una
     * richiesta, si guarda **cosa sta suonando sul telefono**. Se
     * compare qualcosa marcato come notifica o allarme mentre suoniamo
     * noi, si abbassa; quando sparisce, si rialza.
     *
     * ## Cosa non garantisce
     *
     * Che il sistema ci dica sempre di che tipo era quel suono: da
     * Android 9 le informazioni sulle altre app sono ridotte, e un'app
     * senza permessi speciali vede una versione ripulita. Dove
     * l'informazione manca non si fa niente — meglio non abbassare che
     * abbassare a caso.
     *
     * ## L'irrobustimento contro il volume che si alza da solo
     *
     * Segnalato su un dispositivo fuori da quelli di prova: si abbassa
     * e si rialza da solo dopo `MASSIMO_ABBASSATO`, a ogni singolo
     * brano — su altoparlante e su auricolari, sempre. Mai riprodotto
     * sui telefoni provati finora (compreso l'emulatore), il che punta
     * a una particolarita' di quel dispositivo: un livello di
     * post-elaborazione audio del produttore, o una versione di Android
     * che riporta `AudioPlaybackConfiguration` in modo diverso in quel
     * primo istante.
     *
     * `AudioPlaybackConfiguration` non espone pubblicamente ne' un
     * identificativo di sessione ne' il pacchetto d'origine (e' proprio
     * il "un'app senza permessi speciali vede una versione ripulita" di
     * cui sopra) — quindi non c'e' modo di escludere per certo la nostra
     * stessa riproduzione da [configs] confrontandola con qualcosa di
     * nostro. Quello che si puo' fare senza riprodurre il guasto e'
     * togliere fiducia a un singolo fotogramma: serve vedere «altrui»
     * per [MINIMO_VOLTE_DI_FILA] chiamate di fila prima di abbassare. Un
     * vero suono di notifica dura ben piu' di una chiamata; un
     * fotogramma isolato, prima che i nostri `AudioAttributes` si
     * stabilizzino o durante un rimbalzo del sistema, no.
     */
    private val osservatore = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            val altri = configs.count { it.audioAttributes.usage in USI_DA_SENTIRE }
            ultimoSuonoAltrui = if (altri > 0) System.currentTimeMillis() else ultimoSuonoAltrui
            quantiAltri = altri

            if (altri > 0) {
                volteDiFilaAltrui++
                if (volteDiFilaAltrui >= MINIMO_VOLTE_DI_FILA && player.playWhenReady && vuoleSentirle()) {
                    manina.removeCallbacks(rialzaDaSolo)
                    abbassa()
                    // Una rete di sicurezza: se la sparizione non
                    // arrivasse mai, il volume resterebbe basso per
                    // sempre — ed e' il guasto peggiore dei due.
                    riprendiTraPoco(MASSIMO_ABBASSATO)
                }
            } else {
                volteDiFilaAltrui = 0
                // Non si rialza di scatto.
                //
                // Un bip dura un secondo: abbassare e rialzare dentro
                // quel secondo produce un buco che l'orecchio non
                // registra come «la musica e' calata» — si sente solo un
                // disturbo. Restando giu' ancora un momento il calo si
                // percepisce per quello che e'.
                riprendiTraPoco(CODA_ABBASSATO)
            }
        }
    }

    /** Quante chiamate di fila hanno visto un suono altrui: si abbassa
     *  solo alla seconda, non alla prima. Vedi il commento sopra. */
    private var volteDiFilaAltrui = 0

    /** L'ultima volta che un'altra app ha suonato qualcosa, o 0. */
    @Volatile var ultimoSuonoAltrui: Long = 0
        private set

    /** Quanti suoni altrui sono in corso adesso. */
    @Volatile var quantiAltri: Int = 0
        private set

    private val manina = android.os.Handler(android.os.Looper.getMainLooper())
    private val rialzaDaSolo = Runnable { rialza() }

    private fun riprendiTraPoco(fraQuanto: Long) {
        manina.removeCallbacks(rialzaDaSolo)
        manina.postDelayed(rialzaDaSolo, fraQuanto)
    }

    fun osserva() {
        runCatching { audioManager.registerAudioPlaybackCallback(osservatore, manina) }
    }

    fun smettiDiOsservare() {
        runCatching { audioManager.unregisterAudioPlaybackCallback(osservatore) }
        manina.removeCallbacks(rialzaDaSolo)
    }

    /** Il volume di prima, da ripristinare quando il bip e' passato. */
    private var volumePrima: Float? = null

    private fun abbassa() {
        if (volumePrima != null) return
        volumePrima = player.volume
        // Un quinto: abbastanza basso da far capire la notifica, non
        // cosi' basso da sembrare che la musica si sia fermata.
        player.volume = 0.2f
    }

    private fun rialza() {
        volumePrima?.let { player.volume = it }
        volumePrima = null
    }

    /**
     * Da chiamare quando la riproduzione viene fermata dall'utente.
     * Se invece l'abbiamo fermata noi per una telefonata, il fuoco resta
     * prenotato: e' cosi' che la musica riparte da sola a chiamata finita.
     */
    fun onUserPaused() {
        if (!pausedByLoss) release()
    }
}
