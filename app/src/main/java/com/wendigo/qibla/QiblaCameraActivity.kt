package com.wendigo.qibla

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs

class QiblaCameraActivity : AppCompatActivity(), SensorEventListener, SurfaceHolder.Callback {

    private companion object {
        const val REQ_CAM = 3001
        const val PREFS = "qibla_prefs"
    }

    private var camera: Camera? = null
    private lateinit var overlay: QiblaOverlayView
    private lateinit var info: TextView
    private lateinit var bearingText: TextView

    private var sensorManager: SensorManager? = null
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private var useRotation = false
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMag = false

    private var azimuth = 0f
    private var firstSample = true
    private var declination = 0f
    private var qibla = 0.0
    private var hasLoc = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qibla_camera)

        overlay = findViewById(R.id.qiblaOverlay)
        info = findViewById(R.id.camInfo)
        bearingText = findViewById(R.id.camBearing)
        findViewById<TextView>(R.id.camClose).setOnClickListener { finish() }

        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        val la = p.getString("device_lat", null)?.toDoubleOrNull()
        val lo = p.getString("device_lon", null)?.toDoubleOrNull()
        if (la != null && lo != null) {
            qibla = Qibla.bearingToKaaba(la, lo)
            declination = try {
                GeomagneticField(la.toFloat(), lo.toFloat(), 0f, System.currentTimeMillis()).declination
            } catch (_: Exception) {
                0f
            }
            overlay.qibla = qibla.toFloat()
            hasLoc = true
        } else {
            info.text = getString(R.string.cam_no_loc)
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        useRotation = rotationSensor != null

        val surface = findViewById<SurfaceView>(R.id.camPreview)
        surface.holder.addCallback(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAM)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAM && (grantResults.isEmpty() ||
                grantResults[0] != PackageManager.PERMISSION_GRANTED)
        ) {
            Toast.makeText(this, R.string.cam_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun hasCamera(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun openCamera(holder: SurfaceHolder) {
        if (camera != null || !hasCamera()) return
        try {
            val cam = Camera.open()
            val params = cam.parameters
            val sizes = params.supportedPreviewSizes
            val target = sizes?.minByOrNull { abs(it.width - 1280) + abs(it.height - 720) }
            if (target != null) params.setPreviewSize(target.width, target.height)
            if (params.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            cam.parameters = params
            cam.setDisplayOrientation(90)
            cam.setPreviewDisplay(holder)
            cam.startPreview()
            camera = cam
        } catch (_: Exception) {
            Toast.makeText(this, R.string.cam_error, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseCamera() {
        val cam = camera ?: return
        camera = null
        try {
            cam.stopPreview()
            cam.release()
        } catch (_: Exception) {
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        openCamera(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        openCamera(holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseCamera()
    }

    override fun onResume() {
        super.onResume()
        firstSample = true
        hasGravity = false
        hasMag = false
        val sm = sensorManager ?: return
        sm.unregisterListener(this)
        if (useRotation) {
            sm.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        releaseCamera()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val r = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(r, event.values)
                updateOrientation(r)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                for (i in 0..2) gravity[i] += (event.values[i] - gravity[i]) * 0.15f
                hasGravity = true
                fallback()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                for (i in 0..2) geomagnetic[i] += (event.values[i] - geomagnetic[i]) * 0.15f
                hasMag = true
                fallback()
            }
        }
    }

    private fun fallback() {
        if (useRotation || !hasGravity || !hasMag) return
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) updateOrientation(r)
    }

    private fun updateOrientation(r: FloatArray) {
        val o = FloatArray(3)
        SensorManager.getOrientation(r, o)
        var az = Math.toDegrees(o[0].toDouble()).toFloat() + declination
        az = ((az % 360f) + 360f) % 360f
        if (firstSample) {
            azimuth = az
            firstSample = false
        } else {
            val diff = Qibla.angleDifference(az, azimuth)
            azimuth = ((azimuth + 0.2f * diff) % 360f + 360f) % 360f
        }
        overlay.azimuth = azimuth
        val rot = ((qibla.toFloat() - azimuth + 540f) % 360f) - 180f
        val aligned = abs(rot) < 5f
        info.text = if (!hasLoc) getString(R.string.cam_no_loc)
        else if (aligned) getString(R.string.aligned) else getString(R.string.cam_hint)
        bearingText.text = String.format(
            Locale.US, "Yön: %d°  •  Kıble: %d°", azimuth.toInt(), qibla.toInt()
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
