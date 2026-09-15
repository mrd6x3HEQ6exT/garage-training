package com.garage.yardsurvey

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Feeds the rotation-vector sensor (gyro + accelerometer + magnetometer fusion) into [Attitude].
 * The magnetometer only affects headings; tilt comes from gravity and is immune to a steel rod.
 */
class OrientationTracker(
    context: Context,
    private val onAttitude: (Attitude) -> Unit,
) {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrix = FloatArray(9)
    private var lastEmitNs = 0L

    var mount: MountMode = MountMode.PORTRAIT_ON_ROD
    /** Magnetic declination at the current position, degrees east-positive. Updated by the location side. */
    var declinationDeg: Double = 0.0
    /** SensorManager.SENSOR_STATUS_* for the magnetometer part; LOW/UNRELIABLE means "wave the phone in a figure 8". */
    var accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        private set
    val available: Boolean get() = rotSensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Throttle to ~25 Hz; the UI does not need more.
            if (e.timestamp - lastEmitNs < 40_000_000L) return
            lastEmitNs = e.timestamp
            SensorManager.getRotationMatrixFromVector(matrix, e.values)
            onAttitude(Attitude.fromRotationMatrix(matrix, mount, declinationDeg))
        }
        override fun onAccuracyChanged(sensor: Sensor?, acc: Int) { accuracy = acc }
    }

    fun start() {
        rotSensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() { sm.unregisterListener(listener) }
}
