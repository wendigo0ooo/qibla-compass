package com.wendigo.qibla

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

        count = prefs.getInt(KEY_COUNT, 0)
        target = prefs.getInt(KEY_TARGET, 33)
        preset = prefs.getInt(KEY_PRESET, 0)

        findViewById<FrameLayout>(R.id.tbTap).setOnClickListener { increment() }
        findViewById<Button>(R.id.tbReset).setOnClickListener { reset() }
        findViewById<Button>(R.id.tbChange).setOnClickListener { choosePreset() }
        findViewById<Button>(R.id.tbTargetBtn).setOnClickListener { chooseTarget() }

        render()
    }

    private fun increment() {
        count++
        vibrate(15)
        if (target > 0 && count == target) {
            vibrate(250)
            Toast.makeText(this, getString(R.string.tb_done), Toast.LENGTH_SHORT).show()
        }
        prefs.edit { putInt(KEY_COUNT, count) }
        render()
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
        render()
    }

    private fun render() {
        tvCount.text = count.toString()
        tvPreset.text = PRESETS[preset.coerceIn(0, PRESETS.size - 1)].first
        tvTarget.text = if (target > 0) {
            getString(R.string.tb_target_fmt, target)
        } else {
            getString(R.string.tb_free)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }
}
