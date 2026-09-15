package com.garage.yardsurvey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Raw GPS provider updates (no fused/Wi-Fi/cell blending: for surveying we want the
 * GNSS solution and its honest accuracy figures) plus satellite counts.
 *
 * On Android 14+ each fix is also annotated with a mean-sea-level altitude via the
 * platform geoid model, on a background thread, before being delivered.
 */
class LocationTracker(
    private val context: Context,
    private val onFix: (Location) -> Unit,
    private val onSats: (used: Int, visible: Int) -> Unit,
) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var running = false

    /** Reflection-free holder so the class loads on API < 34. */
    private var mslConverter: Any? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            if (Build.VERSION.SDK_INT >= 34) {
                worker.execute {
                    try { addMsl(loc) } catch (_: Exception) { }
                    main.post { if (running) onFix(loc) }
                }
            } else {
                onFix(loc)
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) { }
        override fun onProviderEnabled(provider: String) { }
        override fun onProviderDisabled(provider: String) { }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            onSats(used, status.satelliteCount)
        }
    }

    private fun addMsl(loc: Location) {
        if (Build.VERSION.SDK_INT < 34) return
        val conv = (mslConverter as? android.location.altitude.AltitudeConverter)
            ?: android.location.altitude.AltitudeConverter().also { mslConverter = it }
        conv.addMslAltitudeToLocation(context, loc)
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun gpsEnabled(): Boolean = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)

    fun start() {
        if (running || !hasPermission()) return
        running = true
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            lm.registerGnssStatusCallback(gnssCallback, main)
        } catch (e: SecurityException) {
            running = false
        }
    }

    fun stop() {
        if (!running) return
        running = false
        lm.removeUpdates(listener)
        lm.unregisterGnssStatusCallback(gnssCallback)
    }
}
