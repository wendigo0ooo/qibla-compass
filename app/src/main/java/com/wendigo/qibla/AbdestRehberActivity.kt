package com.wendigo.qibla

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/** Step-by-step illustrated guide for ablution (abdest). */
class AbdestRehberActivity : AppCompatActivity() {

    private data class Step(
        val part: WuduFigureView.Part,
        val titleRes: Int,
        val bodyRes: Int
    )

    private val steps = listOf(
        Step(WuduFigureView.Part.HANDS, R.string.abdest_step1_title, R.string.abdest_step1_body),
        Step(WuduFigureView.Part.MOUTH, R.string.abdest_step2_title, R.string.abdest_step2_body),
        Step(WuduFigureView.Part.NOSE, R.string.abdest_step3_title, R.string.abdest_step3_body),
        Step(WuduFigureView.Part.FACE, R.string.abdest_step4_title, R.string.abdest_step4_body),
        Step(WuduFigureView.Part.ARMS, R.string.abdest_step5_title, R.string.abdest_step5_body),
        Step(WuduFigureView.Part.HEAD, R.string.abdest_step6_title, R.string.abdest_step6_body),
        Step(WuduFigureView.Part.EARS, R.string.abdest_step7_title, R.string.abdest_step7_body),
        Step(WuduFigureView.Part.FEET, R.string.abdest_step8_title, R.string.abdest_step8_body)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rehber)

        findViewById<Toolbar>(R.id.rehberToolbar).apply {
            title = getString(R.string.tool_abdest_title)
            setNavigationOnClickListener { finish() }
        }

        val container = findViewById<LinearLayout>(R.id.rehberContainer)
        container.addView(textCard(getString(R.string.abdest_rehber_intro), false))
        for (step in steps) container.addView(figureCard(step))
        container.addView(textCard(getString(R.string.abdest_rehber_farz), true))
    }

    private fun figureCard(step: Step): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.panel_bg)
            setPadding(dp(16), dp(14), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        card.addView(WuduFigureView(this).apply {
            part = step.part
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)
            )
        })
        card.addView(TextView(this).apply {
            text = getString(step.titleRes)
            setTextColor(getColor(R.color.accent))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = getString(step.bodyRes)
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(4), 0, 0)
        })
        return card
    }

    private fun textCard(text: String, dim: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.panel_bg)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            addView(TextView(this@AbdestRehberActivity).apply {
                this.text = text
                setTextColor(getColor(if (dim) R.color.text_dim else R.color.text_primary))
                textSize = 13.5f
                setLineSpacing(dp(3).toFloat(), 1f)
            })
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
