package com.wendigo.qibla

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules exact alarms for the five daily prayers (Diyanet times) and, optionally,
 * pre-prayer reminders, for today and tomorrow, so notifications appear even when the
 * app is closed.
 */
object PrayerScheduler {

    const val ACTION = "com.wendigo.qibla.ACTION_PRAYER"
    const val EXTRA_NAME = "prayer_name"
    const val EXTRA_CITY = "prayer_city"
    const val EXTRA_MINUTES = "reminder_minutes"
    const val EXTRA_TIME = "prayer_time"

    const val PREFS = "qibla_prefs"
    const val KEY_ENABLED = "notif_enabled"
    const val KEY_COUNTDOWN = "notif_countdown"
    const val KEY_LAT = "device_lat"
    const val KEY_LON = "device_lon"
    const val KEY_CITY = "notif_city"
    const val KEY_REMIND_PREFIX = "notif_remind_"

    /** Reminder intervals (minutes before the prayer), shared by all prayers. */
    val REMIND_MINUTES = intArrayOf(30, 15, 5)

    private const val LEGACY_MAX = 20

    private val NAME_RES = intArrayOf(
        R.string.p_fajr, R.string.p_dhuhr, R.string.p_asr, R.string.p_maghrib, R.string.p_isha
    )

    fun schedule(context: Context, lat: Double, lon: Double, city: String?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remind = REMIND_MINUTES.filter { prefs.getBoolean(KEY_REMIND_PREFIX + it, false) }

        val now = Calendar.getInstance()
        val tz = now.timeZone.getOffset(now.timeInMillis) / 3_600_000.0
        val today = now.clone() as Calendar
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val days = listOf(today, tomorrow)

        for ((di, day) in days.withIndex()) {
            val t = PrayerTimes.compute(
                day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1,
                day.get(Calendar.DAY_OF_MONTH), lat, lon, tz, PrayerMethod.DIYANET
            )
            val values = doubleArrayOf(t.fajr, t.dhuhr, t.asr, t.maghrib, t.isha)
            for (i in NAME_RES.indices) {
                val name = context.getString(NAME_RES[i])
                val prayerMillis = millisAt(day, values[i])
                scheduleAt(context, am, code(di, i, 0), prayerMillis, name, city, 0, prayerMillis)
                for ((ri, m) in remind.withIndex()) {
                    scheduleAt(
                        context, am, code(di, i, ri + 1),
                        millisAt(day, values[i] - m / 60.0), name, city, m, prayerMillis
                    )
                }
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (c in 0 until LEGACY_MAX) am.cancel(pending(context, c, null, null, 0))
        for (di in 0..1) for (i in NAME_RES.indices) {
            for (k in 0..REMIND_MINUTES.size) {
                am.cancel(pending(context, code(di, i, k), null, null, 0))
            }
        }
    }

    fun rescheduleFromPrefs(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY_COUNTDOWN, true)) {
            PrayerCountdownService.start(context)
        } else {
            PrayerCountdownService.stop(context)
        }
        if (!p.getBoolean(KEY_ENABLED, false)) {
            cancel(context)
            return
        }
        val la = p.getString(KEY_LAT, null)?.toDoubleOrNull()
        val lo = p.getString(KEY_LON, null)?.toDoubleOrNull()
        if (la != null && lo != null) schedule(context, la, lo, p.getString(KEY_CITY, null))
    }

    private fun code(dayIndex: Int, prayerIndex: Int, kind: Int): Int =
        dayIndex * 1000 + prayerIndex * 10 + kind

    private fun scheduleAt(
        context: Context, am: AlarmManager, code: Int, millis: Long,
        name: String, city: String?, minutes: Int, prayerMillis: Long
    ) {
        if (millis <= System.currentTimeMillis()) return
        setAlarm(context, am, millis, pending(context, code, name, city, minutes, prayerMillis))
    }

    private fun setAlarm(context: Context, am: AlarmManager, millis: Long, pi: PendingIntent) {
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

    private fun pending(
        context: Context, code: Int, name: String?, city: String?, minutes: Int,
        prayerMillis: Long = 0L
    ): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_CITY, city)
            putExtra(EXTRA_MINUTES, minutes)
            putExtra(EXTRA_TIME, prayerMillis)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, code, intent, flags)
    }
}
