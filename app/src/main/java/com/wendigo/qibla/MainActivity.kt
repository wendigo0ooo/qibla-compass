package com.wendigo.qibla

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    private lateinit var compass: CompassView
    private lateinit var tvInfo: TextView
    private lateinit var tvStatus: TextView

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

    private var lat: Double? = null
    private var lon: Double? = null
    private var qiblaBearing = 0.0
    private var distanceKm = 0.0

    private var locationManager: LocationManager? = null

    companion object {
        private const val REQ_LOCATION = 1001
        private const val SMOOTH = 0.15f
        private const val ALIGN_TOLERANCE = 5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        compass = findViewById(R.id.compass)
        tvInfo = findViewById(R.id.tvInfo)
        tvStatus = findViewById(R.id.tvStatus)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        useRotationVector = rotationSensor != null

        if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
            tvStatus.text = getString(R.string.no_sensor)
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        findViewById<Button>(R.id.btnManual).setOnClickListener { showManualDialog() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            ensurePermissionAndLocate()
        }

        ensurePermissionAndLocate()
        updateUi()
    }

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
                tvStatus.text = getString(R.string.location_disabled)
                return
            }
            bestLastKnown(lm)?.let { updateLocation(it) }
            if (gpsOn) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1f, this)
            }
            if (netOn) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, this)
            }
            if (lat == null) tvStatus.text = getString(R.string.searching)
        } catch (e: SecurityException) {
            tvStatus.text = getString(R.string.permission_denied)
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
                tvStatus.text = getString(R.string.permission_denied)
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }
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
                val la = etLat.text.toString().trim().toDoubleOrNull()
                val lo = etLon.text.toString().trim().toDoubleOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                    Toast.makeText(this, R.string.invalid_coords, Toast.LENGTH_SHORT).show()
                } else {
                    locationManager?.let { lm -> runCatching { lm.removeUpdates(this@MainActivity) } }
                    applyLocation(la, lo, 0.0)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateLocation(location: Location) {
        applyLocation(location.latitude, location.longitude, location.altitude.toDouble())
    }

    private fun applyLocation(la: Double, lo: Double, altitude: Double) {
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
        compass.setQibla(qiblaBearing.toFloat())
        compass.setHasLocation(true)
        updateUi()
    }

    override fun onLocationChanged(location: Location) {
        updateLocation(location)
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        if (hasLocationPermission()) startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        runCatching { locationManager?.removeUpdates(this) }
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

    private fun updateUi() {
        val la = lat
        val lo = lon
        if (la == null || lo == null) {
            tvInfo.text = getString(R.string.waiting_location)
            return
        }

        compass.setHasLocation(true)

        tvInfo.text = getString(
            R.string.info_fmt,
            azimuth.roundToInt().toString(),
            qiblaBearing.roundToInt().toString(),
            distanceKm.roundToInt().toString(),
            String.format(Locale.US, "%.4f", la),
            String.format(Locale.US, "%.4f", lo)
        )

        val calibrated = sensorAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        val aligned = abs(Qibla.angleDifference(qiblaBearing.toFloat(), azimuth)) < ALIGN_TOLERANCE

        tvStatus.text = when {
            !calibrated -> getString(R.string.calibrate)
            aligned -> getString(R.string.aligned)
            else -> getString(R.string.rotate_hint)
        }
    }
}
