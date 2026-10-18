package com.wendigo.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

class TesbihDialView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var count = 0
    private var target = 33

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val accent = ContextCompat.getColor(context, R.color.accent)
    private val dim = ContextCompat.getColor(context, R.color.text_dim)
    private val trackColor = ContextCompat.getColor(context, R.color.panel_stroke)

    init {
        trackPaint.strokeWidth = dp(2f)
        trackPaint.color = trackColor
        ringPaint.strokeWidth = dp(2.5f)
    }

    fun setValues(newCount: Int, newTarget: Int) {
        count = newCount
        target = newTarget
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - dp(16f)
        if (radius <= 0f) return

        canvas.drawCircle(cx, cy, radius, trackPaint)

        val beadCount = if (target in 1..40) target else 33
        val beadR = dp(3.4f)
        val filled = when {
            beadCount <= 0 -> 0
            count <= 0 -> 0
            count % beadCount == 0 -> beadCount
            else -> count % beadCount
        }

        for (i in 0 until beadCount) {
            val ang = Math.toRadians(i.toDouble() / beadCount * 360.0 - 90.0)
            val x = cx + (radius * cos(ang)).toFloat()
            val y = cy + (radius * sin(ang)).toFloat()
            if (i < filled) {
                beadPaint.color = accent
                beadPaint.alpha = 255
                canvas.drawCircle(x, y, beadR + dp(0.6f), beadPaint)
            } else {
                beadPaint.color = dim
                beadPaint.alpha = 80
                canvas.drawCircle(x, y, beadR, beadPaint)
            }
        }

        if (filled < beadCount) {
            val ang = Math.toRadians(filled.toDouble() / beadCount * 360.0 - 90.0)
            val x = cx + (radius * cos(ang)).toFloat()
            val y = cy + (radius * sin(ang)).toFloat()
            ringPaint.color = accent
            ringPaint.alpha = 210
            canvas.drawCircle(x, y, beadR + dp(3.5f), ringPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
