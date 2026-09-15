package com.garage.yardsurvey

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saved points, newest first, with export / backup actions. */
class PointsScreen(private val a: MainActivity) : LinearLayout(a) {
    private val list = ListView(a)
    private val summary = Ui.text(a, "", 13f, Ui.MUTED)
    private val ts = SimpleDateFormat("MM-dd HH:mm", Locale.US)
    private var rows: List<Row> = emptyList()

    private class Row(val p: SurveyPoint, val enu: Enu?, val isBase: Boolean)

    init {
        orientation = VERTICAL
        setBackgroundColor(Ui.BG)
        val c = a
        val bar = HorizontalScrollView(c).apply {
            addView(Ui.row(c) {
                addView(Ui.button(c, "Save CSV") { a.exportCsv() })
                addView(Ui.button(c, "Share CSV") { a.shareCsv() })
                addView(Ui.button(c, "Backup JSON") { a.backupJson() })
                addView(Ui.button(c, "Import JSON") { a.importJson() })
                addView(Ui.button(c, "Delete all") { confirmClear() })
            })
        }
        addView(bar)
        summary.setPadding(Ui.dp(c, 12f), 0, Ui.dp(c, 12f), Ui.dp(c, 4f))
        addView(summary)
        list.divider = android.graphics.drawable.ColorDrawable(0x00000000)
        list.dividerHeight = Ui.dp(c, 6f)
        list.setOnItemClickListener { _, _, pos, _ -> showDetail(rows[pos]) }
        addView(list, Ui.fill())
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(i: Int) = rows[i]
        override fun getItemId(i: Int) = rows[i].p.id
        override fun getView(i: Int, convert: View?, parent: ViewGroup?): View {
            val c = a
            val imp = a.prefs.imperial
            val r = rows[i]
            val v = (convert as? LinearLayout) ?: Ui.card(c) {
                addView(Ui.text(c, "", 17f, Ui.FG, bold = true).apply { tag = "t" })
                addView(Ui.text(c, "", 13f, Ui.MUTED).apply { tag = "s" })
                addView(Ui.text(c, "", 13f, Ui.MUTED).apply { tag = "q" })
            }
            val title = v.findViewWithTag<TextView>("t")
            val sub = v.findViewWithTag<TextView>("s")
            val q = v.findViewWithTag<TextView>("q")
            val enu = r.enu
            title.text = (if (r.isBase) "■ " else "") + r.p.name + (if (r.isBase) "  (base)" else "") +
                (if (r.p.note.isNotEmpty()) "  —  " + r.p.note else "")
            sub.text = if (enu == null || r.isBase) "origin of the local grid"
                else String.format(Locale.US, "%s @ %03.0f° from base  ·  Δelev %s  ·  grade %+.1f%%",
                    Units.fmtLen(enu.horizontal, imp, 1), enu.bearingDeg, Units.fmtLen(enu.u, imp, 2, signed = true), enu.gradePercent)
            q.text = String.format(Locale.US, "%s  ·  H %s V %s %s  ·  %d fixes  ·  scatter %s  ·  tilt %.1f°%s",
                ts.format(Date(r.p.timeMs)), Units.fmtAcc(r.p.hAcc, imp),
                if (r.p.vAcc.isNaN()) "±?" else Units.fmtAcc(r.p.vAcc, imp), Units.unitLabel(imp),
                r.p.samples, Units.fmtAcc(r.p.hSpread, imp), r.p.tiltDeg,
                if (r.p.tiltCompensated) " (comp.)" else "")
            return v
        }
    }

    init { list.adapter = adapter }

    fun refresh() {
        val base = a.originPoint()
        val origin = base?.pos
        rows = a.store.points.asReversed().map { p ->
            Row(p, origin?.let { Geo.toEnu(it, p.pos) }, base != null && p.id == base.id)
        }
        summary.text = when (rows.size) {
            0 -> "No points yet. Capture some on the first tab."
            1 -> "1 point  ·  base: ${base?.name}"
            else -> "${rows.size} points  ·  base: ${base?.name}  ·  tap a point for details"
        }
        adapter.notifyDataSetChanged()
    }

