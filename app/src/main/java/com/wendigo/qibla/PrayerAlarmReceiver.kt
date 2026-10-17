package com.wendigo.qibla

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a scheduled prayer time is reached and shows a notification. */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrayerScheduler.ACTION) return
        val name = intent.getStringExtra(PrayerScheduler.EXTRA_NAME) ?: return
        val city = intent.getStringExtra(PrayerScheduler.EXTRA_CITY)

        val title = context.getString(R.string.notif_title_fmt, name)
        val text = city?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)
        NotificationHelper.show(context, title, text)

        // Keep the chain alive by rescheduling the next day's alarms.
        PrayerScheduler.rescheduleFromPrefs(context)
    }
}
