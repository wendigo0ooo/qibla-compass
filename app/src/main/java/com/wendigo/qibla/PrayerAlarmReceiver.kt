package com.wendigo.qibla

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a prayer time or a pre-prayer reminder is reached and shows a notification. */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerScheduler.ACTION) return
        val name = intent.getStringExtra(PrayerScheduler.EXTRA_NAME) ?: return
        val city = intent.getStringExtra(PrayerScheduler.EXTRA_CITY)
        val minutes = intent.getIntExtra(PrayerScheduler.EXTRA_MINUTES, 0)

        val text = city?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)
        if (minutes > 0) {
            NotificationHelper.show(
                context,
                context.getString(R.string.notif_reminder_title, name, minutes),
                text,
                id = 200 + minutes,
                channel = NotificationHelper.channelFor(minutes),
                minutes = minutes
            )
        } else {
            NotificationHelper.show(
                context,
                context.getString(R.string.notif_title_fmt, name),
                text,
                id = 100
            )
        }

        // Keep the chain alive by rescheduling the next day's alarms.
        PrayerScheduler.rescheduleFromPrefs(context)
    }
}
