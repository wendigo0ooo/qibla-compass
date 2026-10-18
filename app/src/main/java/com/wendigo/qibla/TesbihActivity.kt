package com.wendigo.qibla

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class TesbihActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "qibla_prefs"
        private const val KEY_COUNT = "tesbih_count"
        private const val KEY_TARGET = "tesbih_target"
        private const val KEY_PRESET = "tesbih_preset"

        private val PRESETS = listOf(
            "Sübhânallâh" to 33,
            "Elhamdülillâh" to 33,
            "Allâhü Ekber" to 34,
            "Lâ ilâhe illallâh" to 100,
            "Estağfirullâh" to 100,
            "Salavât" to 100,
            "Serbest" to 0
        )
    }

    private var count = 0
    private var target = 33
    private var preset = 0
    private lateinit var tvCount: TextView
    private lateinit var tvTarget: TextView
    private lateinit var tvPreset: TextView
    private lateinit var dial: TesbihDialView

    private val celebrateHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesbih)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.tesbihToolbar).apply {
            title = getString(R.string.tab_tesbih)
            setNavigationOnClickListener { finish() }
        }

        tvCount = findViewById(R.id.tbCount)
        tvTarget = findViewById(R.id.tbTarget)
        tvPreset = findViewById(R.id.tbPreset)
        dial = findViewById(R.id.tbDial)

        count = prefs.getInt(KEY_COUNT, 0)
        target = prefs.getInt(KEY_TARGET, 33)
        preset = prefs.getInt(KEY_PRESET, 0)

        findViewById<FrameLayout>(R.id.tbTap).setOnClickListener { increment() }
        findViewById<Button>(R.id.tbReset).setOnClickListener { reset() }
        findViewById<Button>(R.id.tbChange).setOnClickListener { choosePreset() }
        findViewById<Button>(R.id.tbTargetBtn).setOnClickListener { chooseTarget() }

        findViewById<View>(R.id.tbCelebrate).setOnClickListener { hideCelebration() }
        findViewById<Button>(R.id.tbCelebrateContinue).setOnClickListener { hideCelebration() }
        findViewById<Button>(R.id.tbCelebrateReset).setOnClickListener {
            reset()
            hideCelebration()
        }

        render()
    }

    override fun onPause() {
        super.onPause()
        celebrateHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        celebrateHandler.removeCallbacksAndMessages(null)
    }

    private fun increment() {
        count++
        vibrate(15)
        animateCount()
        val reached = target > 0 && count == target
        prefs.edit { putInt(KEY_COUNT, count) }
        render()
        if (reached) celebrate()
    }

    private fun reset() {
        count = 0
        prefs.edit { putInt(KEY_COUNT, count) }
        render()
    }

    private fun choosePreset() {
        val labels = PRESETS.map { "${it.first}  (${if (it.second > 0) it.second else "∞"})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.tb_change)
            .setItems(labels) { _, which ->
                preset = which
                target = PRESETS[which].second
                count = 0
                prefs.edit {
                    putInt(KEY_PRESET, preset)
                    putInt(KEY_TARGET, target)
                    putInt(KEY_COUNT, count)
                }
                hideCelebration()
                render()
            }
            .show()
    }

    private fun chooseTarget() {
        val options = arrayOf("33", "34", "99", "100", "Serbest", "Elle gir…")
        AlertDialog.Builder(this)
            .setTitle(R.string.tb_target)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setTarget(33)
                    1 -> setTarget(34)
                    2 -> setTarget(99)
                    3 -> setTarget(100)
                    4 -> setTarget(0)
                    5 -> customTarget()
                }
            }
            .show()
    }

    private fun customTarget() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0 = serbest"
            setPadding(48, 24, 48, 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.tb_target)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                setTarget(input.text.toString().toIntOrNull() ?: 0)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setTarget(value: Int) {
        target = value.coerceAtLeast(0)
        prefs.edit { putInt(KEY_TARGET, target) }
        hideCelebration()
        render()
    }

    private fun render() {
        tvCount.text = count.toString()
        val name = PRESETS[preset.coerceIn(0, PRESETS.size - 1)].first
        tvPreset.text = name
        tvTarget.text = if (target > 0) {
            getString(R.string.tb_target_fmt, target)
        } else {
            getString(R.string.tb_free)
        }
        dial.setValues(count, target)
    }

    private fun animateCount() {
        tvCount.animate().cancel()
        tvCount.scaleX = 1.12f
        tvCount.scaleY = 1.12f
        tvCount.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
    }

    private fun celebrate() {
        vibratePattern(longArrayOf(0, 60, 80, 140))
        val name = PRESETS[preset.coerceIn(0, PRESETS.size - 1)].first
        findViewById<TextView>(R.id.tbCelebrateText).text =
            getString(R.string.tb_target_reached, name, target)

        val overlay = findViewById<View>(R.id.tbCelebrate)
        val card = findViewById<View>(R.id.tbCelebrateCard)
        val icon = findViewById<View>(R.id.tbCelebrateIcon)
        overlay.animate().cancel()
        card.animate().cancel()
        icon.animate().cancel()

        overlay.alpha = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        icon.scaleX = 0.5f
        icon.scaleY = 0.5f
        icon.rotation = -30f
        overlay.visibility = View.VISIBLE

        overlay.animate().alpha(1f).setDuration(180).start()
        card.animate().scaleX(1f).scaleY(1f).setDuration(240).start()
        icon.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(340).start()

        celebrateHandler.removeCallbacksAndMessages(null)
        celebrateHandler.postDelayed({ hideCelebration() }, 4500L)
    }

    private fun hideCelebration() {
        val overlay = findViewById<View>(R.id.tbCelebrate)
        if (overlay.visibility != View.VISIBLE) return
        celebrateHandler.removeCallbacksAndMessages(null)
        overlay.animate().alpha(0f).setDuration(160)
            .withEndAction { overlay.visibility = View.GONE }.start()
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        try {
            val v = vibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun vibratePattern(pattern: LongArray) {
        try {
            val v = vibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                v.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
