package com.wendigo.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Compass rose that rotates with the device heading and shows a green arrow
 * pointing toward the Qibla direction.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var heading = 0f          // device heading, degrees from true north
    private var qibla = 0f            // qibla bearing, degrees from true north
    private var hasLocation = false

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#33FFFFFF")
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val needleNorthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E53935")
    }
    private val needleSouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#90A4AE")
    }
    private val qiblaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E676")
    }
    private val qiblaLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#6600E676")
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    fun setHeading(deg: Float) {
        heading = normalize(deg)
        invalidate()
    }

    fun setQibla(deg: Float) {
        qibla = normalize(deg)
        invalidate()
    }

    fun setHasLocation(value: Boolean) {
        hasLocation = value
        invalidate()
    }

    private fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f * 0.86f
        cardinalPaint.textSize = radius * 0.17f
        dialPaint.strokeWidth = radius * 0.02f
        tickPaint.strokeWidth = radius * 0.012f
        qiblaLinePaint.strokeWidth = radius * 0.022f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return

        canvas.drawCircle(cx, cy, radius, dialPaint)
        drawTicks(canvas)
        drawCardinals(canvas)
        drawNeedle(canvas)
        drawQibla(canvas)
        canvas.drawCircle(cx, cy, radius * 0.035f, hubPaint)
    }

    private fun drawTicks(canvas: Canvas) {
        for (i in 0 until 360 step 2) {
            val major = i % 30 == 0
            val mid = i % 10 == 0
            val len = when {
                major -> radius * 0.09f
                mid -> radius * 0.06f
                else -> radius * 0.035f
            }
            tickPaint.alpha = when {
                major -> 220
                mid -> 150
                else -> 70
            }
            val a = Math.toRadians((i - heading).toDouble())
            val s = sin(a).toFloat()
            val c = cos(a).toFloat()
            canvas.drawLine(
                cx + radius * s, cy - radius * c,
                cx + (radius - len) * s, cy - (radius - len) * c,
                tickPaint
            )
        }
    }

    private fun drawCardinals(canvas: Canvas) {
        val labels = arrayOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
        for ((text, angle) in labels) {
            cardinalPaint.color =
                if (text == "N") Color.parseColor("#E53935") else Color.parseColor("#EEFFFFFF")
            val a = Math.toRadians((angle - heading).toDouble())
            val r = radius * 0.76f
            val x = cx + r * sin(a).toFloat()
            val y = cy - r * cos(a).toFloat() + cardinalPaint.textSize / 3f
            canvas.drawText(text, x, y, cardinalPaint)
        }
    }

    private fun drawNeedle(canvas: Canvas) {
        canvas.save()
        canvas.rotate(-heading, cx, cy)
        val len = radius * 0.6f
        val w = radius * 0.045f

        val south = Path().apply {
            moveTo(cx, cy + len * 0.85f)
            lineTo(cx - w, cy)
            lineTo(cx + w, cy)
            close()
        }
        val north = Path().apply {
            moveTo(cx, cy - len)
            lineTo(cx - w, cy)
            lineTo(cx + w, cy)
            close()
        }
        canvas.drawPath(south, needleSouthPaint)
        canvas.drawPath(north, needleNorthPaint)
        canvas.restore()
    }

    private fun drawQibla(canvas: Canvas) {
        if (!hasLocation) return
        canvas.save()
        canvas.rotate(qibla - heading, cx, cy)

        val tip = radius * 0.95f
        val base = radius * 0.66f
        val halfW = radius * 0.09f

        canvas.drawLine(cx, cy, cx, cy - base, qiblaLinePaint)

        val arrow = Path().apply {
            moveTo(cx, cy - tip)
            lineTo(cx - halfW, cy - base)
            lineTo(cx, cy - base + halfW * 0.9f)
            lineTo(cx + halfW, cy - base)
            close()
        }
        canvas.drawPath(arrow, qiblaPaint)
        canvas.restore()
    }
}
