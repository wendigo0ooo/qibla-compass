package com.wendigo.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Draws a simple stick-figure illustration of the positions of the ritual
 * prayer (namaz). Pure Canvas drawing so no external images are needed.
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

    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        ground.strokeWidth = 3f * sc

        fun px(v: Float) = ox + v * sc
        fun py(v: Float) = oy + v * sc
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            canvas.drawLine(px(x1), py(y1), px(x2), py(y2), body)
        fun head(cx: Float, cy: Float, r: Float) =
            canvas.drawCircle(px(cx), py(cy), r * sc, body)
        fun groundLine(x1: Float, y1: Float, x2: Float, y2: Float) =
            canvas.drawLine(px(x1), py(y1), px(x2), py(y2), ground)

        when (pose) {
            Pose.KIYAM -> {
                head(50f, 16f, 10f)
                line(50f, 26f, 50f, 80f)
                line(34f, 38f, 66f, 38f)
                line(34f, 38f, 30f, 58f); line(30f, 58f, 48f, 61f)
                line(66f, 38f, 70f, 58f); line(70f, 58f, 52f, 61f)
                line(44f, 80f, 56f, 80f)
                line(48f, 80f, 43f, 110f); line(43f, 110f, 43f, 136f)
                line(52f, 80f, 57f, 110f); line(57f, 110f, 57f, 136f)
            }
            Pose.TEKBIR -> {
                head(50f, 16f, 10f)
                line(50f, 26f, 50f, 80f)
                line(34f, 38f, 66f, 38f)
                line(34f, 38f, 26f, 42f); line(26f, 42f, 36f, 20f)
                line(66f, 38f, 74f, 42f); line(74f, 42f, 64f, 20f)
                line(44f, 80f, 56f, 80f)
                line(48f, 80f, 43f, 110f); line(43f, 110f, 43f, 136f)
                line(52f, 80f, 57f, 110f); line(57f, 110f, 57f, 136f)
            }
            Pose.RUKU -> {
                head(16f, 54f, 10f)
                line(26f, 60f, 58f, 74f)
                line(30f, 55f, 34f, 66f)
                line(30f, 58f, 34f, 92f)
                line(34f, 61f, 38f, 93f)
                line(58f, 74f, 58f, 106f); line(58f, 106f, 58f, 136f)
                line(60f, 76f, 60f, 108f); line(60f, 108f, 60f, 136f)
                line(58f, 136f, 64f, 136f); line(60f, 136f, 66f, 136f)
                groundLine(8f, 138f, 92f, 138f)
            }
            Pose.SECDE -> {
                head(20f, 124f, 10f)
                line(30f, 120f, 58f, 94f)
                line(33f, 118f, 28f, 133f)
                line(36f, 120f, 32f, 133f)
                line(58f, 94f, 66f, 116f); line(66f, 116f, 80f, 132f)
                line(58f, 96f, 62f, 118f); line(62f, 118f, 72f, 133f)
                groundLine(6f, 135f, 94f, 135f)
            }
            Pose.OTURUS -> {
                head(58f, 64f, 10f)
                line(58f, 74f, 58f, 106f)
                line(52f, 78f, 64f, 78f)
                line(52f, 78f, 48f, 94f); line(48f, 94f, 44f, 104f)
                line(64f, 78f, 60f, 94f); line(60f, 94f, 56f, 104f)
                line(58f, 106f, 38f, 112f); line(38f, 112f, 32f, 132f)
                line(60f, 108f, 40f, 116f); line(40f, 116f, 34f, 134f)
                groundLine(20f, 135f, 84f, 135f)
            }
            Pose.SELAM -> {
                head(66f, 62f, 10f)
                line(58f, 74f, 64f, 66f)
                line(58f, 74f, 58f, 106f)
                line(52f, 78f, 64f, 78f)
                line(52f, 78f, 48f, 94f); line(48f, 94f, 44f, 104f)
                line(64f, 78f, 60f, 94f); line(60f, 94f, 56f, 104f)
                line(58f, 106f, 38f, 112f); line(38f, 112f, 32f, 132f)
                line(60f, 108f, 40f, 116f); line(40f, 116f, 34f, 134f)
                line(76f, 56f, 83f, 53f)
                line(78f, 64f, 85f, 62f)
                groundLine(20f, 135f, 84f, 135f)
            }
        }
    }
}
