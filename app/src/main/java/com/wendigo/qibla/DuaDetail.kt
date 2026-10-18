package com.wendigo.qibla

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** Ortak dua detay diyaloğu (Dualar listesi ve arama sonuçları kullanır). */
object DuaDetail {

    fun show(activity: Activity, d: DuaRepository.Dua) {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun add(text: String, size: Float, colorRes: Int, rtl: Boolean = false, bold: Boolean = false) {
            if (text.isBlank()) return
            col.addView(TextView(activity).apply {
                this.text = text
                textSize = size
                setTextColor(activity.getColor(colorRes))
                if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (rtl) {
                    textDirection = View.TEXT_DIRECTION_RTL
                    gravity = Gravity.RIGHT
                    setLineSpacing(10f, 1f)
                } else {
                    setLineSpacing(4f, 1f)
                }
                setPadding(0, 0, 0, pad / 2)
            })
        }

        add(d.title, 17f, R.color.accent, bold = true)
        add(d.arabic, 24f, R.color.text_primary, rtl = true)
        if (d.okunus.isNotBlank()) add(d.okunus, 15f, R.color.text_dim)
        add(d.meal, 16f, R.color.text_primary)
        if (d.arabic.isBlank() && d.intro.isNotBlank()) add(d.intro, 15f, R.color.text_dim)
        add(d.reference, 12f, R.color.text_dim)

        val scroll = ScrollView(activity).apply { addView(col) }
        AlertDialog.Builder(activity)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
