package com.wendigo.qibla

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

class MapActivity : AppCompatActivity() {

    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidBasePath = File(cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(cacheDir, "osmdroid/tiles")

        setContentView(R.layout.activity_map)

        val toolbar = findViewById<Toolbar>(R.id.toolbarMap)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)
        val user = if (!lat.isNaN() && !lon.isNaN()) GeoPoint(lat, lon) else null
        val kaaba = GeoPoint(Qibla.KAABA_LAT, Qibla.KAABA_LON)

        map.overlays.add(Marker(map).apply {
            position = kaaba
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = getString(R.string.kaaba)
        })

        if (user != null) {
            map.overlays.add(Marker(map).apply {
                position = user
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = getString(R.string.you)
            })

            val points = Qibla.greatCircle(
                user.latitude, user.longitude,
                kaaba.latitude, kaaba.longitude,
                SEGMENTS
            ).map { GeoPoint(it.first, it.second) }

            map.overlays.add(Polyline(map).apply {
                setPoints(points)
                outlinePaint.color = Color.parseColor("#00C853")
                outlinePaint.strokeWidth = 6f
            })

            val box = BoundingBox.fromGeoPoints(listOf(user, kaaba)).increaseByScale(1.4f)
            map.post { map.zoomToBoundingBox(box, true) }
        } else {
            map.controller.setZoom(4.0)
            map.controller.setCenter(kaaba)
        }

        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        private const val SEGMENTS = 96
    }
}
