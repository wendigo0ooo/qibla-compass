package com.wendigo.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Front-facing figure used to illustrate the ablution (abdest) steps.
 * The active body part is highlighted with a translucent marker.
 */
class WuduFigureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Part { HANDS, MOUTH, NOSE, FACE, ARMS, HEAD, EARS, FEET }

    var part: Part = Part.HANDS
        set(value) {
            field = value
            invalidate()
        }

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = context.getColor(R.color.text_primary)
    }
    private val hlFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.accent)
        alpha = 70
    }
    private val hlEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.accent)
    }
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.text_dim)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val logicalW = 100f
        val logicalH = 140f
        val sc = minOf((width - 24f) / logicalW, (height - 24f) / logicalH)
        val ox = (width - logicalW * sc) / 2f
        val oy = (height - logicalH * sc) / 2f
        body.strokeWidth = 5f * sc
        hlEdge.strokeWidth = 3f * sc
        ground.strokeWidth = 3f * sc

        fun px(v: Float) = ox + v * sc
        fun py(v: Float) = oy + v * sc
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            canvas.drawLine(px(x1), py(y1), px(x2), py(y2), body)

        // body
        canvas.drawCircle(px(50f), py(18f), 12f * sc, body)
        line(50f, 30f, 50f, 84f)
        line(32f, 40f, 68f, 40f)
        line(32f, 40f, 28f, 64f); line(28f, 64f, 32f, 90f)
        line(68f, 40f, 72f, 64f); line(72f, 64f, 68f, 90f)
        line(44f, 84f, 56f, 84f)
        line(44f, 84f, 42f, 112f); line(42f, 112f, 42f, 138f)
        line(56f, 84f, 58f, 112f); line(58f, 112f, 58f, 138f)
        line(42f, 138f, 35f, 138f); line(58f, 138f, 65f, 138f)
        canvas.drawLine(px(12f), py(139f), px(88f), py(139f), ground)

        fun circle(cx: Float, cy: Float, r: Float) {
            canvas.drawCircle(px(cx), py(cy), r * sc, hlFill)
            canvas.drawCircle(px(cx), py(cy), r * sc, hlEdge)
        }
        fun oval(cx: Float, cy: Float, rx: Float, ry: Float) {
            val r = RectF(px(cx - rx), py(cy - ry), px(cx + rx), py(cy + ry))
            canvas.drawOval(r, hlFill)
            canvas.drawOval(r, hlEdge)
        }

        when (part) {
            Part.HANDS -> { circle(32f, 92f, 13f); circle(68f, 92f, 13f) }
            Part.MOUTH -> circle(50f, 25f, 6f)
            Part.NOSE -> circle(50f, 19f, 5f)
            Part.FACE -> oval(50f, 18f, 14f, 16f)
            Part.ARMS -> { circle(29f, 64f, 15f); circle(71f, 64f, 15f) }
            Part.HEAD -> circle(50f, 17f, 18f)
            Part.EARS -> { circle(38f, 20f, 6f); circle(62f, 20f, 6f) }
            Part.FEET -> { circle(39f, 138f, 12f); circle(61f, 138f, 12f) }
        }
    }
}
