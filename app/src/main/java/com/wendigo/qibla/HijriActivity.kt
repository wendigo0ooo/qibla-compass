package com.wendigo.qibla

import android.app.DatePickerDialog
import android.icu.util.IslamicCalendar
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HijriActivity : AppCompatActivity() {

    companion object {
        private val HIJRI_MONTHS = arrayOf(
            "Muharrem", "Safer", "Rebîulevvel", "Rebîulâhir",
            "Cemâziyelevvel", "Cemâziyelâhir", "Recep", "Şaban",
            "Ramazan", "Şevval", "Zilkade", "Zilhicce"
        )
    }

    private val tr = Locale("tr", "TR")
    private lateinit var gregFmt: SimpleDateFormat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hijri)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.hijriToolbar).apply {
            title = getString(R.string.tool_hijri_title)
            setNavigationOnClickListener { finish() }
        }

        gregFmt = SimpleDateFormat("d MMMM yyyy, EEEE", tr)

        val now = Date()
        findViewById<TextView>(R.id.hijriDate).text = formatHijri(now)
        findViewById<TextView>(R.id.gregDate).text = gregFmt.format(now)

        findViewById<Button>(R.id.btnConvert).setOnClickListener { pickDate() }
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d)
                val date = cal.time
                val tv = findViewById<TextView>(R.id.convertResult)
                tv.text = gregFmt.format(date) + "\n" + formatHijri(date)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatHijri(date: Date): String {
        val ic = IslamicCalendar()
        ic.time = date
        val y = ic.get(IslamicCalendar.YEAR)
        val m = ic.get(IslamicCalendar.MONTH)
        val d = ic.get(IslamicCalendar.DAY_OF_MONTH)
        val month = HIJRI_MONTHS.getOrElse(m) { "" }
        return "$d $month $y"
    }
}
