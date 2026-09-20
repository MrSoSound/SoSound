package com.sosound.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
