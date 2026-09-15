package com.garage.yardsurvey

import java.util.Locale

/** Length formatting. Everything is stored in metres; only display changes. */
object Units {
    const val FT_PER_M = 3.280839895013123

    fun fmtLen(m: Double, imperial: Boolean, decimals: Int = 2, signed: Boolean = false): String {
        val v = if (imperial) m * FT_PER_M else m
        val unit = if (imperial) "ft" else "m"
        val sign = if (signed && v > 0) "+" else ""
        return String.format(Locale.US, "%s%.${decimals}f %s", sign, v, unit)
    }

    /** Short "±1.2" style accuracy string without unit. */
    fun fmtAcc(m: Double, imperial: Boolean): String {
        val v = if (imperial) m * FT_PER_M else m
        return String.format(Locale.US, "±%.1f", v)
    }

    fun unitLabel(imperial: Boolean) = if (imperial) "ft" else "m"

    fun toDisplay(m: Double, imperial: Boolean) = if (imperial) m * FT_PER_M else m
    fun fromDisplay(v: Double, imperial: Boolean) = if (imperial) v / FT_PER_M else v

    fun fmtDeg(d: Double, decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f°", d)

    fun fmtLatLon(v: Double): String = String.format(Locale.US, "%.7f", v)

    fun cardinal(bearingDeg: Double): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val i = ((Geo.normDeg(bearingDeg) + 22.5) / 45.0).toInt() % 8
        return names[i]
    }
}
