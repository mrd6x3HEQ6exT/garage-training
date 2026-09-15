package com.garage.yardsurvey

import kotlin.math.sqrt

/** One GNSS fix plus the attitude the phone had at that instant. */
data class FixSample(
    val timeMs: Long,
    val pos: LatLonAlt,          // antenna position, altitude above the WGS84 ellipsoid
    val hAcc: Double,            // reported 68% horizontal accuracy radius, metres
    val vAcc: Double,            // reported 68% vertical accuracy, metres (NaN if unknown)
    val mslOffset: Double,       // (MSL altitude - ellipsoid altitude) if the platform supplied it, else NaN
    val attitude: Attitude?,     // null when no orientation sensor data was available
)

/** The averaged outcome of a capture window. All positions refer to the ROD TIP (ground), not the antenna. */
data class CaptureResult(
    val tip: LatLonAlt,          // ground point, ellipsoidal altitude
    val antenna: LatLonAlt,      // mean raw antenna position
    val samples: Int,
    val hSpread: Double,         // RMS horizontal scatter of the per-fix tip positions, metres
    val vSpread: Double,         // std-dev of the per-fix tip heights, metres
    val meanHAcc: Double,
    val meanVAcc: Double,        // NaN if never reported
    val mslOffset: Double,       // NaN if never reported
    val meanTiltDeg: Double,
    val meanLeanAzimuthDeg: Double,
    val meanHeadingDeg: Double,
    val tiltCompensated: Boolean,
    val durationMs: Long,
)

/**
 * Accumulates fixes over a capture window and reduces them to one ground point.
 *
 * Per fix, the rod tip is computed from the antenna position and the attitude at that moment
 * (so a rod that wobbles during the window is handled per-sample, not from an averaged tilt),
 * then the tips are averaged in a local ENU frame anchored at the first fix.
 */
class CaptureSession(
    private val rodLenM: Double,
    private val tiltCompensate: Boolean,
) {
    private val samples = ArrayList<FixSample>()
    val count: Int get() = samples.size
    val startMs: Long = System.currentTimeMillis()

    fun add(s: FixSample) { samples.add(s) }

    /** Running horizontal spread of tips so far (metres); 0 with fewer than 2 samples. */
    fun runningSpread(): Double = if (samples.size < 2) 0.0 else reduce().hSpread

    fun reduce(): CaptureResult {
        check(samples.isNotEmpty()) { "no samples" }
        val origin = samples[0].pos
        val tips = ArrayList<Enu>(samples.size)
        val antennas = ArrayList<Enu>(samples.size)
        var sumH = 0.0; var sumV = 0.0; var nV = 0
        var sumMsl = 0.0; var nMsl = 0
        var sumTilt = 0.0
        var sinLean = 0.0; var cosLean = 0.0
        var sinHead = 0.0; var cosHead = 0.0
        var nAtt = 0
        var anyCompensated = false

        for (s in samples) {
            val ant = Geo.toEnu(origin, s.pos)
            antennas.add(ant)
            val att = s.attitude
            val off = if (tiltCompensate && att != null) {
                anyCompensated = true
                Geo.tipOffset(att.rodUp, rodLenM)
            } else {
                Enu(0.0, 0.0, -rodLenM)
            }
            tips.add(ant + off)
            sumH += s.hAcc
            if (!s.vAcc.isNaN()) { sumV += s.vAcc; nV++ }
            if (!s.mslOffset.isNaN()) { sumMsl += s.mslOffset; nMsl++ }
            if (att != null) {
                nAtt++
                sumTilt += att.tiltDeg
                sinLean += Math.sin(Math.toRadians(att.leanAzimuthDeg)); cosLean += Math.cos(Math.toRadians(att.leanAzimuthDeg))
                sinHead += Math.sin(Math.toRadians(att.headingDeg)); cosHead += Math.cos(Math.toRadians(att.headingDeg))
            }
        }
        val n = samples.size.toDouble()
        val meanTip = mean(tips)
        val meanAnt = mean(antennas)
        var ssH = 0.0; var ssV = 0.0
        for (t in tips) {
            val d = t - meanTip
            ssH += d.e * d.e + d.n * d.n
            ssV += d.u * d.u
        }
        val hSpread = sqrt(ssH / n)
        val vSpread = if (samples.size > 1) sqrt(ssV / (n - 1)) else 0.0

        return CaptureResult(
            tip = Geo.fromEnu(origin, meanTip),
            antenna = Geo.fromEnu(origin, meanAnt),
            samples = samples.size,
            hSpread = hSpread,
            vSpread = vSpread,
            meanHAcc = sumH / n,
            meanVAcc = if (nV > 0) sumV / nV else Double.NaN,
            mslOffset = if (nMsl > 0) sumMsl / nMsl else Double.NaN,
            meanTiltDeg = if (nAtt > 0) sumTilt / nAtt else 0.0,
            meanLeanAzimuthDeg = if (nAtt > 0) Geo.normDeg(Math.toDegrees(Math.atan2(sinLean, cosLean))) else 0.0,
            meanHeadingDeg = if (nAtt > 0) Geo.normDeg(Math.toDegrees(Math.atan2(sinHead, cosHead))) else 0.0,
            tiltCompensated = anyCompensated,
            durationMs = samples.last().timeMs - samples.first().timeMs,
        )
    }

    private fun mean(v: List<Enu>): Enu {
        var e = 0.0; var n = 0.0; var u = 0.0
        for (x in v) { e += x.e; n += x.n; u += x.u }
        val k = v.size.toDouble()
        return Enu(e / k, n / k, u / k)
    }
}
