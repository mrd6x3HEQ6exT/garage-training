package com.garage.yardsurvey

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A saved ground point. Coordinates are the ROD TIP after rod-height (and, if enabled, tilt) correction. */
data class SurveyPoint(
    val id: Long,
    var name: String,
    var note: String,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double,            // ellipsoidal height of the ground point, metres
    val mslOffset: Double,      // NaN when the platform gave no geoid data
    val antLat: Double,
    val antLon: Double,
    val antAlt: Double,
    val samples: Int,
    val hSpread: Double,
    val vSpread: Double,
    val hAcc: Double,
    val vAcc: Double,           // NaN when unknown
    val rodHeightM: Double,
    val tiltDeg: Double,
    val leanAzimuthDeg: Double,
    val headingDeg: Double,
    val tiltCompensated: Boolean,
    val satsUsed: Int,
    val durationMs: Long,
) {
    val pos: LatLonAlt get() = LatLonAlt(lat, lon, alt)
    val altMsl: Double get() = if (mslOffset.isNaN()) Double.NaN else alt + mslOffset

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("note", note); put("timeMs", timeMs)
        put("lat", lat); put("lon", lon); put("alt", alt); putNum("mslOffset", mslOffset)
        put("antLat", antLat); put("antLon", antLon); put("antAlt", antAlt)
        put("samples", samples); put("hSpread", hSpread); put("vSpread", vSpread)
        put("hAcc", hAcc); putNum("vAcc", vAcc)
        put("rodHeightM", rodHeightM); put("tiltDeg", tiltDeg); put("leanAzimuthDeg", leanAzimuthDeg)
        put("headingDeg", headingDeg); put("tiltCompensated", tiltCompensated)
        put("satsUsed", satsUsed); put("durationMs", durationMs)
    }

    private fun JSONObject.putNum(key: String, v: Double) {
        if (!v.isNaN() && !v.isInfinite()) put(key, v)
    }

    companion object {
        fun fromJson(o: JSONObject): SurveyPoint = SurveyPoint(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            note = o.optString("note", ""),
            timeMs = o.getLong("timeMs"),
            lat = o.getDouble("lat"), lon = o.getDouble("lon"), alt = o.getDouble("alt"),
            mslOffset = o.optDouble("mslOffset", Double.NaN),
            antLat = o.optDouble("antLat", o.getDouble("lat")),
            antLon = o.optDouble("antLon", o.getDouble("lon")),
            antAlt = o.optDouble("antAlt", o.getDouble("alt")),
            samples = o.optInt("samples", 1),
            hSpread = o.optDouble("hSpread", 0.0),
            vSpread = o.optDouble("vSpread", 0.0),
            hAcc = o.optDouble("hAcc", Double.NaN),
            vAcc = o.optDouble("vAcc", Double.NaN),
            rodHeightM = o.optDouble("rodHeightM", 0.0),
            tiltDeg = o.optDouble("tiltDeg", 0.0),
            leanAzimuthDeg = o.optDouble("leanAzimuthDeg", 0.0),
            headingDeg = o.optDouble("headingDeg", 0.0),
            tiltCompensated = o.optBoolean("tiltCompensated", false),
            satsUsed = o.optInt("satsUsed", 0),
            durationMs = o.optLong("durationMs", 0L),
        )

        fun from(r: CaptureResult, id: Long, name: String, rodHeightM: Double, satsUsed: Int) = SurveyPoint(
            id = id, name = name, note = "", timeMs = System.currentTimeMillis(),
            lat = r.tip.lat, lon = r.tip.lon, alt = r.tip.alt, mslOffset = r.mslOffset,
            antLat = r.antenna.lat, antLon = r.antenna.lon, antAlt = r.antenna.alt,
            samples = r.samples, hSpread = r.hSpread, vSpread = r.vSpread,
            hAcc = r.meanHAcc, vAcc = r.meanVAcc, rodHeightM = rodHeightM,
            tiltDeg = r.meanTiltDeg, leanAzimuthDeg = r.meanLeanAzimuthDeg, headingDeg = r.meanHeadingDeg,
            tiltCompensated = r.tiltCompensated, satsUsed = satsUsed, durationMs = r.durationMs,
        )
    }
}

/** JSON-file-backed list of points. Single-threaded: only touched from the main thread. */
class PointStore(context: Context) {
    private val file = File(context.filesDir, "points.json")
    val points: MutableList<SurveyPoint> = ArrayList()
    private var nextId: Long = 1

