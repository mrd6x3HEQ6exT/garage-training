package com.garage.yardsurvey

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A 3-vector. Used for directions (unit vectors) in either the device frame or the ENU world frame. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    val length: Double get() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val l = length
        return if (l < 1e-12) Vec3(0.0, 0.0, 1.0) else Vec3(x / l, y / l, z / l)
    }
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
}

/** East / North / Up offset in metres from some origin. */
data class Enu(val e: Double, val n: Double, val u: Double) {
    val horizontal: Double get() = hypot(e, n)
    val distance3d: Double get() = sqrt(e * e + n * n + u * u)
    /** Compass bearing (deg, 0..360, clockwise from north) from the origin to this offset. */
    val bearingDeg: Double get() = Geo.normDeg(Math.toDegrees(atan2(e, n)))
    /** Grade from the origin to this point as a percentage (rise over run). */
    val gradePercent: Double get() = if (horizontal < 1e-9) 0.0 else 100.0 * u / horizontal
    operator fun plus(o: Enu) = Enu(e + o.e, n + o.n, u + o.u)
    operator fun minus(o: Enu) = Enu(e - o.e, n - o.n, u - o.u)
    operator fun times(k: Double) = Enu(e * k, n * k, u * k)

    companion object {
        val ZERO = Enu(0.0, 0.0, 0.0)
    }
}

data class LatLonAlt(val lat: Double, val lon: Double, val alt: Double)

/**
 * WGS84 local-tangent-plane geodesy. Everything a backyard survey needs and nothing more:
 * a metre-accurate ENU frame around an origin. Error is sub-millimetre over 100 m and
 * ~cm over a few km, which is far below what any phone GNSS can resolve.
 */
object Geo {
    const val A = 6378137.0                 // WGS84 semi-major axis (m)
    const val F = 1.0 / 298.257223563       // flattening
    val E2: Double = F * (2 - F)            // first eccentricity squared

    /** Meridional radius of curvature (metres per radian of latitude). */
    fun radiusM(latDeg: Double): Double {
        val s = sin(Math.toRadians(latDeg))
        val w = 1 - E2 * s * s
        return A * (1 - E2) / (w * sqrt(w))
    }

    /** Prime-vertical radius of curvature (metres per radian of longitude, before the cos(lat) factor). */
    fun radiusN(latDeg: Double): Double {
        val s = sin(Math.toRadians(latDeg))
        return A / sqrt(1 - E2 * s * s)
    }

    /** Normalise degrees to [0, 360). */
    fun normDeg(d: Double): Double {
        var x = d % 360.0
        if (x < 0) x += 360.0
        if (x >= 360.0) x -= 360.0   // -1e-15 % 360 + 360 rounds to exactly 360.0
        return x
    }

    /** Wrap a longitude difference to [-180, 180]. */
    fun wrapLonDiff(d: Double): Double {
        var x = d
        while (x > 180.0) x -= 360.0
        while (x < -180.0) x += 360.0
        return x
    }

    /** ENU offset of [p] relative to [origin]. Radii are evaluated at the origin so [fromEnu] is an exact inverse. */
    fun toEnu(origin: LatLonAlt, p: LatLonAlt): Enu {
        val n = Math.toRadians(p.lat - origin.lat) * radiusM(origin.lat)
        val e = Math.toRadians(wrapLonDiff(p.lon - origin.lon)) * radiusN(origin.lat) * cos(Math.toRadians(origin.lat))
        return Enu(e, n, p.alt - origin.alt)
    }

    /** Inverse of [toEnu]. */
    fun fromEnu(origin: LatLonAlt, off: Enu): LatLonAlt {
        val dLat = Math.toDegrees(off.n / radiusM(origin.lat))
        val dLon = Math.toDegrees(off.e / (radiusN(origin.lat) * cos(Math.toRadians(origin.lat))))
        return LatLonAlt(origin.lat + dLat, wrapLon(origin.lon + dLon), origin.alt + off.u)
    }

    private fun wrapLon(lon: Double): Double {
        var x = lon
        while (x > 180.0) x -= 360.0
        while (x < -180.0) x += 360.0
        return x
    }

    /**
     * Where the rod tip is relative to the phone's antenna.
     * [rodUp] is the unit vector pointing from the tip toward the phone, in the true-north ENU frame.
     * The tip is [rodLen] metres back along that vector. With a plumb rod this is simply (0, 0, -rodLen).
     */
    fun tipOffset(rodUp: Vec3, rodLen: Double): Enu {
        val u = rodUp.normalized()
        return Enu(-rodLen * u.x, -rodLen * u.y, -rodLen * u.z)
    }

    /** Rotate horizontal components from a magnetic-north frame into a true-north frame (declination east-positive). */
    fun magneticToTrue(v: Vec3, declinationDeg: Double): Vec3 {
        val d = Math.toRadians(declinationDeg)
        val c = cos(d)
        val s = sin(d)
        // A vector at magnetic azimuth Am sits at true azimuth Am + d.
        return Vec3(v.x * c + v.y * s, v.y * c - v.x * s, v.z)
    }
}
