package com.wendigo.qibla

import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Calculation conventions for prayer times. */
enum class PrayerMethod(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaMinutes: Double?,
    val asrShadow: Double,
    val label: String
) {
    DIYANET(18.0, 17.0, null, 1.0, "Diyanet (Türkiye)"),
    MWL(18.0, 17.0, null, 1.0, "Müslüman Dünya Birliği"),
    ISNA(15.0, 15.0, null, 1.0, "ISNA (K. Amerika)"),
    EGYPT(19.5, 17.5, null, 1.0, "Mısır"),
    KARACHI(18.0, 18.0, null, 1.0, "Karaçi"),
    MAKKAH(18.5, 0.0, 90.0, 1.0, "Ümmü'l-Kura (Mekke)")
}

/**
 * Solar prayer time calculator based on the PrayTimes.org algorithm, plus
 * the moment the sun lines up with the Qibla direction.
 */
object PrayerTimes {

    data class Times(
        val fajr: Double,
        val sunrise: Double,
        val dhuhr: Double,
        val asr: Double,
        val maghrib: Double,
        val isha: Double
    )

    /** Local decimal hours for the given civil date. */
    fun compute(
        year: Int, month: Int, day: Int,
        lat: Double, lon: Double, tz: Double,
        method: PrayerMethod
    ): Times {
        val jd = julianDate(year, month, day) - lon / (15.0 * 24.0)
        val (decl, eqt) = sunPosition(jd + 0.5 - tz / 24.0)

        val dhuhr = 12.0 + tz - lon / 15.0 - eqt

        fun hourAngle(alt: Double): Double {
            val c = (dsin(alt) - dsin(lat) * dsin(decl)) / (dcos(lat) * dcos(decl))
            return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0))) / 15.0
        }

        val sunrise = dhuhr - hourAngle(-0.833)
        val sunset = dhuhr + hourAngle(-0.833)
        val fajr = dhuhr - hourAngle(-method.fajrAngle)

        val asrAlt = Math.toDegrees(atan(1.0 / (method.asrShadow + dtan(abs(lat - decl)))))
        val asr = dhuhr + hourAngle(asrAlt)

        val isha = method.ishaMinutes?.let { sunset + it / 60.0 }
            ?: dhuhr + hourAngle(-method.ishaAngle)

        return Times(fajr, sunrise, dhuhr, asr, sunset, isha)
    }

    /**
     * Local decimal hour when the sun sits exactly on the Qibla bearing for
     * this location today, or null if it does not happen (polar latitudes).
     */
    fun qiblaSunTime(
        times: Times, lat: Double, lon: Double, tz: Double, qibla: Double,
        year: Int, month: Int, day: Int
    ): Double? {
        val jd = julianDate(year, month, day) - lon / (15.0 * 24.0)
        val (decl, _) = sunPosition(jd + 0.5 - tz / 24.0)

        var havePrev = false
        var prevT = 0.0
        var prevDiff = 0.0
        var t = times.sunrise
        while (t <= times.maghrib) {
            val az = sunAzimuth(t, lat, decl, times.dhuhr)
            if (az != null) {
                val diff = angleDiff(az, qibla)
                if (havePrev && prevDiff * diff < 0.0) {
                    val frac = prevDiff / (prevDiff - diff)
                    return prevT + (t - prevT) * frac
                }
                prevT = t
                prevDiff = diff
                havePrev = true
            }
            t += 1.0 / 60.0
        }
        return null
    }

    fun format(hours: Double): String {
        var h = ((hours % 24) + 24) % 24
        var hh = h.toInt()
        var mm = ((h - hh) * 60).roundToInt()
        if (mm == 60) {
            hh = (hh + 1) % 24
            mm = 0
        }
        return String.format(Locale.US, "%02d:%02d", hh, mm)
    }

    // ---- astronomy helpers -------------------------------------------------

    private fun sunAzimuth(hour: Double, lat: Double, decl: Double, dhuhr: Double): Double? {
        val h = 15.0 * (hour - dhuhr)
        val sinAlt = dsin(lat) * dsin(decl) + dcos(lat) * dcos(decl) * dcos(h)
        val alt = dasin(sinAlt.coerceIn(-1.0, 1.0))
        val denom = dcos(alt) * dcos(lat)
        if (abs(denom) < 1e-9) return null
        val c = (dsin(decl) - dsin(alt) * dsin(lat)) / denom
        var az = Math.toDegrees(acos(c.coerceIn(-1.0, 1.0)))
        if (h > 0) az = 360.0 - az
        return az
    }

    private fun angleDiff(a: Double, b: Double): Double = ((a - b + 540.0) % 360.0) - 180.0

    private fun julianDate(y: Int, m: Int, d: Int): Double {
        var year = y
        var month = m
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + d + b - 1524.5
    }

    /** Returns (declination in degrees, equation of time in hours). */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(datan2(dcos(e) * dsin(l), dcos(l)) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = dasin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)

    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    private fun dsin(deg: Double) = sin(Math.toRadians(deg))
    private fun dcos(deg: Double) = cos(Math.toRadians(deg))
    private fun dtan(deg: Double) = tan(Math.toRadians(deg))
    private fun dasin(x: Double) = Math.toDegrees(asin(x))
    private fun datan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
}
