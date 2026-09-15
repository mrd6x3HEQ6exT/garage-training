package com.garage.yardsurvey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CaptureTest {
    private val origin = LatLonAlt(40.0, -105.0, 1650.0)
    private val plumb = Attitude.PLUMB

    private fun sample(off: Enu, att: Attitude? = plumb, t: Long = 0L, hAcc: Double = 3.0, vAcc: Double = Double.NaN, msl: Double = Double.NaN) =
        FixSample(t, Geo.fromEnu(origin, off), hAcc, vAcc, msl, att)

    @Test fun identicalPlumbFixesGiveTipBelowAntenna() {
        val s = CaptureSession(2.0, tiltCompensate = true)
        repeat(5) { s.add(sample(Enu.ZERO, t = it * 1000L)) }
        val r = s.reduce()
        assertEquals(origin.lat, r.tip.lat, 1e-12)
        assertEquals(origin.lon, r.tip.lon, 1e-12)
        assertEquals(origin.alt - 2.0, r.tip.alt, 1e-9)
        assertEquals(origin.alt, r.antenna.alt, 1e-9)
        assertEquals(0.0, r.hSpread, 1e-9)
        assertEquals(0.0, r.vSpread, 1e-9)
        assertEquals(5, r.samples)
        assertEquals(4000L, r.durationMs)
        assertTrue(r.meanVAcc.isNaN())
        assertTrue(r.mslOffset.isNaN())
    }

    @Test fun symmetricScatterAveragesOutAndReportsSpread() {
        val s = CaptureSession(1.0, tiltCompensate = false)
        s.add(sample(Enu(1.0, 0.0, 0.5)))
        s.add(sample(Enu(-1.0, 0.0, -0.5)))
        s.add(sample(Enu(0.0, 1.0, 0.5)))
        s.add(sample(Enu(0.0, -1.0, -0.5)))
        val r = s.reduce()
        val tip = Geo.toEnu(origin, r.tip)
        assertEquals(0.0, tip.e, 1e-6)
        assertEquals(0.0, tip.n, 1e-6)
        assertEquals(-1.0, tip.u, 1e-9)
        assertEquals(1.0, r.hSpread, 1e-9)                 // RMS radial scatter
        assertEquals(Math.sqrt(1.0 / 3.0), r.vSpread, 1e-9) // sample std-dev of ±0.5
    }

    @Test fun tiltCompensationMovesTipAgainstTheLean() {
        val a = Math.toRadians(10.0)
        val leaningEast = Attitude(Vec3(sin(a), 0.0, cos(a)), 10.0, 90.0, 0.0, sin(a), 0.0)
        val s = CaptureSession(2.0, tiltCompensate = true)
        repeat(3) { s.add(sample(Enu.ZERO, leaningEast)) }
        val r = s.reduce()
        val tip = Geo.toEnu(origin, r.tip)
        assertEquals(-2.0 * sin(a), tip.e, 1e-6)
        assertEquals(0.0, tip.n, 1e-6)
        assertEquals(-2.0 * cos(a), tip.u, 1e-6)
        assertTrue(r.tiltCompensated)
        assertEquals(10.0, r.meanTiltDeg, 1e-9)
        assertEquals(90.0, r.meanLeanAzimuthDeg, 1e-6)
    }

    @Test fun compensationOffAssumesPlumb() {
        val a = Math.toRadians(10.0)
        val leaningEast = Attitude(Vec3(sin(a), 0.0, cos(a)), 10.0, 90.0, 0.0, sin(a), 0.0)
        val s = CaptureSession(2.0, tiltCompensate = false)
        s.add(sample(Enu.ZERO, leaningEast))
        val r = s.reduce()
        val tip = Geo.toEnu(origin, r.tip)
        assertEquals(0.0, tip.e, 1e-9)
        assertEquals(-2.0, tip.u, 1e-9)
        assertFalse(r.tiltCompensated)
        assertEquals(10.0, r.meanTiltDeg, 1e-9)   // still recorded for the log
    }

    @Test fun missingAttitudeFallsBackToPlumbPerSample() {
        val s = CaptureSession(1.5, tiltCompensate = true)
        s.add(sample(Enu.ZERO, att = null))
        val r = s.reduce()
        assertFalse(r.tiltCompensated)
        assertEquals(origin.alt - 1.5, r.tip.alt, 1e-9)
        assertEquals(0.0, r.meanTiltDeg, 0.0)
    }

    @Test fun circularMeanOfAzimuthsAcrossNorth() {
        val s = CaptureSession(1.0, tiltCompensate = true)
        s.add(sample(Enu.ZERO, Attitude(Vec3(0.0, 0.0, 1.0), 0.0, 350.0, 350.0, 0.0, 0.0)))
        s.add(sample(Enu.ZERO, Attitude(Vec3(0.0, 0.0, 1.0), 0.0, 10.0, 10.0, 0.0, 0.0)))
        val r = s.reduce()
        assertEquals(0.0, r.meanLeanAzimuthDeg, 1e-6)
        assertEquals(0.0, r.meanHeadingDeg, 1e-6)
    }

    @Test fun accuracyAndMslAveragesSkipMissing() {
        val s = CaptureSession(1.0, tiltCompensate = true)
        s.add(sample(Enu.ZERO, hAcc = 2.0, vAcc = 4.0, msl = -20.0))
        s.add(sample(Enu.ZERO, hAcc = 4.0, vAcc = Double.NaN, msl = Double.NaN))
        val r = s.reduce()
        assertEquals(3.0, r.meanHAcc, 1e-12)
        assertEquals(4.0, r.meanVAcc, 1e-12)
        assertEquals(-20.0, r.mslOffset, 1e-12)
    }

    @Test fun runningSpreadIsZeroUntilTwoSamples() {
        val s = CaptureSession(1.0, tiltCompensate = true)
        assertEquals(0.0, s.runningSpread(), 0.0)
        s.add(sample(Enu.ZERO))
        assertEquals(0.0, s.runningSpread(), 0.0)
        s.add(sample(Enu(2.0, 0.0, 0.0)))
        assertEquals(1.0, s.runningSpread(), 1e-9)
    }
}
