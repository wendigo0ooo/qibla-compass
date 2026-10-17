package com.wendigo.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Flat (filled) front-facing figure used to illustrate the ablution (abdest)
 * steps. The active body part is highlighted with a translucent marker.
 * Drawn entirely with Canvas primitives, no external assets.
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

    private val accent = context.getColor(R.color.accent)

    private val garment = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accent
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = accent
    }
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = dark(accent, 0.68f)
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = dark(accent, 0.6f)
    }
    private val skin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE7B48B.toInt()
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x1A000000
    }
    private val hlFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accent
        alpha = 64
    }
    private val hlEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = accent
    }

    private fun dark(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * f).toInt(),
        (Color.green(color) * f).toInt(),
        (Color.blue(color) * f).toInt()
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val lw = 120f
        val lh = 178f
        val sc = minOf((width - 12f) / lw, (height - 12f) / lh)
        val ox = (width - lw * sc) / 2f
        val oy = (height - lh * sc) / 2f

        fun dx(v: Float) = ox + v * sc
        fun dy(v: Float) = oy + v * sc

        fun stroke(p: Paint, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
            p.strokeWidth = w * sc
            canvas.drawLine(dx(x1), dy(y1), dx(x2), dy(y2), p)
        }
        fun body(x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
            stroke(bodyPaint, x1, y1, x2, y2, w)
        fun arm(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 11f) =
            stroke(armPaint, x1, y1, x2, y2, w)

        fun head(cx: Float, cy: Float, r: Float) {
            canvas.drawCircle(dx(cx), dy(cy), r * sc, skin)
            val p = Path()
            p.addArc(
                dx(cx) - r * 1.04f * sc, dy(cy) - r * 1.3f * sc,
                dx(cx) + r * 1.04f * sc, dy(cy) + r * 0.8f * sc,
                180f, 180f
            )
            p.close()
            canvas.drawPath(p, capPaint)
        }
        fun hand(cx: Float, cy: Float, r: Float = 5f) =
            canvas.drawCircle(dx(cx), dy(cy), r * sc, skin)
        fun foot(cx: Float, cy: Float) {
            canvas.drawOval(
                RectF(dx(cx - 7f), dy(cy - 3.5f), dx(cx + 7f), dy(cy + 3.5f)), skin
            )
        }
        fun groundShadow(x1: Float, x2: Float, y: Float) {
            canvas.drawOval(RectF(dx(x1), dy(y - 4f), dx(x2), dy(y + 4f)), shadow)
        }

        // ---- static figure (front view) ----
        groundShadow(26f, 94f, 168f)
        body(60f, 48f, 60f, 110f, 30f)
        body(51f, 110f, 51f, 150f, 16f)
        body(69f, 110f, 69f, 150f, 16f)
        foot(49f, 158f)
        foot(71f, 158f)
        arm(46f, 56f, 40f, 98f)
        arm(74f, 56f, 80f, 98f)
        hand(39f, 101f)
        hand(81f, 101f)
        head(60f, 30f, 14f)

        // ---- highlight ----
        fun circle(cx: Float, cy: Float, r: Float) {
            canvas.drawCircle(dx(cx), dy(cy), r * sc, hlFill)
            hlEdge.strokeWidth = 2.5f * sc
            canvas.drawCircle(dx(cx), dy(cy), r * sc, hlEdge)
        }
        fun oval(cx: Float, cy: Float, rx: Float, ry: Float) {
            val r = RectF(dx(cx - rx), dy(cy - ry), dx(cx + rx), dy(cy + ry))
            canvas.drawOval(r, hlFill)
            hlEdge.strokeWidth = 2.5f * sc
            canvas.drawOval(r, hlEdge)
        }

        when (part) {
            Part.HANDS -> { circle(39f, 101f, 13f); circle(81f, 101f, 13f) }
            Part.MOUTH -> circle(60f, 36f, 6f)
            Part.NOSE -> circle(60f, 29f, 5f)
            Part.FACE -> oval(60f, 30f, 15f, 17f)
            Part.ARMS -> { oval(40f, 78f, 9f, 26f); oval(80f, 78f, 9f, 26f) }
            Part.HEAD -> circle(60f, 28f, 20f)
            Part.EARS -> { circle(45f, 33f, 6f); circle(75f, 33f, 6f) }
            Part.FEET -> { oval(49f, 158f, 12f, 8f); oval(71f, 158f, 12f, 8f) }
        }
    }
}