    init { load() }

    private fun load() {
        points.clear()
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val arr = root.getJSONArray("points")
            for (i in 0 until arr.length()) points.add(SurveyPoint.fromJson(arr.getJSONObject(i)))
            nextId = root.optLong("nextId", (points.maxOfOrNull { it.id } ?: 0L) + 1)
        } catch (e: Exception) {
            // A corrupt file should not brick the app: keep what parsed, and a backup of the bad file.
            file.copyTo(File(file.parentFile, "points.corrupt.json"), overwrite = true)
        }
    }

    fun save() {
        val tmp = File(file.parentFile, "points.json.tmp")
        tmp.writeText(toJson().toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText()); tmp.delete()
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("app", "YardSurvey"); put("version", 1); put("nextId", nextId)
        put("points", JSONArray().also { a -> points.forEach { a.put(it.toJson()) } })
    }

    fun allocId(): Long = nextId++

    fun add(p: SurveyPoint) { points.add(p); save() }
    fun remove(id: Long) { points.removeAll { it.id == id }; save() }
    fun byId(id: Long): SurveyPoint? = points.firstOrNull { it.id == id }
    fun clear() { points.clear(); save() }

    /** Next free "P<n>" name. */
    fun nextName(): String {
        val used = points.mapNotNull { Regex("^P(\\d+)$").find(it.name)?.groupValues?.get(1)?.toIntOrNull() }.toSet()
        var n = 1
        while (n in used) n++
        return "P$n"
    }

    /** Merge points from a backup, skipping ids that already exist. Returns the number imported. */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        val arr = root.getJSONArray("points")
        var added = 0
        val ids = points.map { it.id }.toHashSet()
        for (i in 0 until arr.length()) {
            val p = SurveyPoint.fromJson(arr.getJSONObject(i))
            if (p.id in ids) continue
            points.add(p); ids.add(p.id); added++
            if (p.id >= nextId) nextId = p.id + 1
        }
        save()
        return added
    }
}

object Csv {
    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** CSV with both absolute coordinates and local ENU relative to [base] (or the first point if null). */
    fun build(points: List<SurveyPoint>, base: SurveyPoint?, imperial: Boolean): String {
        val origin = (base ?: points.firstOrNull())?.pos
        val u = Units.unitLabel(imperial)
        val sb = StringBuilder()
        sb.append("name,time,lat,lon,alt_ellipsoid_m,alt_msl_m,east_$u,north_$u,up_$u,dist_$u,hacc_m,vacc_m,hspread_m,vspread_m,samples,sats,rod_height_m,tilt_deg,lean_az_deg,heading_deg,tilt_compensated,note\n")
        for (p in points) {
            val enu = if (origin != null) Geo.toEnu(origin, p.pos) else Enu.ZERO
            sb.append(q(p.name)).append(',')
                .append(ts.format(Date(p.timeMs))).append(',')
                .append(f7(p.lat)).append(',').append(f7(p.lon)).append(',')
                .append(f3(p.alt)).append(',').append(f3(p.altMsl)).append(',')
                .append(f3(Units.toDisplay(enu.e, imperial))).append(',')
                .append(f3(Units.toDisplay(enu.n, imperial))).append(',')
                .append(f3(Units.toDisplay(enu.u, imperial))).append(',')
                .append(f3(Units.toDisplay(enu.horizontal, imperial))).append(',')
                .append(f2(p.hAcc)).append(',').append(f2(p.vAcc)).append(',')
                .append(f2(p.hSpread)).append(',').append(f2(p.vSpread)).append(',')
                .append(p.samples).append(',').append(p.satsUsed).append(',')
                .append(f3(p.rodHeightM)).append(',')
                .append(f1(p.tiltDeg)).append(',').append(f1(p.leanAzimuthDeg)).append(',').append(f1(p.headingDeg)).append(',')
                .append(if (p.tiltCompensated) "yes" else "no").append(',')
                .append(q(p.note)).append('\n')
        }
        return sb.toString()
    }

    private fun f1(v: Double) = if (v.isNaN()) "" else String.format(Locale.US, "%.1f", v)
    private fun f2(v: Double) = if (v.isNaN()) "" else String.format(Locale.US, "%.2f", v)
    private fun f3(v: Double) = if (v.isNaN()) "" else String.format(Locale.US, "%.3f", v)
    private fun f7(v: Double) = String.format(Locale.US, "%.7f", v)
    private fun q(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
}
