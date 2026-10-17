package com.wendigo.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

/** Transparent overlay drawing an arrow that points to the Qibla over the camera preview. */
class QiblaOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var azimuth = 0f
        set(value) {
            field = value
            invalidate()
        }

    var qibla = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val arrow = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val rot = qibla - azimuth
        val aligned = abs(((rot + 540f) % 360f) - 180f) < 5f
        val color = if (aligned) Color.parseColor("#00E676") else Color.parseColor("#FFC107")
        linePaint.color = color
        fillPaint.color = color

        val len = minOf(width, height) * 0.30f

        canvas.save()
        canvas.rotate(rot, cx, cy)

        canvas.drawLine(cx, cy, cx, cy - len, linePaint)

        val tip = cy - len
        arrow.reset()
        arrow.moveTo(cx, tip - 46f)
        arrow.lineTo(cx - 34f, tip + 14f)
        arrow.lineTo(cx + 34f, tip + 14f)
        arrow.close()
        canvas.drawPath(arrow, fillPaint)

        canvas.restore()

        fillPaint.color = Color.parseColor("#80FFFFFF")
        canvas.drawCircle(cx, cy, 10f, fillPaint)
    }
}
