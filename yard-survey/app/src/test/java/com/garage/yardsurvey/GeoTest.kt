package com.garage.yardsurvey

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GeoTest {
    private val boulder = LatLonAlt(40.0, -105.0, 1650.0)

    @Test fun enuRoundTrip() {
        val off = Enu(12.345, -45.678, 1.234)
        val p = Geo.fromEnu(boulder, off)
        val back = Geo.toEnu(boulder, p)
        assertEquals(off.e, back.e, 1e-6)
        assertEquals(off.n, back.n, 1e-6)
        assertEquals(off.u, back.u, 1e-9)
    }

    @Test fun oneMilliDegreeOfLatitudeAt45() {
        // M(45°) = 6367381.8 m  ->  0.001° = 111.13 m
        val o = LatLonAlt(45.0, 10.0, 0.0)
        val e = Geo.toEnu(o, LatLonAlt(45.001, 10.0, 0.0))
        assertEquals(111.13, e.n, 0.02)
        assertEquals(0.0, e.e, 1e-9)
    }

    @Test fun oneMilliDegreeOfLongitudeAt45() {
        // N(45°)·cos45 = 4517591 m/rad  ->  0.001° = 78.85 m
        val o = LatLonAlt(45.0, 10.0, 0.0)
        val e = Geo.toEnu(o, LatLonAlt(45.0, 10.001, 0.0))
        assertEquals(78.85, e.e, 0.02)
        assertEquals(0.0, e.n, 1e-9)
    }

    @Test fun longitudeWrapsAcrossDateline() {
        val o = LatLonAlt(0.0, 179.9995, 0.0)
        val e = Geo.toEnu(o, LatLonAlt(0.0, -179.9995, 0.0))
        // 0.001° at the equator = 111.32 m, heading EAST across the line
        assertEquals(111.32, e.e, 0.02)
        val back = Geo.fromEnu(o, e)
        assertEquals(-179.9995, back.lon, 1e-9)
    }

    @Test fun plumbTipIsStraightDown() {
        val t = Geo.tipOffset(Vec3(0.0, 0.0, 1.0), 2.0)
        assertEquals(0.0, t.e, 1e-12); assertEquals(0.0, t.n, 1e-12); assertEquals(-2.0, t.u, 1e-12)
    }

    @Test fun tiltedRodTipIsOppositeTheLean() {
        // Top of the rod leans 10° east: the phone is east of the tip, so the tip is WEST of the phone.
        val a = Math.toRadians(10.0)
        val t = Geo.tipOffset(Vec3(sin(a), 0.0, cos(a)), 2.0)
        assertEquals(-2.0 * sin(a), t.e, 1e-12)   // -0.3473
        assertEquals(0.0, t.n, 1e-12)
        assertEquals(-2.0 * cos(a), t.u, 1e-12)   // -1.9696
    }

    @Test fun tipOffsetNormalisesInput() {
        val t = Geo.tipOffset(Vec3(0.0, 0.0, 5.0), 1.5)
        assertEquals(-1.5, t.u, 1e-12)
    }

    @Test fun magneticToTrueRotatesByDeclination() {
        // Magnetic north with +10° (east) declination is at true azimuth 10°.
        val v = Geo.magneticToTrue(Vec3(0.0, 1.0, 0.0), 10.0)
        val a = Math.toRadians(10.0)
        assertEquals(sin(a), v.x, 1e-12)
        assertEquals(cos(a), v.y, 1e-12)
        assertEquals(0.0, v.z, 1e-12)
    }

    @Test fun bearingAndGrade() {
        assertEquals(45.0, Enu(1.0, 1.0, 0.0).bearingDeg, 1e-9)
        assertEquals(270.0, Enu(-1.0, 0.0, 0.0).bearingDeg, 1e-9)
        assertEquals(180.0, Enu(0.0, -3.0, 0.0).bearingDeg, 1e-9)
        assertEquals(10.0, Enu(10.0, 0.0, 1.0).gradePercent, 1e-9)
        assertEquals(-5.0, Enu(3.0, 4.0, -0.25).gradePercent, 1e-9)
    }

    @Test fun normDeg() {
        assertEquals(350.0, Geo.normDeg(-10.0), 1e-12)
        assertEquals(10.0, Geo.normDeg(370.0), 1e-12)
        assertEquals(0.0, Geo.normDeg(360.0), 1e-12)
    }
}
