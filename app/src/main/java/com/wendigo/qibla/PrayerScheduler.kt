package com.wendigo.qibla

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules exact alarms for the five daily prayers (Diyanet times) for today
 * and tomorrow, so a notification can be shown even when the app is closed.
 */
object PrayerScheduler {

    const val ACTION = "com.wendigo.qibla.ACTION_PRAYER"
    const val EXTRA_NAME = "prayer_name"
    const val EXTRA_CITY = "prayer_city"

    const val PREFS = "qibla_prefs"
    const val KEY_ENABLED = "notif_enabled"
    const val KEY_LAT = "device_lat"
    const val KEY_LON = "device_lon"
    const val KEY_CITY = "notif_city"

    private const val MAX_ALARMS = 12

    private val NAME_RES = intArrayOf(
        R.string.p_fajr, R.string.p_dhuhr, R.string.p_asr, R.string.p_maghrib, R.string.p_isha
    )

    fun schedule(context: Context, lat: Double, lon: Double, city: String?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context)

        val now = Calendar.getInstance()
        val tz = now.timeZone.getOffset(now.timeInMillis) / 3_600_000.0
        val today = now.clone() as Calendar
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }

        val times = listOf(
            today to PrayerTimes.compute(
                today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1,
                today.get(Calendar.DAY_OF_MONTH), lat, lon, tz, PrayerMethod.DIYANET
            ),
            tomorrow to PrayerTimes.compute(
                tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH) + 1,
                tomorrow.get(Calendar.DAY_OF_MONTH), lat, lon, tz, PrayerMethod.DIYANET
            )
        )

        var code = 0
        for ((day, t) in times) {
            val values = doubleArrayOf(t.fajr, t.dhuhr, t.asr, t.maghrib, t.isha)
            for (i in NAME_RES.indices) {
                val millis = millisAt(day, values[i])
                if (millis > System.currentTimeMillis()) {
                    setAlarm(context, am, code, millis, context.getString(NAME_RES[i]), city)
                }
                code++
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until MAX_ALARMS) am.cancel(pending(context, i, null, null))
    }

    fun rescheduleFromPrefs(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean(KEY_ENABLED, false)) {
            cancel(context)
            return
        }
        val la = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return
        val lo = p.getString(KEY_LON, null)?.toDoubleOrNull() ?: return
        schedule(context, la, lo, p.getString(KEY_CITY, null))
    }

    private fun setAlarm(
        context: Context, am: AlarmManager, code: Int, millis: Long,
        name: String, city: String?
    ) {
        val pi = pending(context, code, name, city)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun millisAt(day: Calendar, hours: Double): Long {
        val c = day.clone() as Calendar
        var totalMin = Math.round(hours * 60.0).toInt()
        if (totalMin >= 24 * 60) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            totalMin -= 24 * 60
        }
        c.set(Calendar.HOUR_OF_DAY, totalMin / 60)
        c.set(Calendar.MINUTE, totalMin % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun pending(context: Context, code: Int, name: String?, city: String?): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_CITY, city)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }
}
