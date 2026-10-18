package com.wendigo.qibla

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerTrackerActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "qibla_prefs"
        private const val KEY_LAT = "device_lat"
        private const val KEY_LON = "device_lon"
        private const val KEY_TRACK_PREFIX = "track_"
        private const val KEY_KAZA_PREFIX = "kaza_"
        private const val KEY_ADDED_PREFIX = "kaza_added_"
        private const val DAYS = 5
        private val LABELS = intArrayOf(
            R.string.pt_sabah, R.string.pt_ogle, R.string.pt_ikindi,
            R.string.pt_aksam, R.string.pt_yatsi
        )
        private val ICONS = intArrayOf(
            R.drawable.namaz_imsak, R.drawable.namaz_ogle, R.drawable.namaz_ikindi,
            R.drawable.namaz_aksam, R.drawable.namaz_yatsi
        )
        private val HIJRI_MONTHS = arrayOf(
            "Muharrem", "Safer", "Rebîulevvel", "Rebîulâhir",
            "Cemâziyelevvel", "Cemâziyelâhir", "Recep", "Şaban",
            "Ramazan", "Şevval", "Zilkade", "Zilhicce"
        )
        private val WEEK_HEAD = arrayOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")
    }

    private val tr = Locale("tr", "TR")
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val today = Calendar.getInstance()
    private var cursor = Calendar.getInstance()

    private lateinit var todayList: LinearLayout
    private lateinit var kazaList: LinearLayout
    private lateinit var monthGrid: LinearLayout
    private lateinit var weekHeader: LinearLayout
    private lateinit var hijriText: TextView
    private lateinit var gregText: TextView
    private lateinit var streakText: TextView
    private lateinit var progressText: TextView
    private lateinit var monthName: TextView
    private lateinit var monthPrayers: TextView
    private lateinit var monthDays: TextView
    private lateinit var noLoc: TextView
    private lateinit var kazaDone: TextView

    private var times: DoubleArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prayer_tracker)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.ptToolbar).apply {
            title = getString(R.string.pt_title)
            setNavigationOnClickListener { finish() }
        }

        todayList = findViewById(R.id.ptTodayList)
        kazaList = findViewById(R.id.ptKazaList)
        monthGrid = findViewById(R.id.ptMonthGrid)
        weekHeader = findViewById(R.id.ptWeekHeader)
        hijriText = findViewById(R.id.ptHijri)
        gregText = findViewById(R.id.ptGreg)
        streakText = findViewById(R.id.ptStreak)
        progressText = findViewById(R.id.ptProgress)
        monthName = findViewById(R.id.ptMonthName)
        monthPrayers = findViewById(R.id.ptMonthPrayers)
        monthDays = findViewById(R.id.ptMonthDays)
        noLoc = findViewById(R.id.ptNoLoc)
        kazaDone = findViewById(R.id.ptKazaDone)

        cursor.set(Calendar.DAY_OF_MONTH, 1)

        computeTimes()
        buildToday()
        buildKaza()
        buildWeekHeader()
        buildMonth()
        refreshHeader()

        findViewById<android.widget.Button>(R.id.ptAddMissed).setOnClickListener { addMissed() }
        findViewById<android.widget.Button>(R.id.ptReset).setOnClickListener { resetToday() }
        findViewById<TextView>(R.id.ptKazaReset).setOnClickListener { confirmKazaReset() }
        findViewById<TextView>(R.id.ptPrev).setOnClickListener {
            cursor.add(Calendar.MONTH, -1)
            buildMonth()
        }
        findViewById<TextView>(R.id.ptNext).setOnClickListener {
            if (cursor.get(Calendar.YEAR) < today.get(Calendar.YEAR) ||
                (cursor.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cursor.get(Calendar.MONTH) < today.get(Calendar.MONTH))
            ) {
                cursor.add(Calendar.MONTH, 1)
                buildMonth()
            }
        }
    }

    // ---- data ----

    private fun keyFor(cal: Calendar): String = KEY_TRACK_PREFIX + dateFmt.format(cal.time)

    private fun loadSet(key: String): MutableSet<Int> =
        prefs.getStringSet(key, emptySet())!!
            .mapNotNull { it.toIntOrNull() }
            .toMutableSet()

    private fun save(key: String, set: Set<Int>) {
        prefs.edit().putStringSet(key, set.map { it.toString() }.toSet()).apply()
    }

    private fun todaySet(): MutableSet<Int> = loadSet(keyFor(today))

    private fun kaza(i: Int): Int = prefs.getInt(KEY_KAZA_PREFIX + i, 0)

    private fun setKaza(i: Int, value: Int) {
        prefs.edit().putInt(KEY_KAZA_PREFIX + i, value.coerceAtLeast(0)).apply()
    }

    private fun computeTimes() {
        val la = prefs.getString(KEY_LAT, null)?.toDoubleOrNull()
        val lo = prefs.getString(KEY_LON, null)?.toDoubleOrNull()
        if (la == null || lo == null) {
            times = null
            noLoc.visibility = View.VISIBLE
            return
        }
        noLoc.visibility = View.GONE
        val tz = today.timeZone.getOffset(today.timeInMillis) / 3_600_000.0
        val t = PrayerTimes.compute(
            today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH), la, lo, tz, PrayerMethod.DIYANET
        )
        times = doubleArrayOf(t.fajr, t.dhuhr, t.asr, t.maghrib, t.isha)
    }

    private fun nowDecimalHour(): Double {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0 +
                c.get(Calendar.SECOND) / 3600.0
    }

    private fun isAfterToday(cal: Calendar): Boolean {
        val a = cal.get(Calendar.YEAR) * 10000 +
                (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
        val b = today.get(Calendar.YEAR) * 10000 +
                (today.get(Calendar.MONTH) + 1) * 100 + today.get(Calendar.DAY_OF_MONTH)
        return a > b
    }

    // ---- header ----

    private fun refreshHeader() {
        hijriText.text = hijriText(Date())
        gregText.text = SimpleDateFormat("d MMMM yyyy, EEEE", tr).format(Date())

        val streak = computeStreak()
        streakText.text = if (streak > 0) getString(R.string.pt_streak, streak)
        else getString(R.string.pt_streak_none)
        val done = todaySet().size
        progressText.text = getString(R.string.pt_progress, done, DAYS)
    }

    private fun hijriText(date: Date): String {
        val ic = android.icu.util.IslamicCalendar()
        ic.time = date
        val y = ic.get(android.icu.util.IslamicCalendar.YEAR)
        val m = ic.get(android.icu.util.IslamicCalendar.MONTH)
        val d = ic.get(android.icu.util.IslamicCalendar.DAY_OF_MONTH)
        return "$d ${HIJRI_MONTHS.getOrElse(m) { "" }} $y"
    }

    private fun computeStreak(): Int {
        val cal = Calendar.getInstance()
        if (todaySet().size < DAYS) cal.add(Calendar.DAY_OF_MONTH, -1)
        var streak = 0
        while (streak < 3650) {
            if (loadSet(keyFor(cal)).size >= DAYS) {
                streak++
                cal.add(Calendar.DAY_OF_MONTH, -1)
            } else break
        }
        return streak
    }

    // ---- today ----

    private fun buildToday() {
        todayList.removeAllViews()
        val done = todaySet()
        val accent = getColor(R.color.accent)
        val dim = getColor(R.color.text_dim)
        val primary = getColor(R.color.text_primary)
        for (i in 0 until DAYS) {
            val isDone = done.contains(i)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isClickable = true
                isFocusable = true
                if (isDone) setBackgroundResource(R.drawable.vakit_active_bg)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                setImageResource(ICONS[i])
                setColorFilter(if (isDone) accent else dim)
            }
            val name = TextView(this).apply {
                text = getString(LABELS[i])
                textSize = 16f
                setTextColor(if (isDone) accent else primary)
                typeface = Typeface.create(Typeface.DEFAULT, if (isDone) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginStart = dp(14) }
            }
            val time = TextView(this).apply {
                val t = times
                text = if (t != null) PrayerTimes.format(t[i]) else "--:--"
                textSize = 15f
                setTextColor(if (isDone) accent else dim)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val check = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(12) }
                setImageResource(R.drawable.ic_check)
                if (isDone) {
                    setColorFilter(accent)
                    alpha = 1f
                } else {
                    setColorFilter(dim)
                    alpha = 0.35f
                }
            }
            row.addView(icon)
            row.addView(name)
            row.addView(time)
            row.addView(check)
            row.setOnClickListener { toggleToday(i) }
            todayList.addView(row)
        }
    }

    private fun toggleToday(i: Int) {
        val set = todaySet()
        if (!set.add(i)) set.remove(i)
        save(keyFor(today), set)
        buildToday()
        refreshHeader()
        buildMonth()
    }

    private fun resetToday() {
        if (todaySet().isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.pt_reset_today) + "?")
            .setPositiveButton(R.string.pt_yes) { _, _ ->
                save(keyFor(today), emptySet())
                buildToday(); refreshHeader(); buildMonth()
            }
            .setNegativeButton(R.string.pt_cancel, null)
            .show()
    }

    private fun addMissed() {
        val t = times
        if (t == null) {
            Toast.makeText(this, getString(R.string.pt_no_location), Toast.LENGTH_SHORT).show()
            return
        }
        if (prefs.getBoolean(KEY_ADDED_PREFIX + dateFmt.format(today.time), false)) {
            Toast.makeText(this, getString(R.string.pt_nothing_missed), Toast.LENGTH_SHORT).show()
            return
        }
        val done = todaySet()
        val now = nowDecimalHour()
        var added = 0
        for (i in 0 until DAYS) {
            if (!done.contains(i) && t[i] <= now) {
                setKaza(i, kaza(i) + 1)
                added++
            }
        }
        prefs.edit().putBoolean(KEY_ADDED_PREFIX + dateFmt.format(today.time), true).apply()
        buildKaza()
        Toast.makeText(
            this,
            if (added > 0) getString(R.string.pt_added) else getString(R.string.pt_nothing_missed),
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---- kaza ----

    private fun buildKaza() {
        kazaList.removeAllViews()
        val accent = getColor(R.color.accent)
        val dim = getColor(R.color.text_dim)
        val primary = getColor(R.color.text_primary)
        var total = 0
        for (i in 0 until DAYS) {
            val count = kaza(i)
            total += count
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                setImageResource(ICONS[i])
                setColorFilter(dim)
            }
            val name = TextView(this).apply {
                text = getString(LABELS[i])
                textSize = 15f
                setTextColor(primary)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginStart = dp(12) }
            }
            val minus = stepper("−", dim) { setKaza(i, kaza(i) - 1); buildKaza(); refreshHeader() }
            val value = TextView(this).apply {
                text = count.toString()
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(if (count > 0) accent else dim)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val plus = stepper("+", accent) { setKaza(i, kaza(i) + 1); buildKaza(); refreshHeader() }
            row.addView(icon)
            row.addView(name)
            row.addView(minus)
            row.addView(value)
            row.addView(plus)
            kazaList.addView(row)
        }
        kazaDone.visibility = if (total == 0) View.VISIBLE else View.GONE
    }

    private fun stepper(label: String, color: Int, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(color)
            background = getDrawable(R.drawable.chip_bg)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener { onClick() }
        }

    private fun confirmKazaReset() {
        AlertDialog.Builder(this)
            .setMessage(R.string.pt_kaza_reset_msg)
            .setPositiveButton(R.string.pt_yes) { _, _ ->
                for (i in 0 until DAYS) setKaza(i, 0)
                buildKaza()
                refreshHeader()
            }
            .setNegativeButton(R.string.pt_cancel, null)
            .show()
    }

    // ---- month ----

    private fun buildWeekHeader() {
        weekHeader.removeAllViews()
        val dim = getColor(R.color.text_dim)
        for (i in 0 until 7) {
            val tv = TextView(this).apply {
                text = WEEK_HEAD[i]
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(dim)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            weekHeader.addView(tv)
        }
    }

    private fun buildMonth() {
        monthName.text = SimpleDateFormat("MMMM yyyy", tr).format(cursor.time)
        monthGrid.removeAllViews()

        val first = cursor.clone() as Calendar
        first.set(Calendar.DAY_OF_MONTH, 1)
        val offset = (first.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        val lastDay = cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cellCount = offset + lastDay
        val weeks = (cellCount + 6) / 7

        var day = 1
        for (w in 0 until weeks) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            for (col in 0 until 7) {
                val pos = w * 7 + col
                if (pos < offset || day > lastDay) {
                    row.addView(spacer())
                } else {
                    row.addView(dayCell(day))
                    day++
                }
            }
            monthGrid.addView(row)
        }
        updateMonthSummary()
    }

    private fun spacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
    }

    private fun dayCell(day: Int): View {
        val cal = (cursor.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
        val done = loadSet(keyFor(cal)).size
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                cal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
        val isFuture = isAfterToday(cal)
        val accent = getColor(R.color.accent)
        val dim = getColor(R.color.text_dim)

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }
        val num = TextView(this).apply {
            text = day.toString()
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, if (isToday) Typeface.BOLD else Typeface.NORMAL)
        }
        val sub = TextView(this).apply {
            textSize = 9f
            gravity = Gravity.CENTER
        }

        when {
            isFuture -> {
                num.setTextColor(dim)
                num.alpha = 0.4f
                sub.visibility = View.GONE
            }
            done >= DAYS -> {
                cell.setBackgroundResource(R.drawable.chip_bg_selected)
                num.setTextColor(getColor(android.R.color.white))
                sub.setTextColor(getColor(android.R.color.white))
                sub.text = "$done/$DAYS"
            }
            done > 0 -> {
                cell.setBackgroundResource(R.drawable.vakit_active_bg)
                num.setTextColor(accent)
                sub.setTextColor(dim)
                sub.text = "$done/$DAYS"
            }
            else -> {
                num.setTextColor(dim)
                sub.visibility = View.GONE
            }
        }
        cell.addView(num)
        cell.addView(sub)

        if (!isFuture) {
            cell.isClickable = true
            cell.isFocusable = true
            cell.setOnClickListener { editDay(cal) }
        }
        return cell
    }

    private fun updateMonthSummary() {
        val lastDay = cursor.getActualMaximum(Calendar.DAY_OF_MONTH)
        var totalDone = 0
        var completeDays = 0
        var elapsed = 0
        for (day in 1..lastDay) {
            val cal = (cursor.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
            if (isAfterToday(cal)) break
            elapsed++
            val done = loadSet(keyFor(cal)).size
            totalDone += done
            if (done >= DAYS) completeDays++
        }
        if (elapsed == 0) {
            monthPrayers.text = getString(R.string.pt_no_data)
            monthDays.text = ""
        } else {
            monthPrayers.text = getString(R.string.pt_month_prayers, totalDone, elapsed * DAYS)
            monthDays.text = getString(R.string.pt_month_days, completeDays)
        }
    }

    private fun editDay(cal: Calendar) {
        val key = keyFor(cal)
        val set = loadSet(key)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val rows = ArrayList<TextView>()
        val accent = getColor(R.color.accent)
        val dim = getColor(R.color.text_dim)
        val primary = getColor(R.color.text_primary)

        fun refreshRows() {
            for (i in rows.indices) {
                val on = set.contains(i)
                rows[i].setTextColor(if (on) accent else primary)
                rows[i].typeface = Typeface.create(Typeface.DEFAULT, if (on) Typeface.BOLD else Typeface.NORMAL)
                rows[i].text = (if (on) "✓  " else "○  ") + getString(LABELS[i])
            }
        }

        for (i in 0 until DAYS) {
            val tv = TextView(this).apply {
                textSize = 16f
                setPadding(dp(4), dp(14), dp(4), dp(14))
                isClickable = true
                setOnClickListener {
                    if (!set.add(i)) set.remove(i)
                    refreshRows()
                }
            }
            rows.add(tv)
            content.addView(tv)
        }
        refreshRows()

        val divider = View(this).apply {
            setBackgroundColor(getColor(R.color.panel_stroke))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(8) }
        }
        content.addView(divider)
        val clear = TextView(this).apply {
            text = getString(R.string.pt_clear_day)
            textSize = 13f
            setTextColor(dim)
            setPadding(dp(4), dp(12), dp(4), dp(12))
            isClickable = true
        }
        content.addView(clear)

        val dialog = AlertDialog.Builder(this)
            .setTitle(
                SimpleDateFormat("d MMMM, EEEE", tr).format(cal.time) + " · " +
                        getString(R.string.pt_day_edit_title)
            )
            .setView(content)
            .setPositiveButton(R.string.pt_close, null)
            .create()

        clear.setOnClickListener {
            set.clear()
            refreshRows()
        }

        dialog.setOnDismissListener {
            save(key, set)
            if (sameDay(cal, today)) buildToday()
            buildMonth()
            refreshHeader()
        }
        dialog.show()
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.MONTH) == b.get(Calendar.MONTH) &&
                a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
