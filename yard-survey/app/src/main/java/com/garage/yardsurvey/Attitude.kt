package com.garage.yardsurvey

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** How the phone is fixed to the rod. Decides which device axis runs along the rod. */
enum class MountMode {
    /** Phone strapped to the side of the rod, long edge along the rod, screen facing the operator, top of phone up. */
    PORTRAIT_ON_ROD,
    /** Phone lying flat on top of the rod, screen facing the sky. */
    FLAT_ON_TOP;

    val label: String
        get() = when (this) {
            PORTRAIT_ON_ROD -> "Portrait, strapped to rod"
            FLAT_ON_TOP -> "Flat on top of rod (screen up)"
        }
}

/**
 * The phone's attitude, resolved into the quantities the survey needs.
 *
 * @property rodUp        unit vector from rod tip toward the phone, in the TRUE-north ENU frame
 * @property tiltDeg      angle between the rod and vertical (0 = plumb)
 * @property leanAzimuthDeg  true bearing that the TOP of the rod leans toward
 * @property headingDeg   true bearing the operator is facing (portrait: out the back of the phone;
 *                        flat: the phone's top edge)
 * @property bubbleX/Y    lean indicator in screen units (right / down positive), magnitude = sin(tilt)
 */
data class Attitude(
    val rodUp: Vec3,
    val tiltDeg: Double,
    val leanAzimuthDeg: Double,
    val headingDeg: Double,
    val bubbleX: Double,
    val bubbleY: Double,
) {
    companion object {
        /**
         * Build an attitude from a 3x3 row-major rotation matrix as produced by
         * `SensorManager.getRotationMatrixFromVector` (device -> world, world = X east, Y magnetic north, Z up).
         * [declinationDeg] rotates the result into a true-north frame.
         */
        fun fromRotationMatrix(r: FloatArray, mount: MountMode, declinationDeg: Double): Attitude {
            require(r.size >= 9) { "need a 3x3 rotation matrix" }
            val r0 = r[0].toDouble(); val r1 = r[1].toDouble(); val r2 = r[2].toDouble()
            val r3 = r[3].toDouble(); val r4 = r[4].toDouble(); val r5 = r[5].toDouble()
            val r6 = r[6].toDouble(); val r7 = r[7].toDouble(); val r8 = r[8].toDouble()

            // Columns of R are the device axes expressed in world coordinates.
            val devX = Vec3(r0, r3, r6)
            val devY = Vec3(r1, r4, r7)
            val devZ = Vec3(r2, r5, r8)
            // Gravity (world "down") expressed in device coordinates = -(third row of R).
            val gx = -r6; val gy = -r7; val gz = -r8

            val rodUpMag: Vec3
            val headingMag: Double
            val bubbleX: Double
            val bubbleY: Double
            when (mount) {
                MountMode.PORTRAIT_ON_ROD -> {
                    rodUpMag = devY
                    // Operator faces the direction the back of the phone points (device -Z).
                    headingMag = Math.toDegrees(atan2(-devZ.x, -devZ.y))
                    // Lean of the rod top: toward device +X (screen right) and device +Z (toward operator = screen down).
                    bubbleX = gx
                    bubbleY = gz
                }
                MountMode.FLAT_ON_TOP -> {
                    rodUpMag = devZ
                    headingMag = Math.toDegrees(atan2(devY.x, devY.y))
                    // Lean toward device +X = screen right; toward device +Y = screen up.
                    bubbleX = gx
                    bubbleY = -gy
                }
            }
            val rodUp = Geo.magneticToTrue(rodUpMag.normalized(), declinationDeg)
            val tilt = Math.toDegrees(acos(rodUp.z.coerceIn(-1.0, 1.0)))
            val lean = if (hypot(rodUp.x, rodUp.y) < 1e-6) 0.0 else Geo.normDeg(Math.toDegrees(atan2(rodUp.x, rodUp.y)))
            return Attitude(
                rodUp = rodUp,
                tiltDeg = tilt,
                leanAzimuthDeg = lean,
                headingDeg = Geo.normDeg(headingMag + declinationDeg),
                bubbleX = bubbleX,
                bubbleY = bubbleY,
            )
        }

        /** A perfectly plumb rod with unknown heading, used when no orientation sensor is available. */
        val PLUMB = Attitude(Vec3(0.0, 0.0, 1.0), 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}
