package com.wendigo.qibla

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fires when a prayer time or a pre-prayer reminder is reached and shows a notification. */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerScheduler.ACTION) return
        val name = intent.getStringExtra(PrayerScheduler.EXTRA_NAME) ?: return
        val city = intent.getStringExtra(PrayerScheduler.EXTRA_CITY)?.takeIf { it.isNotBlank() }
        val minutes = intent.getIntExtra(PrayerScheduler.EXTRA_MINUTES, 0)
        val time = intent.getLongExtra(PrayerScheduler.EXTRA_TIME, 0L)
            .takeIf { it > 0L }
            ?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) }

        val line = listOfNotNull(city, time).joinToString(" · ")
            .ifBlank { context.getString(R.string.app_name) }

        if (minutes > 0) {
            NotificationHelper.show(
                context,
                context.getString(R.string.notif_reminder_title, name, minutes),
                line,
                id = 200 + minutes,
                channel = NotificationHelper.channelFor(minutes),
                minutes = minutes,
                line = line
            )
        } else {
            NotificationHelper.show(
                context,
                context.getString(R.string.notif_vakit_entered, name),
                line,
                id = 100,
                line = line
            )
        }

        // Keep the chain alive by rescheduling the next day's alarms.
        PrayerScheduler.rescheduleFromPrefs(context)
    }
}
