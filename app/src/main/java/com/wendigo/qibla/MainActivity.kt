package com.wendigo.qibla

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private enum class LocationSource { DEVICE, MANUAL }

    private lateinit var compass: CompassView
    private lateinit var tvInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvCity: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvNextLine: TextView
    private lateinit var prayerRow: LinearLayout

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
    private var method = PrayerMethod.DIYANET

    private var currentCityName: String? = null
    private var geocodedKey: String? = null
    private val geocoderExecutor = Executors.newSingleThreadExecutor()

    private var locationManager: LocationManager? = null

    private var lastInfo = ""
    private var lastStatus = ""

    companion object {
        private const val REQ_LOCATION = 1001
        private const val SMOOTH = 0.15f
        private const val ALIGN_TOLERANCE = 5f
        private const val PREFS = "qibla_prefs"
        private const val KEY_DEVICE_LAT = "device_lat"
        private const val KEY_DEVICE_LON = "device_lon"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_METHOD = "prayer_method"
        private const val STATE_LAT = "state_lat"
        private const val STATE_LON = "state_lon"
        private const val STATE_MANUAL = "state_manual"
        private const val STATE_CITY = "state_city"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        compass = findViewById(R.id.compass)
        tvInfo = findViewById(R.id.tvInfo)
        tvStatus = findViewById(R.id.tvStatus)
        tvCity = findViewById(R.id.tvCity)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvNextLine = findViewById(R.id.tvNextLine)
        prayerRow = findViewById(R.id.prayerRow)
        buildPrayerRow()

        method = savedMethod()

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
        geocoderExecutor.shutdownNow()
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

    // ---- menu ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_map -> {
                openMap()
                true
            }
            R.id.action_prayer -> {
                showPrayerDialog()
                true
            }
            R.id.action_method -> {
                showMethodDialog()
                true
            }
            R.id.action_theme -> {
                toggleTheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
            .setTitle(getString(R.string.prayer_times) + " — " + method.label)
            .setMessage(sb.toString().trim())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showMethodDialog() {
        val methods = PrayerMethod.values()
        val labels = methods.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.prayer_method)
            .setSingleChoiceItems(labels, method.ordinal) { dialog, which ->
                method = methods[which]
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_METHOD, method.ordinal).apply()
                recomputePrayers()
                updateUi()
                dialog.dismiss()
            }
            .show()
    }

    private fun savedMethod(): PrayerMethod {
        val idx = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_METHOD, 0)
        return PrayerMethod.values().getOrElse(idx) { PrayerMethod.DIYANET }
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
        registerSensors()
        if (!manualOverride && hasLocationPermission()) startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
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
        azimuth = smoothAngle(azimuth, az, SMOOTH)

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
