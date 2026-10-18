package com.wendigo.qibla

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

/** Shared Kaaba background images + scrim, used by every screen. */
object AppBackground {

    const val PREFS = "qibla_prefs"
    const val KEY_BG = "bg_style"

    val LABELS = intArrayOf(
        R.string.bg_plain,
        R.string.bg_day,
        R.string.bg_crowd,
        R.string.bg_kiswah,
        R.string.bg_door,
        R.string.bg_night,
        R.string.bg_old,
        R.string.bg_wide,
        R.string.bg_al,
        R.string.bg_city,
        R.string.bg_clock,
        R.string.bg_medina,
        R.string.bg_1910
    )

    val DRAWABLES = intArrayOf(
        0, R.drawable.kaaba_day, R.drawable.kaaba_crowd, R.drawable.kaaba_kiswah,
        R.drawable.kaaba_door, R.drawable.kaaba_night, R.drawable.kaaba_old,
        R.drawable.kaaba_wide, R.drawable.kaaba_al, R.drawable.makkah_city,
        R.drawable.makkah_clock, R.drawable.medina_nabawi, R.drawable.kaaba_1910
    )

    fun current(activity: AppCompatActivity): Int =
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_BG, 0).coerceIn(0, DRAWABLES.size - 1)

    fun apply(activity: AppCompatActivity, image: ImageView, scrim: View) {
        val res = DRAWABLES[current(activity)]
        if (res == 0) {
            image.visibility = View.GONE
            scrim.visibility = View.GONE
        } else {
            image.setImageResource(res)
            image.visibility = View.VISIBLE
            scrim.visibility = View.VISIBLE
        }
    }
}
