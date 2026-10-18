package com.wendigo.qibla

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Foreground service that keeps an ongoing notification with a live countdown to the
 * next prayer time plus a row of all six times of the day.
 *
 * The layout is a custom (decorated) RemoteViews; the countdown uses a [Chronometer]
 * inside the RemoteViews so the system animates it without extra wake-ups. The service
 * only refreshes at prayer boundaries.
 *
 * Android 13+ lets users dismiss foreground-service notifications even with
 * `setOngoing(true)`; a delete intent re-posts the notification so it stays visible.
 */
class PrayerCountdownService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        ContextCompat.registerReceiver(
            this, dismissReceiver, IntentFilter(ACTION_DISMISSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelBoundary()
        try {
            unregisterReceiver(dismissReceiver)
        } catch (_: Exception) {
        }
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val n = currentNotification ?: return
            try {
                NotificationManagerCompat.from(this@PrayerCountdownService)
                    .notify(NOTIF_ID, n)
            } catch (_: Exception) {
            }
        }
    }

    private fun refresh() {
        val vals = todayValues()
        val next = if (vals != null) nextFrom(vals) else null
        val notif = try {
            buildNotification(vals, next)
        } catch (_: Throwable) {
            simpleNotification()
        }
        currentNotification = notif
        startFg(NOTIF_ID, notif)
        if (next != null) scheduleBoundary(next.millis) else scheduleRetry()
    }

    // ---- prayer time helpers ----

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun latLon(): Pair<Double, Double>? {
        val p = prefs()
        val la = p.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lo = p.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        return la to lo
    }

    /** Six values (fajr, sunrise, dhuhr, asr, maghrib, isha) in local decimal hours. */
    private fun todayValues(): DoubleArray? {
        val (la, lo) = latLon() ?: return null
        val cal = Calendar.getInstance()
        val tz = cal.timeZone.getOffset(cal.timeInMillis) / 3_600_000.0
        val t = PrayerTimes.compute(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH), la, lo, tz, PrayerMethod.DIYANET
        )
        return doubleArrayOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
    }

    private data class Next(val idx: Int, val millis: Long, val hour: Double)

    private fun nextFrom(vals: DoubleArray): Next {
        val cal = Calendar.getInstance()
        val nowH = cal.get(Calendar.HOUR_OF_DAY) +
                cal.get(Calendar.MINUTE) / 60.0 + cal.get(Calendar.SECOND) / 3600.0
        for (idx in PRAYER_IDX) {
            if (vals[idx] > nowH) return Next(idx, millisAt(cal, vals[idx]), vals[idx])
        }
        val (la, lo) = latLon() ?: return Next(0, 0L, vals[0])
        val tom = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tz = cal.timeZone.getOffset(cal.timeInMillis) / 3_600_000.0
        val t = PrayerTimes.compute(
            tom.get(Calendar.YEAR), tom.get(Calendar.MONTH) + 1,
            tom.get(Calendar.DAY_OF_MONTH), la, lo, tz, PrayerMethod.DIYANET
        )
        return Next(0, millisAt(tom, t.fajr), t.fajr)
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

    // ---- notification ----

    private var currentNotification: Notification? = null

    private fun buildNotification(vals: DoubleArray?, next: Next?): Notification {
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_notif)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(dismissIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(getColor(R.color.accent))
            .addAction(0, getString(R.string.notif_countdown_action), refreshIntent())

        if (vals == null || next == null) {
            return builder
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_countdown_waiting))
                .build()
        }

        val name = getString(NAME_RES[next.idx])
        val title = getString(R.string.notif_countdown_next, name, PrayerTimes.format(next.hour))

        val small = RemoteViews(packageName, R.layout.notification_countdown)
        val big = RemoteViews(packageName, R.layout.notification_countdown_big)
        fillViews(small, vals, next)
        fillViews(big, vals, next)

        builder
            .setContentTitle(title)
            .setContentText(getString(R.string.notif_countdown_hint))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
        return builder.build()
    }

    private fun fillViews(rv: RemoteViews, vals: DoubleArray, next: Next) {
        val accent = getColor(R.color.notif_accent)

        rv.setTextViewText(R.id.ncLabelNext, getString(R.string.nc_next_label, getString(NAME_RES[next.idx])))
        rv.setTextViewText(R.id.ncNextSmall, getString(R.string.nc_next_small))
        rv.setTextViewText(R.id.ncNextBig, getString(NAME_RES[next.idx]))
        rv.setTextViewText(R.id.ncLeft, getString(R.string.nc_left))

        val base = SystemClock.elapsedRealtime() + (next.millis - System.currentTimeMillis())
        for (id in intArrayOf(R.id.ncChrono, R.id.ncChronoBig)) {
            rv.setChronometerCountDown(id, true)
            rv.setChronometer(id, base, null, true)
            rv.setTextColor(id, accent)
        }

        for (i in NAME_RES.indices) {
            rv.setTextViewText(NAME_IDS[i], getString(NAME_RES[i]))
            rv.setTextViewText(TIME_IDS[i], PrayerTimes.format(vals[i]))
            val active = i == next.idx
            rv.setInt(
                COL_IDS[i], "setBackgroundColor",
                if (active) 0x4026A69A.toInt() else 0x1426A69A.toInt()
            )
        }

        val city = prefs().getString(PrayerScheduler.KEY_CITY, null)
        rv.setTextViewText(R.id.ncCity, city?.takeIf { it.isNotBlank() } ?: "")
    }

    private fun simpleNotification(): Notification =
        NotificationCompat.Builder(this, NotificationHelper.CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_notif)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(dismissIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(getColor(R.color.accent))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_countdown_waiting))
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun refreshIntent(): PendingIntent =
        PendingIntent.getService(
            this, 1, serviceIntent(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun dismissIntent(): PendingIntent {
        val intent = Intent(ACTION_DISMISSED).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(action: String): Intent =
        Intent(this, PrayerCountdownService::class.java).setAction(action)

    // ---- scheduling ----

    private fun scheduleBoundary(nextMillis: Long) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextMillis + 1500L
        val pi = PendingIntent.getService(
            this, ALARM_CODE, serviceIntent(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun scheduleRetry() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, ALARM_CODE, serviceIntent(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15 * 60_000L, pi)
    }

    private fun cancelBoundary() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getService(
            this, ALARM_CODE, serviceIntent(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    private fun startFg(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            super.startForeground(id, notification, FOREGROUND_TYPE)
        } else {
            super.startForeground(id, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "com.wendigo.qibla.COUNTDOWN_START"
        const val ACTION_REFRESH = "com.wendigo.qibla.COUNTDOWN_REFRESH"
        const val ACTION_STOP = "com.wendigo.qibla.COUNTDOWN_STOP"
        const val ACTION_DISMISSED = "com.wendigo.qibla.COUNTDOWN_DISMISSED"

        private const val NOTIF_ID = 500
        private const val ALARM_CODE = 9901
        private const val PREFS = "qibla_prefs"
        private const val KEY_LAT = "device_lat"
        private const val KEY_LON = "device_lon"
        private const val FOREGROUND_TYPE = 0x40000000 // FOREGROUND_SERVICE_TYPE_SPECIAL_USE

        private val NAME_RES = intArrayOf(
            R.string.p_fajr, R.string.p_sunrise, R.string.p_dhuhr,
            R.string.p_asr, R.string.p_maghrib, R.string.p_isha
        )

        private val NAME_IDS = intArrayOf(
            R.id.ncName0, R.id.ncName1, R.id.ncName2,
            R.id.ncName3, R.id.ncName4, R.id.ncName5
        )

        private val TIME_IDS = intArrayOf(
            R.id.ncTime0, R.id.ncTime1, R.id.ncTime2,
            R.id.ncTime3, R.id.ncTime4, R.id.ncTime5
        )

        private val COL_IDS = intArrayOf(
            R.id.ncCol0, R.id.ncCol1, R.id.ncCol2,
            R.id.ncCol3, R.id.ncCol4, R.id.ncCol5
        )

        /** Indices used for "next prayer" (sunrise is not a prayer). */
        private val PRAYER_IDX = intArrayOf(0, 2, 3, 4, 5)

        fun start(context: Context) {
            val intent = Intent(context, PrayerCountdownService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PrayerCountdownService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
