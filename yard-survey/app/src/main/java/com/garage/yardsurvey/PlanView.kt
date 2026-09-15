package com.garage.yardsurvey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Plan view of the saved points in the local ENU frame (north up, base point at the origin).
 * Pinch to zoom, drag to pan, tap a point to select it.
 */
class PlanView(context: Context) : View(context) {
    /** Points to draw, with their ENU offsets from the origin, already computed by the caller. */
    class Item(val point: SurveyPoint, val enu: Enu, val isBase: Boolean)

    var items: List<Item> = emptyList()
        set(v) { field = v; if (needFit && width > 0) fit(); invalidate() }
    var live: Enu? = null
        set(v) { field = v; invalidate() }
    var liveAccM: Double = 0.0
    var imperial: Boolean = true
    var selectedId: Long = -1L
        set(v) { field = v; invalidate() }
    var onSelect: ((SurveyPoint?) -> Unit)? = null

    private var pxPerM = 20.0
    private var centerE = 0.0   // world coords at the view centre
    private var centerN = 0.0
    private var needFit = true

    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3E3DC.toInt(); strokeWidth = 1f }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8B8B0.toInt(); strokeWidth = 2f }
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ptEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.WHITE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.FG }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED }
    private val liveP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Ui.BLUE }
    private val liveFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x221565C0 }
    private val north = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Ui.FG }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val f = d.scaleFactor.toDouble()
            // zoom about the focus point
            val (fe, fn) = toWorld(d.focusX, d.focusY)
            pxPerM = (pxPerM * f).coerceIn(0.5, 5000.0)
            val (fe2, fn2) = toWorld(d.focusX, d.focusY)
            centerE += fe - fe2; centerN += fn - fn2
            invalidate()
            return true
        }
    })
    private var lastX = 0f; private var lastY = 0f
    private var downX = 0f; private var downY = 0f
    private var dragging = false

    fun fit() {
        needFit = false
        val all = ArrayList<Enu>()
        items.forEach { all.add(it.enu) }
        live?.let { all.add(it) }
        if (all.isEmpty()) { pxPerM = 20.0; centerE = 0.0; centerN = 0.0; invalidate(); return }
        var minE = Double.MAX_VALUE; var maxE = -Double.MAX_VALUE
        var minN = Double.MAX_VALUE; var maxN = -Double.MAX_VALUE
        for (p in all) { minE = min(minE, p.e); maxE = max(maxE, p.e); minN = min(minN, p.n); maxN = max(maxN, p.n) }
        centerE = (minE + maxE) / 2; centerN = (minN + maxN) / 2
        val spanE = max(maxE - minE, 2.0); val spanN = max(maxN - minN, 2.0)
        val pad = Ui.dp(context, 48f)
        val w = max(width - 2 * pad, 100); val h = max(height - 2 * pad, 100)
        pxPerM = min(w / spanE, h / spanN).coerceIn(0.5, 5000.0)
        invalidate()
    }

    fun requestFit() { needFit = true; if (width > 0) fit() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { if (needFit) fit() }

    private fun toScreen(e: Double, n: Double): Pair<Float, Float> =
        Pair((width / 2f + (e - centerE) * pxPerM).toFloat(), (height / 2f - (n - centerN) * pxPerM).toFloat())

    private fun toWorld(x: Float, y: Float): Pair<Double, Double> =
        Pair(centerE + (x - width / 2.0) / pxPerM, centerN - (y - height / 2.0) / pxPerM)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = ev.x; lastY = ev.y; downX = ev.x; downY = ev.y; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = ev.x - lastX; val dy = ev.y - lastY
                    if (dragging || hypot(ev.x - downX, ev.y - downY) > Ui.dp(context, 8f)) {
                        dragging = true
                        centerE -= dx / pxPerM; centerN += dy / pxPerM
                        invalidate()
                    }
                }
                lastX = ev.x; lastY = ev.y
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && !scaleDetector.isInProgress) tapAt(ev.x, ev.y)
            }
        }
        return true
    }

    private fun tapAt(x: Float, y: Float) {
        var best: Item? = null; var bestD = Ui.dp(context, 28f).toFloat()
        for (it in items) {
            val (sx, sy) = toScreen(it.enu.e, it.enu.n)
            val d = hypot(sx - x, sy - y)
            if (d < bestD) { bestD = d; best = it }
        }
        selectedId = best?.point?.id ?: -1L
        onSelect?.invoke(best?.point)
    }

    /** Grid spacing in DISPLAY units chosen so cells are at least ~56dp on screen. */
    private fun gridStepDisplay(): Double {
        val minPx = Ui.dp(context, 56f)
        val unitPerM = if (imperial) Units.FT_PER_M else 1.0
        val pxPerUnit = pxPerM / unitPerM
        val steps = doubleArrayOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0)
        for (s in steps) if (s * pxPerUnit >= minPx) return s
        return steps.last()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFFFAFAF5.toInt())
        val unitPerM = if (imperial) Units.FT_PER_M else 1.0
        val stepM = gridStepDisplay() / unitPerM

        // grid
        val (minE, maxN) = toWorld(0f, 0f)
        val (maxE, minN) = toWorld(width.toFloat(), height.toFloat())
        var e = Math.floor(minE / stepM) * stepM
        while (e <= maxE) {
            val (sx, _) = toScreen(e, 0.0)
            c.drawLine(sx, 0f, sx, height.toFloat(), if (abs(e) < 1e-9) axis else grid)
            e += stepM
        }
        var n = Math.floor(minN / stepM) * stepM
        while (n <= maxN) {
            val (_, sy) = toScreen(0.0, n)
            c.drawLine(0f, sy, width.toFloat(), sy, if (abs(n) < 1e-9) axis else grid)
            n += stepM
        }

        // live position + accuracy circle
        live?.let { l ->
            val (sx, sy) = toScreen(l.e, l.n)
            val r = (liveAccM * pxPerM).toFloat()
            if (r > 2f) { c.drawCircle(sx, sy, r, liveFill); c.drawCircle(sx, sy, r, liveP) }
            val k = Ui.dp(context, 12f).toFloat()
            c.drawLine(sx - k, sy, sx + k, sy, liveP)
            c.drawLine(sx, sy - k, sx, sy + k, liveP)
            c.drawCircle(sx, sy, k / 2, liveP)
        }

        // points
        label.textSize = Ui.dp(context, 13f).toFloat()
        small.textSize = Ui.dp(context, 11f).toFloat()
        val rr = Ui.dp(context, 7f).toFloat()
        for (it in items) {
            val (sx, sy) = toScreen(it.enu.e, it.enu.n)
            val sel = it.point.id == selectedId
            pt.color = if (sel) Ui.WARN else if (it.isBase) Ui.ACCENT else Ui.BAD
            if (it.isBase) c.drawRect(sx - rr, sy - rr, sx + rr, sy + rr, pt) else c.drawCircle(sx, sy, rr, pt)
            if (it.isBase) c.drawRect(sx - rr, sy - rr, sx + rr, sy + rr, ptEdge) else c.drawCircle(sx, sy, rr, ptEdge)
            c.drawText(it.point.name, sx + rr + 4, sy - rr, label)
            c.drawText(Units.fmtLen(it.enu.u, imperial, 2, signed = true), sx + rr + 4, sy + rr + small.textSize, small)
        }

        // scale bar
        val barM = stepM
        val barPx = (barM * pxPerM).toFloat()
        val bx = Ui.dp(context, 12f).toFloat(); val by = height - Ui.dp(context, 16f).toFloat()
        c.drawLine(bx, by, bx + barPx, by, axis)
        c.drawLine(bx, by - 6, bx, by + 6, axis); c.drawLine(bx + barPx, by - 6, bx + barPx, by + 6, axis)
        c.drawText(fmtStep(gridStepDisplay()) + " " + Units.unitLabel(imperial), bx, by - 8, small)

        // north arrow
        val nx = width - Ui.dp(context, 24f).toFloat(); val ny = Ui.dp(context, 34f).toFloat()
        val path = Path().apply {
            moveTo(nx, ny - 18f); lineTo(nx - 9f, ny + 10f); lineTo(nx, ny + 3f); lineTo(nx + 9f, ny + 10f); close()
        }
        c.drawPath(path, north)
        label.textAlign = Paint.Align.CENTER
        c.drawText("N", nx, ny + 28f, label)
        label.textAlign = Paint.Align.LEFT
    }

    private fun fmtStep(v: Double): String =
        if (v == Math.floor(v)) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
}
