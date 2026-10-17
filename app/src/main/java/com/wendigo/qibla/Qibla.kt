package com.wendigo.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle bearing and distance from a point on Earth to the Kaaba.
 */
object Qibla {

    /** Kaaba, Makkah. */
    const val KAABA_LAT = 21.422487
    const val KAABA_LON = 39.826206

    private const val EARTH_RADIUS_KM = 6371.0088

    /**
     * Initial great-circle bearing to the Kaaba, in degrees (0 = true north,
     * clockwise). Always returns a value in [0, 360).
     */
    fun bearingToKaaba(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dLambda = Math.toRadians(KAABA_LON - lon)

        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /**
     * Great-circle distance to the Kaaba in kilometres.
     */
    fun distanceToKaaba(lat: Double, lon: Double): Double {
        val phi1 = Math.toRadians(lat)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(KAABA_LON - lon)

        val a = sin(dPhi / 2) * sin(dPhi / 2) +
                cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_KM * c
    }

    /** Smallest signed difference a-b in degrees, result in (-180, 180]. */
    fun angleDifference(a: Float, b: Float): Float {
        return ((a - b + 540f) % 360f) - 180f
    }
}
