package com.garage.yardsurvey

import android.content.Context
import android.content.SharedPreferences

/** User settings. Lengths are stored in metres regardless of the display unit. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("yardsurvey", Context.MODE_PRIVATE)

    var rodHeightM: Double
        get() = sp.getFloat("rodHeightM", 1.5f).toDouble()
        set(v) = sp.edit().putFloat("rodHeightM", v.toFloat()).apply()

    var avgSeconds: Int
        get() = sp.getInt("avgSeconds", 15)
        set(v) = sp.edit().putInt("avgSeconds", v).apply()

    var maxTiltDeg: Double
        get() = sp.getFloat("maxTiltDeg", 3.0f).toDouble()
        set(v) = sp.edit().putFloat("maxTiltDeg", v.toFloat()).apply()

    var tiltCompensate: Boolean
        get() = sp.getBoolean("tiltCompensate", true)
        set(v) = sp.edit().putBoolean("tiltCompensate", v).apply()

    var imperial: Boolean
        get() = sp.getBoolean("imperial", true)
        set(v) = sp.edit().putBoolean("imperial", v).apply()

    var mount: MountMode
        get() = try { MountMode.valueOf(sp.getString("mount", MountMode.PORTRAIT_ON_ROD.name)!!) } catch (e: Exception) { MountMode.PORTRAIT_ON_ROD }
        set(v) = sp.edit().putString("mount", v.name).apply()

    /** Id of the point used as the local origin, or -1 for "first point". */
    var baseId: Long
        get() = sp.getLong("baseId", -1L)
        set(v) = sp.edit().putLong("baseId", v).apply()
}
