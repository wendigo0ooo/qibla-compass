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
 * Flat (filled) illustration of the positions of the ritual prayer (namaz).
 * Everything is drawn with Canvas primitives, so it needs no external assets
 * and carries no third-party licence.
 */
class SalahFigureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class Pose { KIYAM, TEKBIR, RUKU, SECDE, OTURUS, SELAM }

    var pose: Pose = Pose.KIYAM
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

    private fun dark(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * f).toInt(),
        (Color.green(color) * f).toInt(),
        (Color.blue(color) * f).toInt()
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val lw = 120f
        val lh = 174f
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

        fun lump(cx: Float, cy: Float, rx: Float, ry: Float) {
            canvas.drawOval(
                RectF(dx(cx - rx), dy(cy - ry), dx(cx + rx), dy(cy + ry)), garment
            )
        }

        fun groundShadow(x1: Float, x2: Float, y: Float) {
            canvas.drawOval(
                RectF(dx(x1), dy(y - 4f), dx(x2), dy(y + 4f)), shadow
            )
        }

        fun stand() {
            groundShadow(26f, 94f, 164f)
            body(60f, 58f, 60f, 142f, 30f)
            foot(49f, 159f)
            foot(71f, 159f)
            head(60f, 40f, 14f)
        }

        when (pose) {
            Pose.KIYAM -> {
                stand()
                arm(46f, 68f, 58f, 94f)
                arm(74f, 68f, 62f, 94f)
                hand(58f, 96f)
                hand(62f, 96f)
            }
            Pose.TEKBIR -> {
                stand()
                arm(46f, 68f, 38f, 42f)
                arm(74f, 68f, 82f, 42f)
                hand(38f, 39f)
                hand(82f, 39f)
            }
            Pose.RUKU -> {
                groundShadow(24f, 96f, 164f)
                body(58f, 144f, 58f, 110f, 30f)
                foot(47f, 159f)
                foot(69f, 159f)
                body(60f, 112f, 90f, 98f, 26f)
                arm(84f, 102f, 64f, 120f)
                hand(64f, 122f)
                head(101f, 93f, 13f)
            }
            Pose.SECDE -> {
                groundShadow(30f, 104f, 164f)
                lump(58f, 148f, 26f, 14f)
                body(60f, 140f, 90f, 150f, 24f)
                arm(90f, 150f, 100f, 158f, 10f)
                hand(101f, 159f, 4f)
                foot(80f, 159f)
                head(102f, 152f, 12f)
            }
            Pose.OTURUS -> {
                groundShadow(26f, 94f, 164f)
                lump(60f, 148f, 28f, 13f)
                body(60f, 140f, 60f, 76f, 30f)
                arm(47f, 86f, 56f, 122f)
                arm(73f, 86f, 64f, 122f)
                hand(56f, 124f)
                hand(64f, 124f)
                head(60f, 58f, 14f)
            }
            Pose.SELAM -> {
                groundShadow(26f, 94f, 164f)
                lump(60f, 148f, 28f, 13f)
                body(60f, 140f, 60f, 76f, 30f)
                arm(47f, 86f, 56f, 122f)
                arm(73f, 86f, 64f, 122f)
                hand(56f, 124f)
                hand(64f, 124f)
                head(66f, 58f, 14f)
                hand(79f, 61f, 3.5f)
            }
        }
    }
}
