package com.garage.yardsurvey

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    lateinit var prefs: Prefs
    lateinit var store: PointStore
    lateinit var loc: LocationTracker
    lateinit var ori: OrientationTracker

    var lastLoc: Location? = null
    var lastFixWallMs: Long = 0L
    var satsUsed = 0
    var satsVisible = 0
    var attitude: Attitude? = null
    var declination = 0.0
    var session: CaptureSession? = null
    private var sessionName = ""

    private lateinit var content: FrameLayout
    private lateinit var tabs: List<Button>
    private lateinit var capture: CaptureScreen
    private lateinit var points: PointsScreen
    private lateinit var map: MapScreen
    private lateinit var settings: SettingsScreen
    private var current = 0

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            checkSession()
            refreshLive()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        store = PointStore(this)
        loc = LocationTracker(this, ::onFix) { used, vis -> satsUsed = used; satsVisible = vis }
        ori = OrientationTracker(this) { att -> attitude = att; capture.onAttitude(att, ori.accuracy > SensorManager.SENSOR_STATUS_UNRELIABLE) }
        ori.mount = prefs.mount

        capture = CaptureScreen(this)
        points = PointsScreen(this)
        map = MapScreen(this)
        settings = SettingsScreen(this)

        content = FrameLayout(this)
        val names = listOf("Capture", "Points", "Map", "Settings")
        tabs = names.mapIndexed { i, n -> Ui.button(this, n) { showScreen(i) } }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.CARD)
            tabs.forEach { addView(it, Ui.weight(1f)) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            addView(content, Ui.fill())
            addView(Ui.divider(this@MainActivity))
            addView(bar)
        }
        setContentView(root)
        showScreen(0)

        if (!loc.hasPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) loc.start()
        else if (requestCode == 1) toast("Without location permission there is nothing to survey.")
    }

    override fun onResume() {
        super.onResume()
        loc.start()
        ori.start()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        ori.stop()
        loc.stop()
        if (session != null) { cancelCapture(); toast("Capture cancelled (app went to background)") }
    }

    fun showScreen(i: Int) {
        current = i
        content.removeAllViews()
        content.addView(when (i) { 0 -> capture; 1 -> points; 2 -> map; else -> settings },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        tabs.forEachIndexed { k, b -> b.setTextColor(if (k == i) Ui.ACCENT else Ui.MUTED); b.typeface = if (k == i) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT }
        refreshCurrent()
    }

    private fun refreshCurrent() {
        when (current) { 0 -> capture.refresh(); 1 -> points.refresh(); 2 -> map.refresh(); else -> settings.refresh() }
    }

    /** Only the screens that show live sensor data need the frequent redraw. */
    private fun refreshLive() {
        when (current) { 0 -> capture.refresh(); 2 -> map.refresh() }
    }

    /** Re-read everything (after edits to points/settings). */
    fun refreshAll() {
        ori.mount = prefs.mount
        refreshCurrent()
    }

    // ---------------------------------------------------------------- live state

    private fun onFix(l: Location) {
        lastLoc = l
        lastFixWallMs = System.currentTimeMillis()
        declination = GeomagneticField(l.latitude.toFloat(), l.longitude.toFloat(), l.altitude.toFloat(), l.time).declination.toDouble()
        ori.declinationDeg = declination
        session?.add(FixSample(
            timeMs = l.time,
            pos = LatLonAlt(l.latitude, l.longitude, l.altitude),
            hAcc = l.accuracy.toDouble(),
            vAcc = if (l.hasVerticalAccuracy()) l.verticalAccuracyMeters.toDouble() else Double.NaN,
            mslOffset = if (android.os.Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude()) l.mslAltitudeMeters - l.altitude else Double.NaN,
            attitude = if (ori.available) attitude else null,
        ))
        refreshLive()
    }

    /** The point the rod tip is on right now: antenna position corrected for rod height and (if enabled) tilt. */
    fun liveGround(): LatLonAlt? {
        val l = lastLoc ?: return null
        if (System.currentTimeMillis() - lastFixWallMs > 10_000) return null
        val ant = LatLonAlt(l.latitude, l.longitude, l.altitude)
        val att = attitude
        val off = if (prefs.tiltCompensate && att != null) Geo.tipOffset(att.rodUp, prefs.rodHeightM) else Enu(0.0, 0.0, -prefs.rodHeightM)
        return Geo.fromEnu(ant, off)
    }

    /** Base point if set (and still exists), else the first captured point. */
    fun originPoint(): SurveyPoint? = store.byId(prefs.baseId) ?: store.points.firstOrNull()

    fun liveEnu(): Enu? {
        val o = originPoint() ?: return null
        val g = liveGround() ?: return null
        return Geo.toEnu(o.pos, g)
    }

    // ---------------------------------------------------------------- capture

    fun startCapture(name: String) {
        if (session != null) return
        if (!loc.hasPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1); return
        }
        if (!loc.gpsEnabled()) { toast("Turn on GPS / Location in system settings first."); return }
        if (liveGround() == null) { toast("No GPS fix yet. Get outside with a clear view of the sky."); return }
        val att = attitude
        if (att != null && att.tiltDeg > prefs.maxTiltDeg) {
            val msg = String.format(Locale.US,
                "The rod is %.1f° off plumb, top leaning %s. Tilt compensation is %s.\n\nCapture anyway?",
                att.tiltDeg, Units.cardinal(att.leanAzimuthDeg), if (prefs.tiltCompensate) "ON (the point will be moved to the tip)" else "OFF (the point will be wrong by about " + Units.fmtLen(prefs.rodHeightM * Math.sin(Math.toRadians(att.tiltDeg)), prefs.imperial, 2) + ")")
            AlertDialog.Builder(this).setTitle("Rod not level").setMessage(msg)
                .setPositiveButton("Capture anyway") { _, _ -> beginSession(name) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        beginSession(name)
    }

    private fun beginSession(name: String) {
        sessionName = name
        session = CaptureSession(prefs.rodHeightM, prefs.tiltCompensate && ori.available)
        refreshCurrent()
    }

    fun cancelCapture() {
        session = null
        refreshCurrent()
    }

    private fun checkSession() {
        val s = session ?: return
        val elapsed = System.currentTimeMillis() - s.startMs
        val want = prefs.avgSeconds * 1000L
        if (elapsed >= want && s.count >= 1) finishSession(s)
        else if (elapsed >= want + 10_000L) { session = null; toast("No GPS fixes arrived during the window. Capture cancelled."); refreshCurrent() }
    }

    private fun finishSession(s: CaptureSession) {
        session = null
        val r = s.reduce()
        val p = SurveyPoint.from(r, store.allocId(), sessionName, prefs.rodHeightM, satsUsed)
        store.add(p)
        capture.clearName()
        try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 250) } catch (_: Exception) { }
        val imp = prefs.imperial
        val rel = originPoint()?.let { o -> if (o.id != p.id) Geo.toEnu(o.pos, p.pos) else null }
        val relTxt = if (rel == null) "" else String.format(Locale.US, "\n%s @ %03.0f° from %s, Δelev %s",
            Units.fmtLen(rel.horizontal, imp, 1), rel.bearingDeg, originPoint()!!.name, Units.fmtLen(rel.u, imp, 2, true))
        toast("Saved ${p.name}: ${r.samples} fixes, scatter ${Units.fmtAcc(r.hSpread, imp)} ${Units.unitLabel(imp)}$relTxt")
        refreshCurrent()
    }

    fun editRodHeight() {
        val imp = prefs.imperial
        val e = Ui.edit(this, "Rod height (${Units.unitLabel(imp)})", String.format(Locale.US, "%.2f", Units.toDisplay(prefs.rodHeightM, imp)), numeric = true)
        val box = Ui.column(this, Ui.dp(this, 16f)) { addView(e) }
        AlertDialog.Builder(this).setTitle("Rod height, tip to phone").setView(box)
            .setPositiveButton("Save") { _, _ ->
                e.text.toString().trim().toDoubleOrNull()?.let { prefs.rodHeightM = Units.fromDisplay(it, imp).coerceIn(0.0, 10.0) }
                refreshCurrent()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ---------------------------------------------------------------- export / import

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    fun exportCsv() {
        if (store.points.isEmpty()) { toast("Nothing to export yet."); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"; putExtra(Intent.EXTRA_TITLE, "yard-survey-${stamp()}.csv")
        }
        startActivityForResult(i, RC_CSV)
    }

    fun shareCsv() {
        if (store.points.isEmpty()) { toast("Nothing to share yet."); return }
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Yard survey ${stamp()}")
            putExtra(Intent.EXTRA_TEXT, Csv.build(store.points, originPoint(), prefs.imperial))
        }
        startActivity(Intent.createChooser(i, "Share CSV"))
    }

    fun backupJson() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "yard-survey-${stamp()}.json")
        }
        startActivityForResult(i, RC_JSON)
    }

    fun importJson() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        startActivityForResult(i, RC_IMPORT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try {
            when (requestCode) {
                RC_CSV -> { write(uri, Csv.build(store.points, originPoint(), prefs.imperial)); toast("CSV saved") }
                RC_JSON -> { write(uri, store.toJson().toString(2)); toast("Backup saved") }
                RC_IMPORT -> {
                    val text = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    val n = store.importJson(text)
                    toast("Imported $n new point(s)")
                    refreshAll()
                }
            }
        } catch (e: Exception) {
            toast("Failed: ${e.message}")
        }
    }

    private fun write(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).apply { setGravity(Gravity.CENTER, 0, 0) }.show()
    }

    companion object {
        private const val RC_CSV = 11
        private const val RC_JSON = 12
        private const val RC_IMPORT = 13
    }
}
