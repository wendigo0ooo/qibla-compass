package com.wendigo.qibla

import android.app.DatePickerDialog
import android.icu.util.IslamicCalendar
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
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
        private const val MIN_HIJRI_YEAR = 1300
        private const val MAX_HIJRI_YEAR = 1600
    }

    private val tr = Locale("tr", "TR")
    private lateinit var gregFmt: SimpleDateFormat

    private var gregToHijri = true
    private var pickedGreg: Date? = null

    private lateinit var modeG2H: TextView
    private lateinit var modeH2G: TextView
    private lateinit var convLabel: TextView
    private lateinit var selectedGreg: TextView
    private lateinit var hijriFields: View
    private lateinit var hijriDay: EditText
    private lateinit var hijriMonth: EditText
    private lateinit var hijriYear: EditText
    private lateinit var hijriPreview: TextView
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hijri)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.hijriToolbar).apply {
            title = getString(R.string.tool_hijri_title)
            setNavigationOnClickListener { finish() }
        }

        gregFmt = SimpleDateFormat("d MMMM yyyy, EEEE", tr)

        modeG2H = findViewById(R.id.modeG2H)
        modeH2G = findViewById(R.id.modeH2G)
        convLabel = findViewById(R.id.convLabel)
        selectedGreg = findViewById(R.id.selectedGreg)
        hijriFields = findViewById(R.id.hijriFields)
        hijriDay = findViewById(R.id.hijriDay)
        hijriMonth = findViewById(R.id.hijriMonth)
        hijriYear = findViewById(R.id.hijriYear)
        hijriPreview = findViewById(R.id.hijriPreview)
        result = findViewById(R.id.convertResult)

        val now = Date()
        findViewById<TextView>(R.id.hijriDate).text = formatHijri(now)
        findViewById<TextView>(R.id.gregDate).text = gregFmt.format(now)

        seedHijriFields(now)

        modeG2H.setOnClickListener { setMode(true) }
        modeH2G.setOnClickListener { setMode(false) }
        findViewById<Button>(R.id.btnConvert).setOnClickListener {
            if (gregToHijri) pickGregorian() else convertHijriToGregorian()
        }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updatePreview()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        hijriDay.addTextChangedListener(watcher)
        hijriMonth.addTextChangedListener(watcher)
        hijriYear.addTextChangedListener(watcher)

        setMode(true)
    }

    private fun seedHijriFields(date: Date) {
        val ic = IslamicCalendar()
        ic.time = date
        hijriDay.setText(ic.get(IslamicCalendar.DAY_OF_MONTH).toString())
        hijriMonth.setText((ic.get(IslamicCalendar.MONTH) + 1).toString())
        hijriYear.setText(ic.get(IslamicCalendar.YEAR).toString())
    }

    private fun setMode(g2h: Boolean) {
        gregToHijri = g2h
        styleChip(modeG2H, g2h)
        styleChip(modeH2G, !g2h)
        convLabel.setText(if (g2h) R.string.hijri_conv_label_greg else R.string.hijri_conv_label_hijri)
        selectedGreg.visibility = if (g2h) View.VISIBLE else View.GONE
        hijriFields.visibility = if (g2h) View.GONE else View.VISIBLE
        hijriPreview.visibility = if (g2h) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.btnConvert)
            .setText(if (g2h) R.string.hijri_pick else R.string.hijri_convert)
        result.setText(R.string.hijri_convert_hint)
        updatePreview()
    }

    private fun styleChip(tv: TextView, selected: Boolean) {
        tv.setBackgroundResource(if (selected) R.drawable.chip_bg_selected else R.drawable.chip_bg)
        tv.setTextColor(getColor(if (selected) android.R.color.white else R.color.text_primary))
    }

    private fun updatePreview() {
        if (gregToHijri) return
        val m = hijriMonth.text.toString().trim().toIntOrNull()
        val y = hijriYear.text.toString().trim().toIntOrNull()
        hijriPreview.text = if (m != null && m in 1..12 && y != null) {
            "${HIJRI_MONTHS[m - 1]} $y"
        } else ""
    }

    private fun pickGregorian() {
        val c = Calendar.getInstance()
        pickedGreg?.let { c.time = it }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.clear()
                cal.set(y, m, d)
                val date = cal.time
                pickedGreg = date
                selectedGreg.text = getString(R.string.hijri_selected, gregFmt.format(date))
                result.text = gregFmt.format(date) + "\n" + formatHijri(date)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun convertHijriToGregorian() {
        val d = hijriDay.text.toString().trim().toIntOrNull()
        val m = hijriMonth.text.toString().trim().toIntOrNull()
        val y = hijriYear.text.toString().trim().toIntOrNull()
        if (d == null || m == null || y == null ||
            d !in 1..30 || m !in 1..12 || y !in MIN_HIJRI_YEAR..MAX_HIJRI_YEAR
        ) {
            result.setText(R.string.hijri_invalid)
            return
        }
        val ic = IslamicCalendar()
        ic.clear()
        ic.set(IslamicCalendar.YEAR, y)
        ic.set(IslamicCalendar.MONTH, m - 1)
        ic.set(IslamicCalendar.DAY_OF_MONTH, d)
        val date = ic.time
        result.text = "$d ${HIJRI_MONTHS[m - 1]} $y\n" + gregFmt.format(date)
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