    private fun showDetail(r: Row) {
        val p = r.p
        val imp = a.prefs.imperial
        val sb = StringBuilder()
        sb.append("Ground point (rod tip)\n")
        sb.append("  lat ${Units.fmtLatLon(p.lat)}\n  lon ${Units.fmtLatLon(p.lon)}\n")
        sb.append("  elev ${Units.fmtLen(p.alt, imp, 2)} (ellipsoid)")
        if (!p.mslOffset.isNaN()) sb.append("\n  elev ${Units.fmtLen(p.altMsl, imp, 2)} (MSL)")
        r.enu?.let { e ->
            sb.append("\n\nRelative to base\n")
            sb.append("  E ${Units.fmtLen(e.e, imp, 2, true)}  N ${Units.fmtLen(e.n, imp, 2, true)}  Up ${Units.fmtLen(e.u, imp, 2, true)}\n")
            sb.append(String.format(Locale.US, "  %s @ %03.0f°, grade %+.1f%%", Units.fmtLen(e.horizontal, imp, 2), e.bearingDeg, e.gradePercent))
        }
        sb.append("\n\nQuality\n")
        sb.append("  ${p.samples} fixes over ${p.durationMs / 1000}s, ${p.satsUsed} sats\n")
        sb.append("  reported acc H ${Units.fmtAcc(p.hAcc, imp)}  V ${if (p.vAcc.isNaN()) "±?" else Units.fmtAcc(p.vAcc, imp)} ${Units.unitLabel(imp)}\n")
        sb.append("  scatter H ${Units.fmtAcc(p.hSpread, imp)}  V ${Units.fmtAcc(p.vSpread, imp)} ${Units.unitLabel(imp)}\n")
        sb.append(String.format(Locale.US, "  rod %s, tilt %.1f° toward %s, heading %03.0f°\n  tilt compensation %s",
            Units.fmtLen(p.rodHeightM, imp, 2), p.tiltDeg, Units.cardinal(p.leanAzimuthDeg), p.headingDeg,
            if (p.tiltCompensated) "applied" else "off (assumed plumb)"))
        AlertDialog.Builder(a)
            .setTitle(p.name + if (r.isBase) " (base)" else "")
            .setMessage(sb.toString())
            .setPositiveButton("Edit") { _, _ -> editPoint(p) }
            .setNeutralButton(if (r.isBase) "Unset base" else "Set as base") { _, _ ->
                a.prefs.baseId = if (r.isBase) -1L else p.id
                a.refreshAll()
            }
            .setNegativeButton("Delete") { _, _ ->
                AlertDialog.Builder(a).setTitle("Delete ${p.name}?")
                    .setPositiveButton("Delete") { _, _ -> a.store.remove(p.id); if (a.prefs.baseId == p.id) a.prefs.baseId = -1L; a.refreshAll() }
                    .setNegativeButton("Keep", null).show()
            }
            .show()
    }

    private fun editPoint(p: SurveyPoint) {
        val c = a
        val name = Ui.edit(c, "Name", p.name)
        val note = Ui.edit(c, "Note", p.note)
        val box = Ui.column(c, Ui.dp(c, 16f)) { addView(name); addView(note) }
        AlertDialog.Builder(a).setTitle("Edit point").setView(box)
            .setPositiveButton("Save") { _, _ ->
                p.name = name.text.toString().trim().ifEmpty { p.name }
                p.note = note.text.toString().trim()
                a.store.save(); a.refreshAll()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmClear() {
        if (a.store.points.isEmpty()) return
        AlertDialog.Builder(a).setTitle("Delete all ${a.store.points.size} points?")
            .setMessage("Back up to JSON first if you want to keep them.")
            .setPositiveButton("Delete all") { _, _ -> a.store.clear(); a.prefs.baseId = -1L; a.refreshAll() }
            .setNegativeButton("Cancel", null).show()
    }
}
