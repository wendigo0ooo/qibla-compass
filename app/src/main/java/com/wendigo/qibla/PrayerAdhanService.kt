package com.wendigo.qibla

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Vakit girdiğinde 15 saniyelik ezanı çalar. Bildirimdeki "Durdur" ile ya da
 * süre bitince kendiliğinden susar.
 */
class PrayerAdhanService : Service() {

    companion object {
        const val ACTION_START = "com.wendigo.qibla.ADHAN_START"
        const val ACTION_STOP = "com.wendigo.qibla.ADHAN_STOP"
        const val EXTRA_NAME = "adhan_name"
        const val EXTRA_CITY = "adhan_city"
        const val EXTRA_TIME = "adhan_time"
        const val EXTRA_INDEX = "adhan_index"

        private const val NOTIF_ID = 150
        private const val DURATION_MS = 15_000L

        fun start(context: Context, name: String, city: String?, timeMillis: Long, index: Int) {
            val i = Intent(context, PrayerAdhanService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_CITY, city)
                putExtra(EXTRA_TIME, timeMillis)
                putExtra(EXTRA_INDEX, index)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, PrayerAdhanService::class.java).apply { action = ACTION_STOP }
            try {
                context.startService(i)
            } catch (_: Exception) {
            }
        }
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { finish() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        val city = intent?.getStringExtra(EXTRA_CITY)
        val time = intent?.getLongExtra(EXTRA_TIME, 0L) ?: 0L
        val index = intent?.getIntExtra(EXTRA_INDEX, 0) ?: 0

        startForeground(NOTIF_ID, buildNotification(name, city, time, index))
        playAdhan()
        return START_NOT_STICKY
    }

    private fun playAdhan() {
        releasePlayer()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(this, Uri.parse("android.resource://$packageName/${R.raw.snd_adhan}"))
            mp.isLooping = false
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepare()
            mp.start()
            player = mp
            handler.postDelayed(stopRunnable, DURATION_MS)
        } catch (_: Exception) {
            finish()
        }
    }

    private fun buildNotification(
        name: String,
        city: String?,
        time: Long,
        index: Int
    ): Notification {
        NotificationHelper.ensureChannel(this)
        val timeText = time.takeIf { it > 0L }
            ?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) }
        val line = listOfNotNull(city?.takeIf { it.isNotBlank() }, timeText)
            .joinToString(" · ").ifBlank { getString(R.string.app_name) }
        val title = getString(R.string.notif_vakit_entered, name)
        val sub = getString(R.string.notif_adhan_playing)

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PrayerAdhanService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ADHAN)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(this, R.color.accent))
            .setContentTitle(title)
            .setContentText(line)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .addAction(0, getString(R.string.notif_adhan_stop), stopIntent)
        try {
            val cv = NotificationHelper.prayerRemoteViews(
                this, title, line, sub, NotificationHelper.prayerIcon(index)
            )
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(cv)
                .setCustomBigContentView(cv)
        } catch (_: Exception) {
        }
        return builder.build()
    }

    private fun finish() {
        handler.removeCallbacks(stopRunnable)
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        releasePlayer()
        super.onDestroy()
    }
}
