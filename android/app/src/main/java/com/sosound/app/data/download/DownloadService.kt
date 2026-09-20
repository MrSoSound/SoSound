package com.sosound.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sosound.app.MainActivity
import com.sosound.app.R
import com.sosound.app.SoSoundApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tiene vivo il processo mentre la coda scarica.
 *
 * Senza un servizio in primo piano, chiudere l'app durante un download
 * lo interromperebbe a meta': Android sospende il processo appena
 * l'ultima schermata sparisce. La notifica e' il prezzo obbligatorio
 * per avere quel permesso, ed e' anche onesta - sta succedendo qualcosa.
 */
class DownloadService : Service() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForeground(NOTIFICATION_ID, build("Preparo il download", null))

        val queue = (application as SoSoundApp).downloadQueue
        job = scope.launch {
            queue.items.collectLatest { items ->
                val active = items.firstOrNull {
                    it.state == QueueItem.State.IN_CORSO ||
                        it.state == QueueItem.State.AGGIORNO
                } ?: items.firstOrNull { it.state == QueueItem.State.IN_ATTESA }

                if (active == null) {
                    stopSelf()
                    return@collectLatest
                }
                val waiting = items.count { it.state == QueueItem.State.IN_ATTESA }
                val text = buildString {
                    // Durante l'aggiornamento il titolo del brano non dice
                    // niente di utile: sta succedendo altro.
                    if (active.state == QueueItem.State.AGGIORNO) {
                        append("Aggiorno yt-dlp…")
                    } else {
                        append("${active.track.artist} — ${active.track.title}")
                        if (waiting > 0) append("  (+$waiting in coda)")
                    }
                }
                notify(build(text, active.progress))
            }
        }
    }

    private fun build(text: String, progress: Float?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scarico musica")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (progress != null && progress > 0f) {
                    setProgress(100, (progress * 100).toInt(), false)
                } else {
                    setProgress(0, 0, true)   // indeterminata: sta partendo
                }
            }
            .build()
    }

    private fun notify(n: Notification) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "sosound_download"
        private const val NOTIFICATION_ID = 3

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_channel_name),
                // LOW: niente suono a ogni aggiornamento della barra.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
