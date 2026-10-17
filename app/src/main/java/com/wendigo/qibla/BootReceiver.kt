package com.wendigo.qibla

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores prayer-time alarms after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> PrayerScheduler.rescheduleFromPrefs(context)
        }
    }
}
