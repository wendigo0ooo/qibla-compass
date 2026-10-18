package com.wendigo.qibla

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MescitActivity : AppCompatActivity(), LocationListener {

    private data class Mosque(val name: String, val lat: Double, val lon: Double, val distance: Float)

    private companion object {
        const val REQ_LOC = 2001
        const val PREFS = "qibla_prefs"
        const val LOC_TIMEOUT_MS = 12000L

        val RADII = intArrayOf(3000, 8000, 15000)

        val ENDPOINTS = arrayOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
    }

    private val items = ArrayList<Mosque>()
    private lateinit var adapter: MosqueAdapter
    private lateinit var status: TextView
    private lateinit var findButton: Button
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    private val timeout = Runnable { onLocationTimeout() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mescit)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.mescitToolbar).apply {
            title = getString(R.string.tool_mescit_title)
            setNavigationOnClickListener { finish() }
        }

        status = findViewById(R.id.mescitStatus)
        findButton = findViewById(R.id.btnFind)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        val list = findViewById<RecyclerView>(R.id.mescitList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = MosqueAdapter()
        list.adapter = adapter

        findButton.setOnClickListener { onFind() }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun onFind() {
        if (!hasPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQ_LOC
            )
            return
        }
        stopListening()
        findButton.isEnabled = false
        status.text = getString(R.string.mescit_locating)

        val known = bestLastKnown()
        if (known != null && System.currentTimeMillis() - known.time < 60_000L) {
            useLocation(known)
            return
        }
        startListening()
        if (listening) handler.postDelayed(timeout, LOC_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        val lm = locationManager ?: run {
            fallbackLocation()
            return
        }
        try {
            val gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val netOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!gpsOn && !netOn) {
                fallbackLocation()
                return
            }
            listening = true
            if (gpsOn) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
            if (netOn) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this)
        } catch (_: SecurityException) {
            fallbackLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopListening() {
        if (!listening) return
        listening = false
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!listening) return
        stopListening()
        handler.removeCallbacks(timeout)
        useLocation(location)
    }

    private fun onLocationTimeout() {
        stopListening()
        fallbackLocation()
    }

    private fun fallbackLocation() {
        val l = bestLastKnown() ?: savedLocation()
        if (l != null) useLocation(l) else {
            status.text = getString(R.string.mescit_no_loc)
            findButton.isEnabled = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(): Location? {
        val lm = locationManager ?: return null
        var best: Location? = null
        for (p in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                if (!lm.isProviderEnabled(p)) continue
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best!!.time) best = l
            } catch (_: Exception) {
            }
        }
        return best
    }

    private fun savedLocation(): Location? {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val la = p.getString("device_lat", null)?.toDoubleOrNull() ?: return null
        val lo = p.getString("device_lon", null)?.toDoubleOrNull() ?: return null
        return Location("saved").apply {
            latitude = la
            longitude = lo
        }
    }

    private fun useLocation(loc: Location) {
        status.text = getString(R.string.mescit_you_are_here, loc.latitude, loc.longitude)
        query(loc.latitude, loc.longitude)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOC) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                onFind()
            } else {
                status.text = getString(R.string.mescit_no_loc)
            }
        }
    }

    private fun query(la: Double, lo: Double) {
        status.text = getString(R.string.mescit_searching)
        findButton.isEnabled = false
        items.clear()
        adapter.notifyDataSetChanged()

        Thread {
            var found: List<Mosque>? = null
            var usedRadius = RADII.first()

            for (r in RADII) {
                val res = try {
                    fetchMosques(la, lo, r)
                } catch (_: Exception) {
                    null
                }
                if (res == null) {
                    found = null
                    break
                }
                found = res
                usedRadius = r
                if (res.isNotEmpty()) break
            }

            runOnUiThread {
                findButton.isEnabled = true
                val result = found
                when {
                    result == null -> status.text = getString(R.string.mescit_error)
                    result.isEmpty() -> status.text = getString(R.string.mescit_none)
                    else -> {
                        items.clear()
                        items.addAll(result)
                        adapter.notifyDataSetChanged()
                        status.text = getString(R.string.mescit_count, result.size) +
                                " · " + getString(R.string.mescit_radius_info, usedRadius / 1000)
                    }
                }
            }
        }.start()
    }

    private fun fetchMosques(la: Double, lo: Double, radius: Int): List<Mosque> {
        val q = "[out:json][timeout:40];" +
                "(nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:$radius,$la,$lo);" +
                "nwr[\"building\"=\"mosque\"](around:$radius,$la,$lo););" +
                "out center;"
        var last: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                return request(endpoint, q, la, lo)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no endpoint")
    }

    private fun request(endpoint: String, q: String, la: Double, lo: Double): List<Mosque> {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        conn.setRequestProperty("User-Agent", "KiblePusulasi/5.9 (mosque finder)")
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.outputStream.use {
            it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray(Charsets.UTF_8))
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(body)
        if (!json.has("elements")) {
            throw IllegalStateException(json.optString("remark", "overpass error"))
        }
        val elements = json.getJSONArray("elements")

        val out = ArrayList<Mosque>()
        val seen = HashSet<String>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags")
            val name = tags?.let {
                it.optString("name:tr").ifBlank { "" }.ifBlank { it.optString("name") }
            }.orEmpty().ifBlank { getString(R.string.mescit_unnamed) }

            var mlat = el.optDouble("lat", Double.NaN)
            var mlon = el.optDouble("lon", Double.NaN)
            if (mlat.isNaN() || mlon.isNaN()) {
                val c = el.optJSONObject("center") ?: continue
                mlat = c.optDouble("lat", Double.NaN)
                mlon = c.optDouble("lon", Double.NaN)
            }
            if (mlat.isNaN() || mlon.isNaN()) continue

            val key = String.format(Locale.US, "%.5f,%.5f", mlat, mlon)
            if (!seen.add(key)) continue

            val res = FloatArray(1)
            Location.distanceBetween(la, lo, mlat, mlon, res)
            out.add(Mosque(name, mlat, mlon, res[0]))
        }
        out.sortBy { it.distance }
        return out
    }

    private fun openInMap(m: Mosque) {
        val label = Uri.encode(m.name)
        val geo = Uri.parse("geo:${m.lat},${m.lon}?q=${m.lat},${m.lon}($label)")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, geo))
        } catch (_: Exception) {
            val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=${m.lat},${m.lon}")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, web))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.mescit_no_map, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopListening()
        handler.removeCallbacks(timeout)
    }

    private inner class MosqueAdapter : RecyclerView.Adapter<MosqueAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.mescitName)
            val meta: TextView = v.findViewById(R.id.mescitMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_mescit, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = items[position]
            holder.name.text = m.name
            val dist = if (m.distance >= 1000) {
                String.format(Locale.US, "%.1f km", m.distance / 1000f)
            } else {
                String.format(Locale.US, "%d m", m.distance.toInt())
            }
            val walk = if (m.distance <= 2000f) {
                " · " + getString(
                    R.string.mescit_walk,
                    maxOf(1, (m.distance / 83.3f).toInt())
                )
            } else ""
            holder.meta.text = dist + walk
            holder.itemView.setOnClickListener { openInMap(m) }
            holder.itemView.setOnLongClickListener {
                openInMap(m)
                true
            }
        }
    }
}
