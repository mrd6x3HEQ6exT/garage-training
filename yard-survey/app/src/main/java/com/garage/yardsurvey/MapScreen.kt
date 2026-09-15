package com.garage.yardsurvey

import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Plan view of the survey with a live position marker. */
class MapScreen(private val a: MainActivity) : LinearLayout(a) {
    private val plan = PlanView(a)
    private val caption: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(Ui.BG)
        val c = a
        caption = Ui.text(c, "", 13f, Ui.MUTED)
        addView(Ui.row(c) {
            addView(Ui.button(c, "Fit") { plan.requestFit() })
            addView(caption, Ui.weight(1f))
        })
        addView(plan, Ui.fill())
        plan.onSelect = { p -> showCaption(p) }
    }

    private fun showCaption(p: SurveyPoint?) {
        val imp = a.prefs.imperial
        val origin = a.originPoint()?.pos
        caption.text = when {
            p == null -> defaultCaption()
            origin == null -> p.name
            else -> {
                val e = Geo.toEnu(origin, p.pos)
                String.format(Locale.US, "%s: %s @ %03.0f°, Δelev %s, grade %+.1f%%",
                    p.name, Units.fmtLen(e.horizontal, imp, 1), e.bearingDeg, Units.fmtLen(e.u, imp, 2, true), e.gradePercent)
            }
        }
    }

    private fun defaultCaption(): String {
        val origin = a.originPoint() ?: return "Grid origin: first captured point. Pinch to zoom, drag to pan, tap a point."
        return "Origin: ${origin.name}. Blue cross = you (rod tip). Labels show Δelev."
    }

    fun refresh() {
        val base = a.originPoint()
        val origin = base?.pos
        plan.imperial = a.prefs.imperial
        plan.items = if (origin == null) emptyList() else a.store.points.map { p ->
            PlanView.Item(p, Geo.toEnu(origin, p.pos), p.id == base.id)
        }
        plan.live = a.liveEnu()
        plan.liveAccM = a.lastLoc?.accuracy?.toDouble() ?: 0.0
        if (plan.selectedId == -1L || a.store.byId(plan.selectedId) == null) {
            plan.selectedId = -1L
            caption.text = defaultCaption()
        }
    }
}
