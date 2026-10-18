package com.wendigo.qibla

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Creates the notification channels and posts prayer-time / reminder notifications.
 *
 * Every reminder interval has its own channel so the sound differs depending on how
 * many minutes are left before the prayer (e.g. 30 dk, 15 dk, 5 dk).
 */
object NotificationHelper {

    const val CHANNEL_VAKIT = "prayer_times"
    const val CHANNEL_COUNTDOWN = "prayer_countdown"
    const val SOUND_NONE = "none"

    private const val PREFS = "qibla_prefs"

    /** Bumped whenever channel sound config changes, so existing channels are rebuilt. */
    private const val CHANNEL_VERSION = 2

    fun channelFor(minutes: Int): String = "prayer_reminder_${CHANNEL_VERSION}_$minutes"

    fun soundKey(minutes: Int): String = "remind_sound_$minutes"

    private fun rawId(name: String): Int = when (name) {
        "ezan" -> R.raw.snd_ezan
        else -> 0
    }

    /**
     * Resolves the sound chosen for a reminder interval.
     *  - absent            -> system default for that interval
     *  - "none"            -> silent
     *  - "raw:<name>"      -> bundled instrumental sound
     *  - anything else     -> a device sound (content/file URI picked by the user)
     */
    fun reminderSoundUri(context: Context, minutes: Int): Uri? {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(soundKey(minutes), null)
        return when {
            v == null -> RingtoneManager.getDefaultUri(reminderRingtoneType(minutes))
            v == SOUND_NONE -> null
            v.startsWith("raw:") -> {
                val id = rawId(v.substring(4))
                if (id == 0) RingtoneManager.getDefaultUri(reminderRingtoneType(minutes))
                else Uri.parse("android.resource://${context.packageName}/$id")
            }
            else -> Uri.parse(v)
        }
    }

    /** Recreates the reminder channel so a newly chosen sound takes effect. */
    fun updateReminderChannel(context: Context, minutes: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.deleteNotificationChannel(channelFor(minutes))
        ensureReminderChannel(nm, context, minutes)
    }

    /** Removes channels left behind by older app versions. */
    private fun deleteLegacyChannels(nm: NotificationManager) {
        for (m in PrayerScheduler.REMIND_MINUTES) {
            nm.deleteNotificationChannel("prayer_reminder_$m")
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (nm.getNotificationChannel(CHANNEL_VAKIT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_VAKIT,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    enableVibration(true)
                }
            )
        }

        deleteLegacyChannels(nm)
        for (m in PrayerScheduler.REMIND_MINUTES) ensureReminderChannel(nm, context, m)
        ensureCountdownChannel(nm, context)
    }

    private fun ensureCountdownChannel(nm: NotificationManager, context: Context) {
        if (nm.getNotificationChannel(CHANNEL_COUNTDOWN) != null) return
        val channel = NotificationChannel(
            CHANNEL_COUNTDOWN,
            context.getString(R.string.notif_countdown_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_countdown_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun ensureReminderChannel(nm: NotificationManager, context: Context, minutes: Int) {
        val id = channelFor(minutes)
        if (nm.getNotificationChannel(id) != null) return
        val channel = NotificationChannel(
            id,
            context.getString(reminderChannelName(minutes)),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_reminder_desc)
            enableVibration(true)
        }
        val uri = reminderSoundUri(context, minutes)
        if (uri != null) {
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            channel.setSound(uri, attrs)
        } else {
            channel.setSound(null, null)
            channel.enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    private fun reminderChannelName(minutes: Int): Int = when (minutes) {
        30 -> R.string.notif_channel_r30
        15 -> R.string.notif_channel_r15
        5 -> R.string.notif_channel_r5
        else -> R.string.notif_channel_name
    }

    private fun reminderRingtoneType(minutes: Int): Int = RingtoneManager.TYPE_NOTIFICATION

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(
        context: Context,
        title: String,
        text: String,
        id: Int = 100,
        channel: String = CHANNEL_VAKIT,
        minutes: Int = 0,
        line: String? = null
    ) {
        ensureChannel(context)
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(line ?: text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context))
        if (line != null) {
            try {
                val cv = RemoteViews(context.packageName, R.layout.notification_prayer)
                cv.setTextViewText(R.id.notifTitle, title)
                cv.setTextViewText(R.id.notifLine, line)
                builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomContentView(cv)
                    .setCustomBigContentView(cv)
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (minutes > 0) {
                val uri = reminderSoundUri(context, minutes)
                if (uri != null) builder.setSound(uri)
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            } else {
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            }
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
