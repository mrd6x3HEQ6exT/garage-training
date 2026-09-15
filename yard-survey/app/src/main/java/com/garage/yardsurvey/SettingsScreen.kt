package com.garage.yardsurvey

import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class SettingsScreen(private val a: MainActivity) : ScrollView(a) {
    private val units: RadioGroup
    private val rod: EditText
    private val rodUnit: TextView
    private val avg: EditText
    private val tilt: EditText
    private val comp: Switch
    private val mount: RadioGroup
    private val baseBtn: TextView
    private val info: TextView
    private var populating = false

    init {
        val c = a
        setBackgroundColor(Ui.BG)
        units = RadioGroup(c).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(RadioButton(c).apply { text = "Feet"; id = 1 })
            addView(RadioButton(c).apply { text = "Metres"; id = 2 })
            setOnCheckedChangeListener { _, id -> if (!populating) { a.prefs.imperial = (id == 1); rod.clearFocus(); refresh(); a.refreshAll() } }
        }
        rod = Ui.edit(c, "Rod height", numeric = true)
        rodUnit = Ui.text(c, "ft", 15f, Ui.MUTED)
        avg = Ui.edit(c, "Seconds", numeric = true)
        tilt = Ui.edit(c, "Degrees", numeric = true)
        comp = Switch(c).apply {
            text = "Tilt compensation: move the point from the phone to the rod tip using the phone's attitude"
            setOnCheckedChangeListener { _, on -> if (!populating) a.prefs.tiltCompensate = on }
        }
        mount = RadioGroup(c).apply {
            MountMode.values().forEachIndexed { i, m -> addView(RadioButton(c).apply { text = m.label; id = 10 + i }) }
            setOnCheckedChangeListener { _, id -> if (!populating) { a.prefs.mount = MountMode.values()[id - 10]; a.ori.mount = a.prefs.mount } }
        }
        baseBtn = Ui.button(c, "") { pickBase() }
        info = Ui.text(c, "", 13f, Ui.MUTED)

        rod.addTextChangedListener(watcher { v -> a.prefs.rodHeightM = Units.fromDisplay(v, a.prefs.imperial).coerceIn(0.0, 10.0) })
        avg.addTextChangedListener(watcher { v -> a.prefs.avgSeconds = v.toInt().coerceIn(1, 600) })
        tilt.addTextChangedListener(watcher { v -> a.prefs.maxTiltDeg = v.coerceIn(0.2, 45.0) })

        val root = Ui.column(c) {
            addView(Ui.card(c) {
                addView(Ui.text(c, "Units", 13f, Ui.MUTED, bold = true))
                addView(units)
            })
            addView(Ui.card(c) {
                addView(Ui.text(c, "Rod height (tip to phone)", 13f, Ui.MUTED, bold = true))
                addView(Ui.row(c) { addView(rod, Ui.weight(1f)); addView(rodUnit) })
                addView(Ui.text(c, "Measure from the rod tip to roughly the middle of the phone. Every capture subtracts this to get the ground elevation.", 13f, Ui.MUTED))
            })
            addView(Ui.card(c) {
                addView(Ui.text(c, "Averaging window (seconds)", 13f, Ui.MUTED, bold = true))
                addView(avg)
                addView(Ui.text(c, "Fixes arrive about once a second. Longer windows average out more noise; 15–30 s is a good trade-off.", 13f, Ui.MUTED))
            })
            addView(Ui.card(c) {
                addView(Ui.text(c, "Plumb warning (degrees off vertical)", 13f, Ui.MUTED, bold = true))
                addView(tilt)
                addView(comp)
            })
            addView(Ui.card(c) {
                addView(Ui.text(c, "How the phone is mounted", 13f, Ui.MUTED, bold = true))
                addView(mount)
            })
            addView(Ui.card(c) {
                addView(Ui.text(c, "Base point (origin of the local grid)", 13f, Ui.MUTED, bold = true))
                addView(baseBtn)
                addView(Ui.text(c, "Pick a point to use as the origin for east/north/elevation differences. Relative numbers between points are far more accurate than the absolute coordinates.", 13f, Ui.MUTED))
            })
            addView(Ui.card(c) {
                addView(Ui.text(c, "Status", 13f, Ui.MUTED, bold = true))
                addView(info)
            })
        }
        addView(root)
    }

    private fun watcher(apply: (Double) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (populating) return
            val v = s?.toString()?.trim()?.toDoubleOrNull() ?: return
            apply(v)
        }
    }

    private fun pickBase() {
        val pts = a.store.points
        val names = arrayOf("First captured point (default)") + pts.map { it.name }.toTypedArray()
        val cur = if (a.prefs.baseId == -1L) 0 else pts.indexOfFirst { it.id == a.prefs.baseId } + 1
        AlertDialog.Builder(a).setTitle("Base point")
            .setSingleChoiceItems(names, cur.coerceAtLeast(0)) { d, i ->
                a.prefs.baseId = if (i == 0) -1L else pts[i - 1].id
                d.dismiss(); a.refreshAll()
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun refresh() {
        populating = true
        val p = a.prefs
        units.check(if (p.imperial) 1 else 2)
        rodUnit.text = Units.unitLabel(p.imperial)
        if (!rod.hasFocus()) rod.setText(String.format(Locale.US, "%.2f", Units.toDisplay(p.rodHeightM, p.imperial)))
        if (!avg.hasFocus()) avg.setText(p.avgSeconds.toString())
        if (!tilt.hasFocus()) tilt.setText(String.format(Locale.US, "%.1f", p.maxTiltDeg))
        comp.isChecked = p.tiltCompensate
        mount.check(10 + p.mount.ordinal)
        baseBtn.text = "Base: " + (a.originPoint()?.name ?: "none yet") + (if (p.baseId == -1L) " (first point)" else "")
        val sb = StringBuilder()
        sb.append("Orientation sensor: ").append(if (a.ori.available) "rotation vector OK" else "NOT AVAILABLE").append('\n')
        sb.append(String.format(Locale.US, "Magnetic declination here: %+.1f° (headings shown as true north)\n", a.declination))
        sb.append("Altitudes: GPS gives height above the WGS84 ellipsoid. On Android 14+ an MSL (sea-level) height is added from the platform geoid model. Grading only needs differences, which are the same in either.\n")
        sb.append("Expect a few metres of absolute error and roughly 1 m (3 ft) of elevation scatter between averaged points on a phone; use long averaging windows and re-shoot the base occasionally to see the drift.")
        info.text = sb.toString()
        populating = false
    }
}
