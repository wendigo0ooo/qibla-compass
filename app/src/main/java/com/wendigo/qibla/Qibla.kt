package com.wendigo.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle bearing/distance to the Kaaba plus geodesic helpers.
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

    /** Great-circle distance to the Kaaba in kilometres. */
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

    /**
     * Spherical interpolation between two coordinates. Returns [segments]+1
     * (lat, lon) pairs describing the great-circle path.
     */
    fun greatCircle(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double, segments: Int
    ): List<Pair<Double, Double>> {
        val phi1 = Math.toRadians(lat1)
        val lam1 = Math.toRadians(lon1)
        val phi2 = Math.toRadians(lat2)
        val lam2 = Math.toRadians(lon2)

        val d = 2 * kotlin.math.asin(
            sqrt(
                sin((phi2 - phi1) / 2).let { it * it } +
                        cos(phi1) * cos(phi2) * sin((lam2 - lam1) / 2).let { it * it }
            )
        )
        if (d == 0.0) return listOf(lat1 to lon1, lat2 to lon2)

        val result = ArrayList<Pair<Double, Double>>(segments + 1)
        for (i in 0..segments) {
            val f = i.toDouble() / segments
            val a = sin((1 - f) * d) / sin(d)
            val b = sin(f * d) / sin(d)
            val x = a * cos(phi1) * cos(lam1) + b * cos(phi2) * cos(lam2)
            val y = a * cos(phi1) * sin(lam1) + b * cos(phi2) * sin(lam2)
            val z = a * sin(phi1) + b * sin(phi2)
            val phiI = atan2(z, sqrt(x * x + y * y))
            val lamI = atan2(y, x)
            result.add(Math.toDegrees(phiI) to Math.toDegrees(lamI))
        }
        return result
    }
}
