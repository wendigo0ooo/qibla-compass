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
import android.os.Build
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

    fun channelFor(minutes: Int): String = "prayer_reminder_$minutes"

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
        RingtoneManager.getDefaultUri(reminderRingtoneType(minutes))?.let { uri ->
            val attrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            channel.setSound(uri, attrs)
        }
        nm.createNotificationChannel(channel)
    }

    private fun reminderChannelName(minutes: Int): Int = when (minutes) {
        30 -> R.string.notif_channel_r30
        15 -> R.string.notif_channel_r15
        5 -> R.string.notif_channel_r5
        else -> R.string.notif_channel_name
    }

    private fun reminderRingtoneType(minutes: Int): Int = when (minutes) {
        30 -> RingtoneManager.TYPE_NOTIFICATION
        15 -> RingtoneManager.TYPE_RINGTONE
        5 -> RingtoneManager.TYPE_ALARM
        else -> RingtoneManager.TYPE_NOTIFICATION
    }

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
        minutes: Int = 0
    ) {
        ensureChannel(context)
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (minutes > 0) {
                builder.setSound(RingtoneManager.getDefaultUri(reminderRingtoneType(minutes)))
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
