package com.wendigo.qibla

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.icu.util.IslamicCalendar
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private enum class LocationSource { DEVICE, MANUAL }

    private lateinit var compass: CompassView
    private lateinit var bgImage: ImageView
    private lateinit var bgScrim: View
    private lateinit var tvInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCity: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvNextLine: TextView
    private lateinit var prayerRow: LinearLayout

    private lateinit var pageQibla: View
    private lateinit var pageVakit: View
    private lateinit var pageAyarlar: View
    private lateinit var pageQuran: View
    private lateinit var quranList: ListView
    private lateinit var quranProgress: ProgressBar
    private lateinit var quranEmpty: TextView
    private lateinit var surahSearch: EditText
    private lateinit var recentRow: View
    private lateinit var recentChips: LinearLayout
    private lateinit var quranResume: View
    private lateinit var quranResumeText: TextView
    private lateinit var etFetvaQuery: EditText
    private lateinit var fetvaStatus: TextView
    private lateinit var btnFetvaWeb: Button
    private lateinit var fetvaChips: LinearLayout
    private lateinit var fetvaList: RecyclerView
    private val fetvaAdapter = FetvaAdapter()
    private var fetvaAll: List<FetvaRepository.Fetva> = emptyList()
    private var fetvaSelectedCat: String? = null
    private var fetvaLoaded = false
    private lateinit var toolsList: RecyclerView
    private val favSurahs = HashSet<Int>()
    private val quranSurahs = ArrayList<QuranRepository.Surah>()
    private val allSurahs = ArrayList<QuranRepository.Surah>()
    private lateinit var quranAdapter: QuranAdapter
    private var quranRequested = false
    private var quranAllLoaded = false
    private val loadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var vCity: TextView
    private lateinit var vCount: TextView
    private lateinit var vGregDate: TextView
    private lateinit var vHijriDate: TextView
    private lateinit var vHeroName: TextView
    private lateinit var vHeroTime: TextView
    private lateinit var vProgress: ProgressBar
    private lateinit var vList: LinearLayout
    private lateinit var switchNotif: SwitchCompat
    private lateinit var switchCountdown: SwitchCompat
    private val vNameViews = ArrayList<TextView>(6)
    private val vTimeViews = ArrayList<TextView>(6)
    private val vIconViews = ArrayList<ImageView>(6)
    private val vRowViews = ArrayList<LinearLayout>(6)
    private var currentTab = 0
    private val tickHandler = Handler(Looper.getMainLooper())
    private val vakitTicker = object : Runnable {
        override fun run() {
            if (currentTab == 0) {
                updateVakitPage()
                tickHandler.postDelayed(this, 1000L)
            }
        }
    }
    private val vakitIcons = intArrayOf(
        R.drawable.namaz_imsak, R.drawable.namaz_gunes, R.drawable.namaz_ogle,
        R.drawable.namaz_ikindi, R.drawable.namaz_aksam, R.drawable.namaz_yatsi
    )
    private var suppressNotifListener = false

    private val prayerNameViews = ArrayList<TextView>(6)
    private val prayerTimeViews = ArrayList<TextView>(6)
    private val prayerLabelRes = intArrayOf(
        R.string.p_fajr, R.string.p_sunrise, R.string.p_dhuhr,
        R.string.p_asr, R.string.p_maghrib, R.string.p_isha
    )
    private val prayerRowIndex = mapOf(
        R.string.p_fajr to 0, R.string.p_dhuhr to 2, R.string.p_asr to 3,
        R.string.p_maghrib to 4, R.string.p_isha to 5
    )

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private var useRotationVector = false

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMag = false

    private var azimuth = 0f
    private var firstSample = true
    private var declination = 0f
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var wasAligned = false
    private var noSensor = false
    private var manualOverride = false

    private var lat: Double? = null
    private var lon: Double? = null
    private var qiblaBearing = 0.0
    private var distanceKm = 0.0

    private var prayerToday: PrayerTimes.Times? = null
    private var prayerTomorrow: PrayerTimes.Times? = null
    private var qiblaSun: Double? = null
    private val method = PrayerMethod.DIYANET

    private var currentCityName: String? = null
    private var geocodedKey: String? = null
    private val geocoderExecutor = Executors.newSingleThreadExecutor()

    private var locationManager: LocationManager? = null

    private var lastInfo = ""
    private var lastStatus = ""

    private var soundPreview: MediaPlayer? = null
    private var pendingSoundMinutes = 0

    companion object {
        private const val REQ_LOCATION = 1001
        private const val REQ_NOTIF = 1002
        private const val REQ_NOTIF_COUNTDOWN = 1003
        private const val REQ_PICK_SOUND = 1004
        private const val ALIGN_TOLERANCE = 5f
        private const val PREFS = "qibla_prefs"
        private const val KEY_RECENT_SURAHS = "recent_surahs"
        private const val KEY_FAV_SURAHS = "fav_surahs"
        private const val MAX_RECENT_SURAHS = 6
        private const val RECENT_SEP = "\u0001"
        private const val KEY_DEVICE_LAT = "device_lat"
        private const val KEY_DEVICE_LON = "device_lon"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BG = "bg_style"
        private const val STATE_LAT = "state_lat"
        private const val STATE_LON = "state_lon"
        private const val STATE_MANUAL = "state_manual"
        private const val STATE_CITY = "state_city"
        private const val VAKIT_FUTURE = 0
        private const val VAKIT_PAST = 1
        private const val VAKIT_ACTIVE = 2
        private const val VAKIT_SUN = 3
        private val HIJRI_MONTHS = arrayOf(
            "Muharrem", "Safer", "Rebîulevvel", "Rebîulâhir",
            "Cemâziyelevvel", "Cemâziyelâhir", "Recep", "Şaban",
            "Ramazan", "Şevval", "Zilkade", "Zilhicce"
        )

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        bgImage = findViewById(R.id.bgImage)
        bgScrim = findViewById(R.id.bgScrim)
        applyBackground()

        compass = findViewById(R.id.compass)
        tvInfo = findViewById(R.id.tvInfo)
        tvStatus = findViewById(R.id.tvStatus)
        tvCity = findViewById(R.id.tvCity)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvNextLine = findViewById(R.id.tvNextLine)
        prayerRow = findViewById(R.id.prayerRow)
        buildPrayerRow()

        pageQibla = findViewById(R.id.pageQibla)
        pageVakit = findViewById(R.id.pageVakit)
        pageAyarlar = findViewById(R.id.pageAyarlar)
        vCity = findViewById(R.id.vCity)
        vCount = findViewById(R.id.vCount)
        vGregDate = findViewById(R.id.vGregDate)
        vHijriDate = findViewById(R.id.vHijriDate)
        vHeroName = findViewById(R.id.vHeroName)
        vHeroTime = findViewById(R.id.vHeroTime)
        vProgress = findViewById(R.id.vProgress)
        vList = findViewById(R.id.vList)
        switchNotif = findViewById(R.id.switchNotif)
        buildVakitList()
        setupBottomBar()
        setupSettingsPage()
        setupIbadahPage()
        setupFetvaPage()
        setupToolsPage()
        setupNotifications()

        pageQuran = findViewById(R.id.pageQuran)
        quranList = findViewById(R.id.quranList)
        quranProgress = findViewById(R.id.quranProgress)
        quranEmpty = findViewById(R.id.quranEmpty)
        quranAdapter = QuranAdapter()
        quranList.adapter = quranAdapter
        quranList.setOnItemClickListener { _, _, position, _ ->
            val s = quranSurahs.getOrNull(position) ?: return@setOnItemClickListener
            val query = surahSearch.text.toString().trim()
            if (query.isNotEmpty()) recordRecent(query)
            startActivity(
                Intent(this, SurahActivity::class.java)
                    .putExtra(SurahActivity.EXTRA_SURAH, s.number - 1)
            )
        }
        surahSearch = findViewById(R.id.etSurahSearch)
        recentRow = findViewById(R.id.recentRow)
        recentChips = findViewById(R.id.recentChips)
        quranResume = findViewById(R.id.quranResume)
        quranResumeText = findViewById(R.id.quranResumeText)
        quranResume.setOnClickListener { openResume() }
        loadFavs()
        refreshRecentChips()
        refreshQuranResume()
        surahSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = applySurahFilter()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        surahSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                recordRecent(surahSearch.text.toString())
                true
            } else false
        }
        surahSearch.setOnFocusChangeListener { _, _ -> updateRecentVisibility() }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        useRotationVector = rotationSensor != null

        if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
            noSensor = true
            setStatus(getString(R.string.no_sensor))
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        findViewById<Button>(R.id.btnCity).setOnClickListener { showCityDialog() }
        findViewById<Button>(R.id.btnManual).setOnClickListener { showManualDialog() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            manualOverride = false
            ensurePermissionAndLocate()
        }
        findViewById<View>(R.id.vakitGunun).setOnClickListener {
            startActivity(Intent(this, GununActivity::class.java))
        }

        if (!restoreState(savedInstanceState)) {
            loadDeviceLocation()?.let { applyLocation(it.first, it.second, 0.0, LocationSource.DEVICE) }
        }

        ensurePermissionAndLocate()
        updateUi()
    }

    private fun restoreState(state: Bundle?): Boolean {
        if (state == null || !state.containsKey(STATE_LAT)) return false
        val la = state.getDouble(STATE_LAT)
        val lo = state.getDouble(STATE_LON)
        val manual = state.getBoolean(STATE_MANUAL, false)
        val city = state.getString(STATE_CITY)
        if (city != null) {
            currentCityName = city
            geocodedKey = coarseKey(la, lo)
        }
        applyLocation(
            la, lo, 0.0,
            if (manual) LocationSource.MANUAL else LocationSource.DEVICE,
            city
        )
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_MANUAL, manualOverride)
        outState.putString(STATE_CITY, currentCityName)
        val la = lat
        val lo = lon
        if (la != null && lo != null) {
            outState.putDouble(STATE_LAT, la)
            outState.putDouble(STATE_LON, lo)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickHandler.removeCallbacks(vakitTicker)
        geocoderExecutor.shutdownNow()
        loadExecutor.shutdownNow()
    }

    // ---- prayer panel ----

    private fun buildPrayerRow() {
        val dim = getColor(R.color.text_dim)
        val primary = getColor(R.color.text_primary)
        for (labelRes in prayerLabelRes) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            val name = TextView(this).apply {
                text = getString(labelRes)
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(dim)
            }
            val time = TextView(this).apply {
                text = "--:--"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(primary)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            cell.addView(name)
            cell.addView(time)
            prayerRow.addView(cell)
            prayerNameViews.add(name)
            prayerTimeViews.add(time)
        }
    }

    private fun updatePrayerPanel() {
        val la = lat
        val lo = lon
        tvCity.text = currentCityName
            ?: if (la != null && lo != null) String.format(Locale.US, "%.3f, %.3f", la, lo)
            else getString(R.string.loc_unknown)

        val t = prayerToday
        if (t == null) {
            tvNextLine.text = ""
            tvCountdown.text = ""
            return
        }

        val values = doubleArrayOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
        for (i in values.indices) prayerTimeViews[i].text = PrayerTimes.format(values[i])

        val next = nextPrayerResAndTime()
        val accent = getColor(R.color.accent)
        val dim = getColor(R.color.text_dim)
        val primary = getColor(R.color.text_primary)

        if (next == null) {
            tvNextLine.text = ""
            tvCountdown.text = ""
            for (i in values.indices) {
                prayerTimeViews[i].setTextColor(primary)
                prayerNameViews[i].setTextColor(dim)
            }
            return
        }

        val highlight = prayerRowIndex[next.first] ?: -1
        for (i in values.indices) {
            val on = i == highlight
            prayerTimeViews[i].setTextColor(if (on) accent else primary)
            prayerNameViews[i].setTextColor(if (on) accent else dim)
        }

        tvNextLine.text = getString(
            R.string.next_only, getString(next.first), PrayerTimes.format(next.second)
        )
        tvCountdown.text = countdownText(next.second - nowDecimalHour())
    }

    // ---- vakitler page ----

    private fun buildVakitList() {
        for ((i, labelRes) in prayerLabelRes.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                setImageResource(vakitIcons[i])
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val name = TextView(this).apply {
                text = getString(labelRes)
                textSize = 16f
                setTextColor(getColor(R.color.text_dim))
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginStart = dp(14) }
            }
            val time = TextView(this).apply {
                text = "--:--"
                textSize = 18f
                setTextColor(getColor(R.color.text_primary))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            row.addView(icon)
            row.addView(name)
            row.addView(time)
            vList.addView(row)
            vIconViews.add(icon)
            vNameViews.add(name)
            vTimeViews.add(time)
            vRowViews.add(row)
        }
    }

    private fun styleVakitRow(i: Int, state: Int, accent: Int, dim: Int, primary: Int) {
        val row = vRowViews[i]
        val name = vNameViews[i]
        val time = vTimeViews[i]
        val icon = vIconViews[i]
        when (state) {
            VAKIT_ACTIVE -> {
                row.setBackgroundResource(R.drawable.vakit_active_bg)
                name.setTextColor(accent)
                name.setTypeface(null, Typeface.BOLD)
                time.setTextColor(accent)
                icon.setColorFilter(accent)
            }
            VAKIT_PAST -> {
                row.background = null
                name.setTextColor(dim)
                name.setTypeface(null, Typeface.NORMAL)
                time.setTextColor(dim)
                icon.setColorFilter(dim)
            }
            VAKIT_SUN -> {
                row.background = null
                name.setTextColor(dim)
                name.setTypeface(null, Typeface.ITALIC)
                time.setTextColor(dim)
                icon.setColorFilter(dim)
            }
            else -> {
                row.background = null
                name.setTextColor(primary)
                name.setTypeface(null, Typeface.NORMAL)
                time.setTextColor(primary)
                icon.setColorFilter(dim)
            }
        }
    }

    private fun updateVakitPage() {
        val la = lat
        val lo = lon
        vCity.text = currentCityName
            ?: if (la != null && lo != null) String.format(Locale.US, "%.3f, %.3f", la, lo)
            else getString(R.string.loc_unknown)
        vGregDate.text = gregTodayText()
        vHijriDate.text = hijriTodayText()

        val t = prayerToday
        if (t == null) {
            vHeroName.text = ""
            vHeroTime.text = ""
            vCount.text = ""
            vProgress.progress = 0
            return
        }
        val values = doubleArrayOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
        val accent = getColor(R.color.accent)
        val dim = getColor(R.color.text_dim)
        val primary = getColor(R.color.text_primary)
        for (i in values.indices) vTimeViews[i].text = PrayerTimes.format(values[i])

        val next = nextPrayerResAndTime()
        if (next == null) {
            vHeroName.text = ""
            vHeroTime.text = ""
            vCount.text = ""
            vProgress.progress = 0
            for (i in values.indices) styleVakitRow(i, VAKIT_PAST, accent, dim, primary)
            return
        }
        val now = nowDecimalHour()
        vHeroName.text = getString(next.first)
        vHeroTime.text = PrayerTimes.format(next.second)
        vCount.text = countdownHms(next.second - now)
        vProgress.progress = (prayerProgress(now) * 1000).roundToInt().coerceIn(0, 1000)

        val highlight = prayerRowIndex[next.first] ?: -1
        for (i in values.indices) {
            val state = when {
                i == 1 -> VAKIT_SUN
                i == highlight -> VAKIT_ACTIVE
                values[i] < now -> VAKIT_PAST
                else -> VAKIT_FUTURE
            }
            styleVakitRow(i, state, accent, dim, primary)
        }
    }

    private fun prayerProgress(now: Double): Double {
        val t = prayerToday ?: return 0.0
        val times = doubleArrayOf(t.fajr, t.dhuhr, t.asr, t.maghrib, t.isha)
        var nextIdx = -1
        for (i in times.indices) if (times[i] > now) { nextIdx = i; break }
        val start: Double
        val end: Double
        if (nextIdx == -1) {
            start = times.last()
            end = times[0] + 24.0
        } else {
            start = if (nextIdx == 0) times.last() - 24.0 else times[nextIdx - 1]
            end = times[nextIdx]
        }
        if (end <= start) return 0.0
        return ((now - start) / (end - start)).coerceIn(0.0, 1.0)
    }

    private fun countdownHms(hours: Double): String {
        val total = (hours * 3600).roundToInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    private fun gregTodayText(): String =
        SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr", "TR")).format(Date())

    private fun hijriTodayText(): String {
        val ic = IslamicCalendar()
        ic.time = Date()
        val y = ic.get(IslamicCalendar.YEAR)
        val m = ic.get(IslamicCalendar.MONTH)
        val d = ic.get(IslamicCalendar.DAY_OF_MONTH)
        return "$d ${HIJRI_MONTHS.getOrElse(m) { "" }} $y"
    }

    // ---- bottom navigation ----

    private fun setupBottomBar() {
        findViewById<View>(R.id.tabPrayer).setOnClickListener { selectTab(0) }
        findViewById<View>(R.id.tabQuran).setOnClickListener { selectTab(1) }
        findViewById<View>(R.id.tabIbadah).setOnClickListener { selectTab(2) }
        findViewById<View>(R.id.tabFetva).setOnClickListener { selectTab(3) }
        findViewById<View>(R.id.tabQibla).setOnClickListener { selectTab(4) }
        findViewById<View>(R.id.tabTools).setOnClickListener { selectTab(5) }
        findViewById<View>(R.id.tabSettings).setOnClickListener { selectTab(6) }
        selectTab(0)
    }

    private fun setupIbadahPage() {
        findViewById<View>(R.id.ibHadis).setOnClickListener {
            startActivity(Intent(this, HadisActivity::class.java))
        }
        findViewById<View>(R.id.ibDua).setOnClickListener {
            startActivity(Intent(this, DuaActivity::class.java))
        }
        findViewById<View>(R.id.ibEsma).setOnClickListener {
            startActivity(Intent(this, EsmaActivity::class.java))
        }
        findViewById<View>(R.id.ibZikir).setOnClickListener {
            startActivity(Intent(this, TesbihActivity::class.java))
        }
        findViewById<View>(R.id.ibNamaz).setOnClickListener {
            startActivity(Intent(this, NamazRehberActivity::class.java))
        }
        findViewById<View>(R.id.ibAbdest).setOnClickListener {
            startActivity(Intent(this, AbdestRehberActivity::class.java))
        }
        findViewById<View>(R.id.ibTakip).setOnClickListener {
            startActivity(Intent(this, PrayerTrackerActivity::class.java))
        }
    }

    private fun setupFetvaPage() {
        etFetvaQuery = findViewById(R.id.etFetvaQuery)
        fetvaStatus = findViewById(R.id.fetvaStatus)
        btnFetvaWeb = findViewById(R.id.btnFetvaWeb)
        fetvaChips = findViewById(R.id.fetvaChips)
        fetvaList = findViewById(R.id.fetvaList)
        fetvaList.layoutManager = LinearLayoutManager(this)
        fetvaList.adapter = fetvaAdapter
        findViewById<Button>(R.id.btnFetvaSearch).setOnClickListener { performFetvaSearch() }
        btnFetvaWeb.setOnClickListener { openFetvaWeb() }
        etFetvaQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performFetvaSearch()
                true
            } else false
        }
        etFetvaQuery.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = performFetvaSearch()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun ensureFetvaLoaded() {
        if (fetvaLoaded) return
        fetvaLoaded = true
        loadExecutor.execute {
            val loaded = try {
                FetvaRepository.load(applicationContext)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                fetvaAll = loaded
                buildFetvaChips(loaded.map { it.kategori }.distinct())
                performFetvaSearch()
            }
        }
    }

    private fun buildFetvaChips(categories: List<String>) {
        fetvaChips.removeAllViews()
        addFetvaChip(getString(R.string.fetva_all), null)
        for (c in categories) addFetvaChip(c, c)
    }

    private fun addFetvaChip(label: String, value: String?) {
        val tv = TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            tag = value
            setOnClickListener {
                fetvaSelectedCat = value
                refreshFetvaChips()
                performFetvaSearch()
            }
        }
        fetvaChips.addView(tv)
    }

    private fun refreshFetvaChips() {
        for (i in 0 until fetvaChips.childCount) {
            val tv = fetvaChips.getChildAt(i) as TextView
            val sel = (tv.tag as String?) == fetvaSelectedCat
            tv.setBackgroundResource(if (sel) R.drawable.chip_bg_selected else R.drawable.chip_bg)
            tv.setTextColor(getColor(if (sel) android.R.color.white else R.color.text_primary))
        }
    }

    private fun performFetvaSearch() {
        val q = etFetvaQuery.text.toString()
        var res = FetvaRepository.search(applicationContext, q)
        val cat = fetvaSelectedCat
        if (cat != null) res = res.filter { it.kategori == cat }
        fetvaAdapter.setItems(res)
        fetvaStatus.text = when {
            q.isBlank() && cat == null -> getString(R.string.fetva_sub)
            res.isEmpty() -> getString(R.string.fetva_none)
            else -> getString(R.string.fetva_count, res.size)
        }
        btnFetvaWeb.visibility = if (q.isBlank()) View.GONE else View.VISIBLE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun openFetvaWeb() {
        val q = etFetvaQuery.text.toString().trim()
        if (q.isBlank()) {
            Toast.makeText(this, getString(R.string.fetva_web_query), Toast.LENGTH_SHORT).show()
            return
        }
        if (!isOnline()) {
            Toast.makeText(this, getString(R.string.fetva_no_internet), Toast.LENGTH_LONG).show()
            return
        }
        val options = arrayOf(
            getString(R.string.fetva_web_general),
            getString(R.string.fetva_web_diyanet),
            getString(R.string.fetva_web_youtube)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.fetva_web_title)
            .setItems(options) { _, which ->
                val url = when (which) {
                    1 -> googleUrl("$q site:diyanet.gov.tr")
                    2 -> "https://www.youtube.com/results?search_query=" +
                        java.net.URLEncoder.encode(q, "UTF-8")
                    else -> googleUrl(q)
                }
                openUrl(url)
            }
            .show()
    }

    private fun googleUrl(query: String): String =
        "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.fetva_no_browser), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFetva(f: FetvaRepository.Fetva) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        col.addView(TextView(this).apply {
            text = f.soru
            textSize = 16f
            setTextColor(getColor(R.color.accent))
            setPadding(0, 0, 0, pad / 2)
        })
        if (f.mezhep.isNotBlank()) {
            col.addView(TextView(this).apply {
                text = getString(R.string.fetva_mezhep) + ": " + f.mezhep
                textSize = 12f
                setTextColor(getColor(R.color.text_dim))
                setPadding(0, 0, 0, pad / 3)
            })
        }
        col.addView(TextView(this).apply {
            text = f.cevap
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setLineSpacing(5f, 1f)
            setPadding(0, 0, 0, pad / 2)
        })
        col.addView(TextView(this).apply {
            text = f.kaynak
            textSize = 12f
            setTextColor(getColor(R.color.text_dim))
            gravity = Gravity.END
        })
        val scroll = ScrollView(this).apply { addView(col) }
        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class FetvaAdapter : RecyclerView.Adapter<FetvaAdapter.VH>() {
        private val items = ArrayList<FetvaRepository.Fetva>()

        fun setItems(list: List<FetvaRepository.Fetva>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val question: TextView = v.findViewById(R.id.fetvaQuestion)
            val tag: TextView = v.findViewById(R.id.fetvaTag)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_fetva, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            holder.question.text = f.soru
            holder.tag.text = if (f.mezhep.isBlank()) f.kategori else f.kategori + " • " + f.mezhep
            holder.itemView.setOnClickListener { showFetva(f) }
        }
    }

    private data class ToolItem(
        val titleRes: Int,
        val subRes: Int,
        val iconRes: Int,
        val target: Class<*>
    )

    private val tools = listOf(
        ToolItem(
            R.string.tool_hijri_title, R.string.tool_hijri_sub,
            R.drawable.ic_calendar, HijriActivity::class.java
        ),
        ToolItem(
            R.string.tool_zakat_title, R.string.tool_zakat_sub,
            R.drawable.ic_zakat, ZakatActivity::class.java
        ),
        ToolItem(
            R.string.tool_mescit_title, R.string.tool_mescit_sub,
            R.drawable.ic_place, MescitActivity::class.java
        )
    )

    private fun setupToolsPage() {
        toolsList = findViewById(R.id.pageTools)
        toolsList.layoutManager = LinearLayoutManager(this)
        toolsList.adapter = ToolsAdapter()
    }

    private inner class ToolsAdapter : RecyclerView.Adapter<ToolsAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.toolIcon)
            val title: TextView = v.findViewById(R.id.toolTitle)
            val sub: TextView = v.findViewById(R.id.toolSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false))

        override fun getItemCount() = tools.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = tools[position]
            holder.icon.setImageResource(t.iconRes)
            holder.title.setText(t.titleRes)
            holder.sub.setText(t.subRes)
            holder.itemView.setOnClickListener {
                startActivity(Intent(this@MainActivity, t.target))
            }
        }
    }

    private fun selectTab(index: Int) {
        val accent = getColor(R.color.accent)
        val dim = getColor(R.color.text_dim)
        val icons = intArrayOf(
            R.id.tabPrayerIcon, R.id.tabQuranIcon, R.id.tabIbadahIcon,
            R.id.tabFetvaIcon, R.id.tabQiblaIcon, R.id.tabToolsIcon, R.id.tabSettingsIcon
        )
        val labels = intArrayOf(
            R.id.tabPrayerLabel, R.id.tabQuranLabel, R.id.tabIbadahLabel,
            R.id.tabFetvaLabel, R.id.tabQiblaLabel, R.id.tabToolsLabel, R.id.tabSettingsLabel
        )
        val pages = intArrayOf(
            R.id.pageVakit, R.id.pageQuran, R.id.pageIbadat,
            R.id.pageFetva, R.id.pageQibla, R.id.pageTools, R.id.pageAyarlar
        )
        for (i in icons.indices) {
            val on = i == index
            findViewById<ImageView>(icons[i]).setColorFilter(if (on) accent else dim)
            findViewById<TextView>(labels[i]).setTextColor(if (on) accent else dim)
            findViewById<View>(pages[i]).visibility = if (on) View.VISIBLE else View.GONE
        }
        supportActionBar?.title = when (index) {
            0 -> getString(R.string.tab_prayer)
            1 -> getString(R.string.tab_quran)
            2 -> getString(R.string.tab_ibadah)
            3 -> getString(R.string.tab_fetva)
            4 -> getString(R.string.tab_qibla)
            5 -> getString(R.string.tab_tools)
            else -> getString(R.string.tab_settings)
        }
        currentTab = index
        if (index == 0) {
            updateVakitPage()
            startVakitTicker()
        } else {
            stopVakitTicker()
        }
        if (index == 1) ensureQuranLoaded()
        if (index == 3) ensureFetvaLoaded()
    }

    private fun startVakitTicker() {
        tickHandler.removeCallbacks(vakitTicker)
        tickHandler.postDelayed(vakitTicker, 1000L)
    }

    private fun stopVakitTicker() {
        tickHandler.removeCallbacks(vakitTicker)
    }

    private fun ensureQuranLoaded() {
        if (quranRequested) return
        quranRequested = true
        quranProgress.visibility = View.VISIBLE
        loadExecutor.execute {
            val list = try {
                QuranRepository.load(applicationContext)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                quranProgress.visibility = View.GONE
                allSurahs.clear()
                allSurahs.addAll(list)
                quranAllLoaded = true
                applySurahFilter()
            }
        }
    }

    private fun refreshQuranResume() {
        if (!::quranResume.isInitialized) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val surahIndex = prefs.getInt(SurahActivity.KEY_AUDIO_SURAH, -1)
        val ayahIndex = prefs.getInt(SurahActivity.KEY_AUDIO_AYAH, -1)
        if (surahIndex < 0 || ayahIndex < 0) {
            quranResume.visibility = View.GONE
            return
        }
        val name = allSurahs.getOrNull(surahIndex)?.name ?: "Sûre ${surahIndex + 1}"
        quranResumeText.text = getString(R.string.quran_resume_fmt, name, ayahIndex + 1)
        quranResume.visibility = View.VISIBLE
    }

    private fun openResume() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val surahIndex = prefs.getInt(SurahActivity.KEY_AUDIO_SURAH, -1)
        val ayahIndex = prefs.getInt(SurahActivity.KEY_AUDIO_AYAH, 0)
        if (surahIndex < 0) return
        startActivity(
            Intent(this, SurahActivity::class.java)
                .putExtra(SurahActivity.EXTRA_SURAH, surahIndex)
                .putExtra(SurahActivity.EXTRA_AUTOPLAY, ayahIndex)
        )
    }

    private fun applySurahFilter() {
        val q = surahSearch.text.toString().trim()
        val filtered = if (q.isBlank()) {
            val favs = allSurahs.filter { favSurahs.contains(it.number) }
            val rest = allSurahs.filterNot { favSurahs.contains(it.number) }
            favs + rest
        } else {
            allSurahs.filter { surah ->
                surah.number.toString() == q ||
                    surah.number.toString().startsWith(q) ||
                    norm(surah.name).contains(norm(q)) ||
                    surah.arName.contains(q) ||
                    norm(surah.place).contains(norm(q))
            }
        }
        quranSurahs.clear()
        quranSurahs.addAll(filtered)
        quranAdapter.notifyDataSetChanged()
        updateRecentVisibility()
        refreshQuranResume()
        if (quranAllLoaded) {
            quranEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            quranEmpty.text = if (filtered.isEmpty())
                getString(R.string.quran_no_result) else getString(R.string.quran_loading)
        }
    }

    private fun norm(s: String): String {
        val lower = s.lowercase(Locale("tr", "TR"))
        val sb = StringBuilder(lower.length)
        for (ch in lower) when (ch) {
            'ç' -> sb.append('c')
            'ğ' -> sb.append('g')
            'ı' -> sb.append('i')
            'ö' -> sb.append('o')
            'ş' -> sb.append('s')
            'ü' -> sb.append('u')
            'â' -> sb.append('a')
            'î' -> sb.append('i')
            'û' -> sb.append('u')
            else -> sb.append(ch)
        }
        return sb.toString()
    }

    // ---- recent searches ----

    private fun loadRecent(): List<String> {
        val s = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_SURAHS, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(RECENT_SEP).filter { it.isNotBlank() }
    }

    private fun saveRecent(list: List<String>) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_RECENT_SURAHS, list.joinToString(RECENT_SEP))
        }
    }

    private fun recordRecent(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val list = loadRecent().toMutableList()
        list.removeAll { it.equals(q, ignoreCase = true) }
        list.add(0, q)
        while (list.size > MAX_RECENT_SURAHS) list.removeAt(list.size - 1)
        saveRecent(list)
        refreshRecentChips()
    }

    private fun refreshRecentChips() {
        recentChips.removeAllViews()
        val items = loadRecent()
        for (q in items) {
            val chip = TextView(this).apply {
                text = q
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(160)
                setBackgroundResource(R.drawable.chip_bg)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    surahSearch.setText(q)
                    surahSearch.setSelection(q.length)
                    recordRecent(q)
                }
                setOnLongClickListener {
                    val list = loadRecent().toMutableList()
                    list.remove(q)
                    saveRecent(list)
                    refreshRecentChips()
                    true
                }
            }
            recentChips.addView(chip)
        }
        if (items.isNotEmpty()) {
            val clear = TextView(this).apply {
                text = getString(R.string.quran_recent_clear)
                setTextColor(getColor(R.color.accent))
                textSize = 13f
                setPadding(dp(12), dp(6), dp(4), dp(6))
                setOnClickListener {
                    saveRecent(emptyList())
                    refreshRecentChips()
                }
            }
            recentChips.addView(clear)
        }
        updateRecentVisibility()
    }

    private fun updateRecentVisibility() {
        val show = surahSearch.text.isNullOrBlank() && recentChips.childCount > 0
        recentRow.visibility = if (show) View.VISIBLE else View.GONE
    }

    // ---- favourites ----

    private fun loadFavs() {
        favSurahs.clear()
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FAV_SURAHS, "")?.split(',')?.forEach { part ->
                part.trim().toIntOrNull()?.let { favSurahs.add(it) }
            }
    }

    private fun saveFavs() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_FAV_SURAHS, favSurahs.sorted().joinToString(","))
        }
    }

    private fun toggleFav(number: Int) {
        val added = if (favSurahs.contains(number)) {
            favSurahs.remove(number)
            false
        } else {
            favSurahs.add(number)
            true
        }
        saveFavs()
        applySurahFilter()
        Toast.makeText(
            this,
            if (added) R.string.quran_fav_added else R.string.quran_fav_removed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private inner class QuranAdapter : BaseAdapter() {
        override fun getCount(): Int = quranSurahs.size
        override fun getItem(position: Int): Any = quranSurahs[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_surah, parent, false)
            val s = quranSurahs[position]
            v.findViewById<TextView>(R.id.surahNum).text = s.number.toString()
            v.findViewById<TextView>(R.id.surahName).text = s.name
            v.findViewById<TextView>(R.id.surahMeta).text =
                getString(R.string.surah_meta_fmt, s.ayahs.size, s.place)
            v.findViewById<TextView>(R.id.surahAr).text = s.arName
            val star = v.findViewById<TextView>(R.id.surahStar)
            val fav = favSurahs.contains(s.number)
            star.text = if (fav) "★" else "☆"
            star.setTextColor(getColor(if (fav) R.color.accent else R.color.text_dim))
            star.setOnClickListener { toggleFav(s.number) }
            return v
        }
    }

    private fun setupSettingsPage() {
        findViewById<Button>(R.id.btnThemeAyar).setOnClickListener { toggleTheme() }
        findViewById<Button>(R.id.btnBgAyar).setOnClickListener { showBackgroundDialog() }
        findViewById<Button>(R.id.btnMapAyar).setOnClickListener { openMap() }
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        findViewById<TextView>(R.id.aboutText).text = getString(R.string.about_text, version)
    }

    // ---- prayer notifications ----

    private fun setupNotifications() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        suppressNotifListener = true
        switchNotif.isChecked = p.getBoolean(PrayerScheduler.KEY_ENABLED, false)
        suppressNotifListener = false
        switchNotif.setOnCheckedChangeListener { _, checked -> onNotifToggled(checked) }
        switchCountdown = findViewById(R.id.switchCountdown)
        suppressNotifListener = true
        switchCountdown.isChecked = p.getBoolean(PrayerScheduler.KEY_COUNTDOWN, true)
        suppressNotifListener = false
        switchCountdown.setOnCheckedChangeListener { _, checked -> onCountdownToggled(checked) }
        findViewById<Button>(R.id.btnNotifTest).setOnClickListener {
            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val line = listOfNotNull(currentCityName, now)
                .map { it.trim() }.filter { it.isNotEmpty() }
                .joinToString(" · ").ifBlank { getString(R.string.app_name) }
            NotificationHelper.show(
                this,
                getString(R.string.notif_vakit_entered, getString(R.string.p_dhuhr)),
                line,
                101,
                line = line
            )
        }
        bindReminderCheck(R.id.checkRemind30, 30)
        bindReminderCheck(R.id.checkRemind15, 15)
        bindReminderCheck(R.id.checkRemind5, 5)
        findViewById<View>(R.id.remindSet30).setOnClickListener { showSoundPicker(30) }
        findViewById<View>(R.id.remindSet15).setOnClickListener { showSoundPicker(15) }
        findViewById<View>(R.id.remindSet5).setOnClickListener { showSoundPicker(5) }
    }

    private fun showSoundPicker(minutes: Int) {
        val options = arrayOf(
            getString(R.string.sound_default),
            getString(R.string.sound_silent),
            getString(R.string.sound_ezan),
            getString(R.string.sound_phone)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sound_title, minutes))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyReminderSound(minutes, null)
                    1 -> applyReminderSound(minutes, NotificationHelper.SOUND_NONE)
                    2 -> applyReminderSound(minutes, "raw:ezan")
                    3 -> openRingtonePicker(minutes)
                }
            }
            .show()
    }

    private fun applyReminderSound(minutes: Int, value: String?) {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().apply {
            val key = NotificationHelper.soundKey(minutes)
            if (value == null) remove(key) else putString(key, value)
        }.apply()
        NotificationHelper.updateReminderChannel(this, minutes)
        when (value) {
            null, NotificationHelper.SOUND_NONE -> {}
            "raw:ezan" -> previewRes(R.raw.snd_ezan)
            else -> previewSound(Uri.parse(value))
        }
        Toast.makeText(this, R.string.sound_saved, Toast.LENGTH_SHORT).show()
    }

    private fun openRingtonePicker(minutes: Int) {
        pendingSoundMinutes = minutes
        val current = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(NotificationHelper.soundKey(minutes), null)
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION or
                    RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.sound_title, minutes))
            if (current != null && current != NotificationHelper.SOUND_NONE && !current.startsWith("raw:")) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current))
            }
        }
        startActivityForResult(intent, REQ_PICK_SOUND)
    }

    private fun previewRes(resId: Int) {
        soundPreview?.let { runCatching { it.release() } }
        soundPreview = null
        val mp = try {
            MediaPlayer.create(this, resId)
        } catch (_: Exception) {
            null
        } ?: return
        mp.setOnCompletionListener {
            it.release()
            if (soundPreview === it) soundPreview = null
        }
        soundPreview = mp
        runCatching { mp.start() }
    }

    private fun previewSound(uri: Uri?) {
        soundPreview?.let { runCatching { it.release() } }
        soundPreview = null
        if (uri == null) return
        val mp = try {
            MediaPlayer.create(this, uri)
        } catch (_: Exception) {
            null
        } ?: return
        mp.setOnCompletionListener {
            it.release()
            if (soundPreview === it) soundPreview = null
        }
        soundPreview = mp
        runCatching { mp.start() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_SOUND && resultCode == RESULT_OK) {
            val uri: Uri? =
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            applyReminderSound(pendingSoundMinutes, uri?.toString())
        }
    }

    private fun bindReminderCheck(id: Int, minutes: Int) {
        val cb = findViewById<CheckBox>(id)
        val key = PrayerScheduler.KEY_REMIND_PREFIX + minutes
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        suppressNotifListener = true
        cb.isChecked = p.getBoolean(key, false)
        suppressNotifListener = false
        cb.setOnCheckedChangeListener { _, checked ->
            if (suppressNotifListener) return@setOnCheckedChangeListener
            p.edit().putBoolean(key, checked).apply()
            if (p.getBoolean(PrayerScheduler.KEY_ENABLED, false)) {
                val la = lat
                val lo = lon
                if (la != null && lo != null) PrayerScheduler.schedule(this, la, lo, currentCityName)
            }
        }
    }

    private fun onNotifToggled(enabled: Boolean) {
        if (suppressNotifListener) return
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!enabled) {
            p.edit().putBoolean(PrayerScheduler.KEY_ENABLED, false).apply()
            PrayerScheduler.cancel(this)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF
            )
            return
        }
        enableNotifications(p)
    }

    private fun onCountdownToggled(enabled: Boolean) {
        if (suppressNotifListener) return
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!enabled) {
            p.edit().putBoolean(PrayerScheduler.KEY_COUNTDOWN, false).apply()
            PrayerCountdownService.stop(this)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF_COUNTDOWN
            )
            return
        }
        p.edit().putBoolean(PrayerScheduler.KEY_COUNTDOWN, true).apply()
        PrayerCountdownService.start(this)
        Toast.makeText(this, R.string.notif_countdown_on, Toast.LENGTH_SHORT).show()
    }

    private fun enableNotifications(p: SharedPreferences) {
        p.edit().putBoolean(PrayerScheduler.KEY_ENABLED, true).apply()
        NotificationHelper.ensureChannel(this)
        val la = lat
        val lo = lon
        if (la != null && lo != null) {
            p.edit().putString(PrayerScheduler.KEY_CITY, currentCityName).apply()
            PrayerScheduler.schedule(this, la, lo, currentCityName)
        }
        syncCountdown()
        maybePromptExactAlarm()
        Toast.makeText(this, R.string.notif_opened, Toast.LENGTH_SHORT).show()
    }

    private fun syncCountdown() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(PrayerScheduler.KEY_COUNTDOWN, true)) {
            PrayerCountdownService.start(this)
        } else {
            PrayerCountdownService.stop(this)
        }
    }

    private fun maybePromptExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun rescheduleIfEnabled(la: Double, lo: Double) {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(PrayerScheduler.KEY_ENABLED, false)) {
            p.edit().putString(PrayerScheduler.KEY_CITY, currentCityName).apply()
            PrayerScheduler.schedule(this, la, lo, currentCityName)
        }
        syncCountdown()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun nextPrayerResAndTime(): Pair<Int, Double>? {
        val t = prayerToday ?: return null
        val now = nowDecimalHour()
        val today = listOf(
            R.string.p_fajr to t.fajr,
            R.string.p_dhuhr to t.dhuhr,
            R.string.p_asr to t.asr,
            R.string.p_maghrib to t.maghrib,
            R.string.p_isha to t.isha
        )
        for (p in today) if (p.second > now) return p
        val tm = prayerTomorrow ?: return null
        return R.string.p_fajr to (tm.fajr + 24.0)
    }

    private fun countdownText(hours: Double): String {
        val total = (hours * 60).roundToInt().coerceAtLeast(0)
        val h = total / 60
        val m = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d", h, m)
        else String.format(Locale.US, "%d dk", m)
    }

    // ---- theme ----

    private fun applySavedTheme() {
        val mode = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun toggleTheme() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val mode = if (night) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_THEME, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun openMap() {
        val la = lat
        val lo = lon
        val intent = Intent(this, MapActivity::class.java)
        if (la != null && lo != null) {
            intent.putExtra(MapActivity.EXTRA_LAT, la)
            intent.putExtra(MapActivity.EXTRA_LON, lo)
        }
        startActivity(intent)
    }

    private fun showPrayerDialog() {
        val t = prayerToday
        if (t == null) {
            Toast.makeText(this, R.string.waiting_location, Toast.LENGTH_SHORT).show()
            return
        }
        val rows = listOf(
            getString(R.string.p_fajr) to t.fajr,
            getString(R.string.p_sunrise) to t.sunrise,
            getString(R.string.p_dhuhr) to t.dhuhr,
            getString(R.string.p_asr) to t.asr,
            getString(R.string.p_maghrib) to t.maghrib,
            getString(R.string.p_isha) to t.isha
        )
        val sb = StringBuilder()
        for ((name, value) in rows) {
            sb.append(String.format(Locale.US, "%-9s %s", name, PrayerTimes.format(value)))
                .append('\n')
        }
        qiblaSun?.let {
            sb.append('\n')
                .append(getString(R.string.qibla_sun))
                .append(": ")
                .append(PrayerTimes.format(it))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.prayer_times)
            .setMessage(sb.toString().trim())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showBackgroundDialog() {
        val reqPx = (76 * resources.displayMetrics.density).toInt()
        val current = AppBackground.current(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        val scroll = ScrollView(this).apply { addView(column) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.bg_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        for (i in AppBackground.LABELS.indices) {
            val row = layoutInflater.inflate(R.layout.item_bg_option, column, false)
            val thumb = row.findViewById<ImageView>(R.id.bgThumb)
            val label = row.findViewById<TextView>(R.id.bgLabel)
            val res = AppBackground.DRAWABLES[i]
            if (res != 0) {
                thumb.setImageBitmap(decodeThumb(res, reqPx))
            }
            label.text = getString(AppBackground.LABELS[i])
            if (i == current.coerceIn(0, AppBackground.DRAWABLES.size - 1)) {
                label.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                label.setTextColor(getColor(R.color.accent))
                row.setBackgroundColor(getColor(R.color.panel_stroke))
            }
            row.setOnClickListener {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(AppBackground.KEY_BG, i).apply()
                applyBackground()
                dialog.dismiss()
            }
            column.addView(row)
        }
        column.addView(TextView(this).apply {
            text = getString(R.string.bg_credit)
            textSize = 11f
            setTextColor(getColor(R.color.text_dim))
            setPadding(8, 14, 8, 4)
        })
        dialog.show()
    }

    private fun decodeThumb(resId: Int, reqPx: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, resId, bounds)
        var sample = 1
        while (bounds.outWidth / sample > reqPx || bounds.outHeight / sample > reqPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeResource(resources, resId, opts)
    }

    private fun applyBackground() {
        AppBackground.apply(this, bgImage, bgScrim)
    }

    // ---- location ----

    private fun ensurePermissionAndLocate() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQ_LOCATION
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun startLocationUpdates() {
        val lm = locationManager ?: return
        try {
            val gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val netOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!gpsOn && !netOn) {
                setStatus(getString(R.string.location_disabled))
                return
            }
            bestLastKnown(lm)?.let { updateLocation(it) }
            if (gpsOn) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, this)
            }
            if (netOn) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, this)
            }
            if (lat == null && !noSensor) setStatus(getString(R.string.searching))
        } catch (e: SecurityException) {
            setStatus(getString(R.string.permission_denied))
        }
    }

    private fun bestLastKnown(lm: LocationManager): Location? {
        var best: Location? = null
        for (provider in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                if (!lm.isProviderEnabled(provider)) continue
                val l = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || l.time > best.time) best = l
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        return best
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationUpdates()
            } else {
                setStatus(getString(R.string.permission_denied))
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQ_NOTIF) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                enableNotifications(getSharedPreferences(PREFS, Context.MODE_PRIVATE))
            } else {
                suppressNotifListener = true
                switchNotif.isChecked = false
                suppressNotifListener = false
                Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == REQ_NOTIF_COUNTDOWN) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(PrayerScheduler.KEY_COUNTDOWN, true).apply()
                PrayerCountdownService.start(this)
                Toast.makeText(this, R.string.notif_countdown_on, Toast.LENGTH_SHORT).show()
            } else {
                suppressNotifListener = true
                switchCountdown.isChecked = false
                suppressNotifListener = false
                Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCityDialog() {
        val names = Cities.all.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_city)
            .setItems(names) { _, which ->
                val city = Cities.all[which]
                stopGps()
                applyLocation(city.lat, city.lon, 0.0, LocationSource.MANUAL, city.name)
            }
            .show()
    }

    private fun showManualDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 24, 56, 0)
        }
        val etLat = EditText(this).apply {
            hint = getString(R.string.hint_lat)
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val etLon = EditText(this).apply {
            hint = getString(R.string.hint_lon)
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        container.addView(etLat)
        container.addView(etLon)

        AlertDialog.Builder(this)
            .setTitle(R.string.manual_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val la = etLat.text.toString().trim().replace(',', '.').toDoubleOrNull()
                val lo = etLon.text.toString().trim().replace(',', '.').toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    Toast.makeText(this, R.string.invalid_coords, Toast.LENGTH_SHORT).show()
                } else {
                    stopGps()
                    applyLocation(la, lo, 0.0, LocationSource.MANUAL)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun stopGps() {
        runCatching { locationManager?.removeUpdates(this) }
    }

    private fun updateLocation(location: Location) {
        applyLocation(
            location.latitude, location.longitude,
            location.altitude.toDouble(), LocationSource.DEVICE
        )
    }

    private fun applyLocation(
        la: Double, lo: Double, altitude: Double,
        source: LocationSource, nameHint: String? = null
    ) {
        lat = la
        lon = lo
        qiblaBearing = Qibla.bearingToKaaba(la, lo)
        distanceKm = Qibla.distanceToKaaba(la, lo)
        declination = try {
            GeomagneticField(
                la.toFloat(), lo.toFloat(), altitude.toFloat(), System.currentTimeMillis()
            ).declination
        } catch (_: Exception) {
            0f
        }

        if (source == LocationSource.DEVICE) {
            manualOverride = false
            saveDeviceLocation(la, lo)
            if (nameHint != null) {
                currentCityName = nameHint
                geocodedKey = coarseKey(la, lo)
            } else {
                requestCityName(la, lo)
            }
        } else {
            manualOverride = true
            if (nameHint != null) {
                currentCityName = nameHint
                geocodedKey = coarseKey(la, lo)
            } else {
                requestCityName(la, lo)
            }
        }

        recomputePrayers()
        compass.setQibla(qiblaBearing.toFloat())
        compass.setHasLocation(true)
        rescheduleIfEnabled(la, lo)
        updateUi()
    }

    private fun coarseKey(la: Double, lo: Double): String =
        String.format(Locale.US, "%.2f,%.2f", la, lo)

    private fun requestCityName(la: Double, lo: Double) {
        val key = coarseKey(la, lo)
        if (geocodedKey == key) return
        geocodedKey = key
        geocoderExecutor.execute {
            val name = try {
                @Suppress("DEPRECATION")
                val list = Geocoder(this, Locale.getDefault()).getFromLocation(la, lo, 1)
                val a = list?.firstOrNull()
                if (a == null) null else {
                    val city = a.locality ?: a.subAdminArea ?: a.adminArea
                    listOfNotNull(city, a.countryName)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                        .ifBlank { null }
                }
            } catch (_: Exception) {
                null
            }
            if (name != null) {
                runOnUiThread {
                    currentCityName = name
                    updateUi()
                }
            }
        }
    }

    private fun recomputePrayers() {
        val la = lat ?: return
        val lo = lon ?: return
        val now = Calendar.getInstance()
        val tz = now.timeZone.getOffset(now.timeInMillis) / 3_600_000.0
        val y = now.get(Calendar.YEAR)
        val mo = now.get(Calendar.MONTH) + 1
        val d = now.get(Calendar.DAY_OF_MONTH)
        prayerToday = PrayerTimes.compute(y, mo, d, la, lo, tz, method)
        qiblaSun = PrayerTimes.qiblaSunTime(prayerToday!!, la, lo, tz, qiblaBearing, y, mo, d)

        val next = now.clone() as Calendar
        next.add(Calendar.DAY_OF_MONTH, 1)
        prayerTomorrow = PrayerTimes.compute(
            next.get(Calendar.YEAR),
            next.get(Calendar.MONTH) + 1,
            next.get(Calendar.DAY_OF_MONTH),
            la, lo, tz, method
        )
    }

    override fun onLocationChanged(location: Location) {
        updateLocation(location)
    }

    override fun onResume() {
        super.onResume()
        firstSample = true
        hasGravity = false
        hasMag = false
        java.util.Arrays.fill(gravity, 0f)
        java.util.Arrays.fill(geomagnetic, 0f)
        registerSensors()
        if (!manualOverride && hasLocationPermission()) startLocationUpdates()
        PrayerScheduler.rescheduleFromPrefs(this)
        refreshQuranResume()
        if (currentTab == 0) startVakitTicker()
    }

    override fun onPause() {
        super.onPause()
        soundPreview?.let { runCatching { it.release() } }
        soundPreview = null
        stopVakitTicker()
        sensorManager?.unregisterListener(this)
        stopGps()
    }

    private fun registerSensors() {
        val sm = sensorManager ?: return
        sm.unregisterListener(this)
        if (useRotationVector) {
            sm.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val r = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(r, event.values)
                updateOrientation(r)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, gravity)
                hasGravity = true
                computeFallback()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPass(event.values, geomagnetic)
                hasMag = true
                computeFallback()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensorAccuracy = accuracy
    }

    private fun computeFallback() {
        if (useRotationVector || !hasGravity || !hasMag) return
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
            updateOrientation(r)
        }
    }

    private fun updateOrientation(rotationMatrix: FloatArray) {
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
        az = normalize(az + declination)

        if (firstSample) {
            azimuth = az
            firstSample = false
        } else {
            val diff = abs(Qibla.angleDifference(az, azimuth))
            val alpha = when {
                diff > 25f -> 0.6f
                diff > 5f -> 0.32f
                else -> 0.12f
            }
            azimuth = smoothAngle(azimuth, az, alpha)
        }

        compass.setHeading(azimuth)
        updateUi()
    }

    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in input.indices) {
            output[i] += (input[i] - output[i]) * 0.15f
        }
    }

    private fun smoothAngle(current: Float, next: Float, alpha: Float): Float {
        val diff = Qibla.angleDifference(next, current)
        return normalize(current + alpha * diff)
    }

    private fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    private fun accuracyLabel(accuracy: Int): String = when (accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> getString(R.string.acc_high)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> getString(R.string.acc_medium)
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> getString(R.string.acc_low)
        else -> getString(R.string.acc_unreliable)
    }

    private fun nowDecimalHour(): Double {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) +
                c.get(Calendar.MINUTE) / 60.0 +
                c.get(Calendar.SECOND) / 3600.0
    }

    private fun updateUi() {
        val la = lat
        val lo = lon
        if (la == null || lo == null) {
            if (tvInfo.text != getString(R.string.waiting_location)) {
                tvInfo.text = getString(R.string.waiting_location)
            }
            updatePrayerPanel()
            if (currentTab == 0) updateVakitPage()
            return
        }

        compass.setHasLocation(true)

        val aligned = abs(Qibla.angleDifference(qiblaBearing.toFloat(), azimuth)) < ALIGN_TOLERANCE
        compass.setAligned(aligned)
        if (aligned && !wasAligned) vibrateAligned()
        wasAligned = aligned

        val info = getString(
            R.string.info_fmt,
            azimuth.roundToInt().toString(),
            qiblaBearing.roundToInt().toString(),
            distanceKm.roundToInt().toString(),
            String.format(Locale.US, "%.4f", la),
            String.format(Locale.US, "%.4f", lo),
            String.format(Locale.US, "%.1f", declination),
            accuracyLabel(sensorAccuracy)
        )
        if (info != lastInfo) {
            tvInfo.text = info
            lastInfo = info
        }

        updatePrayerPanel()
        if (currentTab == 0) updateVakitPage()

        val calibrated = sensorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        setStatus(
            when {
                noSensor -> getString(R.string.no_sensor)
                !calibrated -> getString(R.string.calibrate)
                aligned -> getString(R.string.aligned)
                else -> getString(R.string.rotate_hint)
            }
        )
    }

    private fun setStatus(text: String) {
        if (text != lastStatus) {
            tvStatus.text = text
            lastStatus = text
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateAligned() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(120)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveDeviceLocation(la: Double, lo: Double) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DEVICE_LAT, la.toString())
            .putString(KEY_DEVICE_LON, lo.toString())
            .apply()
    }

    private fun loadDeviceLocation(): Pair<Double, Double>? {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val la = p.getString(KEY_DEVICE_LAT, null)?.toDoubleOrNull() ?: return null
        val lo = p.getString(KEY_DEVICE_LON, null)?.toDoubleOrNull() ?: return null
        return la to lo
    }
}
