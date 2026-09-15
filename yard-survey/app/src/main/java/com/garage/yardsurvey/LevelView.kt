package com.garage.yardsurvey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular bubble level. The dot moves in the direction the TOP of the rod leans; rings mark
 * the warning tilt and twice the warning tilt. Green inside, amber between, red beyond.
 */
class LevelView(context: Context) : View(context) {
    var attitude: Attitude? = null
        set(v) { field = v; invalidate() }
    var maxTiltDeg: Double = 3.0
        set(v) { field = v; invalidate() }
    var sensorOk: Boolean = true
        set(v) { field = v; invalidate() }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Ui.LINE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.WHITE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.FG; textAlign = Paint.Align.CENTER }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.MUTED; textAlign = Paint.Align.CENTER }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (hMode == MeasureSpec.UNSPECIFIED) w else min(w, h)
        setMeasuredDimension(w, size)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - Ui.dp(context, 8f)
        // Scale: the outer ring is 3x the warning tilt (in sin units), capped so the view stays useful.
        val outerTilt = maxTiltDeg * 3.0
        val pxPerSin = radius / sin(Math.toRadians(outerTilt))

        val att = attitude
        val tilt = att?.tiltDeg ?: 0.0
        val state = when {
            !sensorOk || att == null -> Ui.MUTED
            tilt <= maxTiltDeg -> Ui.GOOD
            tilt <= maxTiltDeg * 2 -> Ui.WARN
            else -> Ui.BAD
        }

        ring.color = Ui.LINE
        c.drawCircle(cx, cy, radius, ring)
        c.drawLine(cx - radius, cy, cx + radius, cy, cross)
        c.drawLine(cx, cy - radius, cx, cy + radius, cross)
        ring.color = Ui.WARN
        c.drawCircle(cx, cy, (pxPerSin * sin(Math.toRadians(maxTiltDeg * 2))).toFloat(), ring)
        ring.color = Ui.GOOD
        c.drawCircle(cx, cy, (pxPerSin * sin(Math.toRadians(maxTiltDeg))).toFloat(), ring)

        if (att != null && sensorOk) {
            var bx = att.bubbleX * pxPerSin
            var by = att.bubbleY * pxPerSin
            val d = hypot(bx, by)
            if (d > radius) { bx *= radius / d; by *= radius / d }
            dot.color = state
            c.drawCircle((cx + bx).toFloat(), (cy + by).toFloat(), Ui.dp(context, 11f).toFloat(), dot)
            c.drawCircle((cx + bx).toFloat(), (cy + by).toFloat(), Ui.dp(context, 11f).toFloat(), dotEdge)
        }

        txt.textSize = Ui.dp(context, 26f).toFloat()
        txt.color = state
        sub.textSize = Ui.dp(context, 12f).toFloat()
        val label = if (att == null || !sensorOk) "no sensor" else String.format(Locale.US, "%.1f°", tilt)
        c.drawText(label, cx, cy + radius - Ui.dp(context, 14f), txt)
        val subLabel = when {
            att == null || !sensorOk -> "assuming plumb"
            tilt <= maxTiltDeg -> "plumb"
            else -> "top leans " + Units.cardinal(att.leanAzimuthDeg)
        }
        c.drawText(subLabel, cx, cy - radius + Ui.dp(context, 20f), sub)
    }
}
