package com.garage.yardsurvey

import android.hardware.SensorManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** The main working screen: live GNSS readout, bubble level, and the capture button. */
class CaptureScreen(private val a: MainActivity) : ScrollView(a) {
    private val gpsLine: TextView
    private val coordLine: TextView
    private val elevLine: TextView
    private val headingLine: TextView
    private val relLine: TextView
    private val level: LevelView
    private val rodLine: TextView
    private val nameEdit: EditText
    private val captureBtn: Button
    private val cancelBtn: Button
    private val hintLine: TextView

    init {
        isFillViewport = true
        val c = a
        val root = Ui.column(c) {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, Ui.dp(c, 12f))
        }

        gpsLine = Ui.text(c, "GPS: starting…", 14f, Ui.MUTED)
        coordLine = Ui.text(c, "—", 18f, Ui.FG, mono = true)
        elevLine = Ui.text(c, "", 16f, Ui.FG, mono = true)
        headingLine = Ui.text(c, "", 15f, Ui.FG)
        relLine = Ui.text(c, "", 15f, Ui.BLUE)
        root.addView(Ui.card(c) {
            addView(gpsLine); addView(coordLine); addView(elevLine); addView(headingLine); addView(relLine)
        })

        level = LevelView(c)
        rodLine = Ui.text(c, "", 15f, Ui.FG)
        rodLine.gravity = Gravity.CENTER
        rodLine.setPadding(0, Ui.dp(c, 6f), 0, 0)
        rodLine.setOnClickListener { a.editRodHeight() }
        root.addView(Ui.card(c) {
            val lp = LinearLayout.LayoutParams(Ui.dp(c, 220f), Ui.dp(c, 220f))
            lp.gravity = Gravity.CENTER_HORIZONTAL
            addView(level, lp)
            addView(rodLine)
        })

        nameEdit = Ui.edit(c, "Point name")
        captureBtn = Ui.button(c, "CAPTURE") { onCapturePressed() }
        captureBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
        cancelBtn = Ui.button(c, "Cancel") { a.cancelCapture() }
        cancelBtn.visibility = GONE
        hintLine = Ui.text(c, "", 13f, Ui.MUTED)
        root.addView(Ui.card(c) {
            addView(Ui.row(c) {
                addView(Ui.text(c, "Name ", 15f, Ui.MUTED))
                addView(nameEdit, Ui.weight(1f))
            })
            addView(captureBtn, Ui.match().apply { height = Ui.dp(c, 64f) })
            addView(cancelBtn, Ui.match())
            addView(hintLine)
        })
        addView(root)
        setBackgroundColor(Ui.BG)
    }

    private fun onCapturePressed() {
        if (a.session != null) return
        var name = nameEdit.text.toString().trim()
        if (name.isEmpty()) name = a.store.nextName()
        a.startCapture(name)
    }

    /** Called ~25 Hz from the orientation sensor. Only the level widget is touched here. */
    fun onAttitude(att: Attitude?, sensorOk: Boolean) {
        level.attitude = att
        level.sensorOk = sensorOk
    }

    /** Called on every fix and on the 2 Hz UI tick. */
    fun refresh() {
        val imp = a.prefs.imperial
        level.maxTiltDeg = a.prefs.maxTiltDeg
        rodLine.text = "Rod height ${Units.fmtLen(a.prefs.rodHeightM, imp)}  ·  tap to change"

        if (nameEdit.text.isEmpty() && !nameEdit.hasFocus()) nameEdit.hint = a.store.nextName()

        val loc = a.lastLoc
        val age = if (loc == null) Long.MAX_VALUE else (System.currentTimeMillis() - a.lastFixWallMs) / 1000
        when {
            !a.loc.hasPermission() -> { gpsLine.text = "Location permission needed"; gpsLine.setTextColor(Ui.BAD) }
            !a.loc.gpsEnabled() -> { gpsLine.text = "GPS is turned off in system settings"; gpsLine.setTextColor(Ui.BAD) }
            loc == null || age > 10 -> {
                gpsLine.text = "GPS: waiting for fix…  ${a.satsUsed}/${a.satsVisible} sats"
                gpsLine.setTextColor(Ui.WARN)
            }
            else -> {
                val v = if (loc.hasVerticalAccuracy()) Units.fmtAcc(loc.verticalAccuracyMeters.toDouble(), imp) else "±?"
                gpsLine.text = "GPS fix  ${a.satsUsed}/${a.satsVisible} sats  ·  H ${Units.fmtAcc(loc.accuracy.toDouble(), imp)}  V $v ${Units.unitLabel(imp)}  ·  ${age}s"
                gpsLine.setTextColor(if (loc.accuracy <= 5f) Ui.GOOD else Ui.WARN)
            }
        }

        val ground = a.liveGround()
        if (loc != null && ground != null) {
            coordLine.text = "${Units.fmtLatLon(ground.lat)}  ${Units.fmtLatLon(ground.lon)}"
            val msl = if (android.os.Build.VERSION.SDK_INT >= 34 && loc.hasMslAltitude())
                "  ·  MSL ${Units.fmtLen(ground.alt + (loc.mslAltitudeMeters - loc.altitude), imp, 1)}" else ""
            elevLine.text = "Ground elev ${Units.fmtLen(ground.alt, imp, 1)} (ellipsoid)$msl"
        } else {
            coordLine.text = "—"
            elevLine.text = ""
        }

        val att = a.attitude
        val compassBad = a.ori.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
        headingLine.text = when {
            !a.ori.available -> "No orientation sensor: tilt compensation unavailable"
            att == null -> "Heading —"
            else -> String.format(Locale.US, "Heading %03.0f° %s (true, decl %+.1f°)%s",
                att.headingDeg, Units.cardinal(att.headingDeg), a.declination,
                if (compassBad) "  ⚠ calibrate compass (figure-8)" else "")
        }
        headingLine.setTextColor(if (compassBad) Ui.WARN else Ui.FG)

        val origin = a.originPoint()
        val rel = a.liveEnu()
        relLine.text = if (origin == null) "No base point yet: the first capture becomes the origin."
            else if (rel == null) "From ${origin.name}: —"
            else String.format(Locale.US, "From %s: %s @ %03.0f°  ·  Δelev %s  ·  grade %+.1f%%",
                origin.name, Units.fmtLen(rel.horizontal, imp, 1), rel.bearingDeg,
                Units.fmtLen(rel.u, imp, 2, signed = true), rel.gradePercent)

        val s = a.session
        if (s == null) {
            captureBtn.text = "CAPTURE  (avg ${a.prefs.avgSeconds}s)"
            captureBtn.isEnabled = true
            cancelBtn.visibility = GONE
            nameEdit.isEnabled = true
            hintLine.text = if (att != null && att.tiltDeg > a.prefs.maxTiltDeg)
                "Rod is off plumb. Level it, or capture and let tilt compensation move the point to the tip."
                else "Hold the rod tip on the spot, keep it still for the whole averaging window."
        } else {
            val elapsed = (System.currentTimeMillis() - s.startMs) / 1000
            val spread = if (s.count >= 2) "  ·  scatter ${Units.fmtAcc(s.runningSpread(), imp)}" else ""
            captureBtn.text = "Averaging ${elapsed}/${a.prefs.avgSeconds}s  ·  ${s.count} fixes$spread"
            captureBtn.isEnabled = false
            cancelBtn.visibility = VISIBLE
            nameEdit.isEnabled = false
            hintLine.text = "Keep still…"
        }
    }

    fun clearName() { nameEdit.setText(""); nameEdit.hint = a.store.nextName() }
}
