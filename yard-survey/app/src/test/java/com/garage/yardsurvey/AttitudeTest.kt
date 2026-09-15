package com.garage.yardsurvey

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rotation matrices are built by hand from device-axis directions: R's columns are the device
 * X, Y, Z axes expressed in world (east, north, up), exactly as SensorManager produces them.
 */
class AttitudeTest {
    private val s10 = sin(Math.toRadians(10.0))
    private val c10 = cos(Math.toRadians(10.0))

    private fun matrix(x: Vec3, y: Vec3, z: Vec3) = floatArrayOf(
        x.x.toFloat(), y.x.toFloat(), z.x.toFloat(),
        x.y.toFloat(), y.y.toFloat(), z.y.toFloat(),
        x.z.toFloat(), y.z.toFloat(), z.z.toFloat(),
    )

    private val east = Vec3(1.0, 0.0, 0.0)
    private val north = Vec3(0.0, 1.0, 0.0)
    private val up = Vec3(0.0, 0.0, 1.0)
    private val south = Vec3(0.0, -1.0, 0.0)

    @Test fun flatScreenUpTopNorthIsLevelHeadingNorth() {
        val a = Attitude.fromRotationMatrix(matrix(east, north, up), MountMode.FLAT_ON_TOP, 0.0)
        assertEquals(0.0, a.tiltDeg, 1e-4)
        assertEquals(0.0, a.headingDeg, 1e-4)
        assertEquals(0.0, a.bubbleX, 1e-6); assertEquals(0.0, a.bubbleY, 1e-6)
    }

    @Test fun flatTopEastHeadsEast() {
        val a = Attitude.fromRotationMatrix(matrix(south, east, up), MountMode.FLAT_ON_TOP, 0.0)
        assertEquals(90.0, a.headingDeg, 1e-4)
        assertEquals(0.0, a.tiltDeg, 1e-4)
    }

    @Test fun flatLeaningTowardTopEdgeMovesBubbleUp() {
        // Rod (device Z) leans 10° north; the phone's top edge dips.
        val z = Vec3(0.0, s10, c10)
        val y = Vec3(0.0, c10, -s10)
        val a = Attitude.fromRotationMatrix(matrix(east, y, z), MountMode.FLAT_ON_TOP, 0.0)
        assertEquals(10.0, a.tiltDeg, 1e-3)
        assertEquals(0.0, a.leanAzimuthDeg, 1e-3)
        assertEquals(0.0, a.bubbleX, 1e-6)
        assertEquals(-s10, a.bubbleY, 1e-6)   // negative = screen up
    }

    @Test fun portraitVerticalFacingNorth() {
        // Screen faces the operator standing south; back of phone faces north.
        val a = Attitude.fromRotationMatrix(matrix(east, up, south), MountMode.PORTRAIT_ON_ROD, 0.0)
        assertEquals(0.0, a.tiltDeg, 1e-4)
        assertEquals(0.0, a.headingDeg, 1e-4)
        assertEquals(0.0, a.bubbleX, 1e-6); assertEquals(0.0, a.bubbleY, 1e-6)
        assertEquals(1.0, a.rodUp.z, 1e-6)
    }

    @Test fun portraitTopLeaningEastBubbleRight() {
        val y = Vec3(s10, 0.0, c10)
        val x = Vec3(c10, 0.0, -s10)
        val a = Attitude.fromRotationMatrix(matrix(x, y, south), MountMode.PORTRAIT_ON_ROD, 0.0)
        assertEquals(10.0, a.tiltDeg, 1e-3)
        assertEquals(90.0, a.leanAzimuthDeg, 1e-3)
        assertEquals(0.0, a.headingDeg, 1e-3)
        assertEquals(s10, a.bubbleX, 1e-6)    // positive = screen right
        assertEquals(0.0, a.bubbleY, 1e-6)
        // And the tip offset then lands west and below the phone.
        val tip = Geo.tipOffset(a.rodUp, 2.0)
        assertEquals(-2 * s10, tip.e, 1e-5)
        assertEquals(-2 * c10, tip.u, 1e-5)
    }

    @Test fun portraitTopLeaningTowardOperatorBubbleDown() {
        val y = Vec3(0.0, -s10, c10)
        val z = Vec3(0.0, -c10, -s10)
        val a = Attitude.fromRotationMatrix(matrix(east, y, z), MountMode.PORTRAIT_ON_ROD, 0.0)
        assertEquals(10.0, a.tiltDeg, 1e-3)
        assertEquals(180.0, a.leanAzimuthDeg, 1e-3)
        assertEquals(0.0, a.headingDeg, 1e-3)
        assertEquals(0.0, a.bubbleX, 1e-6)
        assertEquals(s10, a.bubbleY, 1e-6)    // positive = screen down (toward operator)
    }

    @Test fun declinationShiftsHeadingAndLean() {
        val y = Vec3(s10, 0.0, c10)
        val x = Vec3(c10, 0.0, -s10)
        val a = Attitude.fromRotationMatrix(matrix(x, y, south), MountMode.PORTRAIT_ON_ROD, 8.5)
        assertEquals(8.5, a.headingDeg, 1e-3)
        assertEquals(98.5, a.leanAzimuthDeg, 1e-3)
        assertEquals(10.0, a.tiltDeg, 1e-3)   // tilt magnitude is unaffected
    }

    @Test fun headingWrapsPositive() {
        // Back of phone faces west: heading 270, not -90.
        val west = Vec3(-1.0, 0.0, 0.0)
        val a = Attitude.fromRotationMatrix(matrix(north, up, east), MountMode.PORTRAIT_ON_ROD, 0.0)
        assertEquals(270.0, a.headingDeg, 1e-3)
        assertEquals(-1.0, west.x, 0.0)
    }
}
