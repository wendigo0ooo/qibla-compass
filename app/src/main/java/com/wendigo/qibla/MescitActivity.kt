package com.wendigo.qibla

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
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

class MescitActivity : AppCompatActivity() {

    private data class Mosque(val name: String, val lat: Double, val lon: Double, val distance: Float)

    private companion object {
        const val REQ_LOC = 2001
        const val PREFS = "qibla_prefs"

        val ENDPOINTS = arrayOf(
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
    }

    private var lat: Double? = null
    private var lon: Double? = null
    private val items = ArrayList<Mosque>()
    private lateinit var adapter: MosqueAdapter
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mescit)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.mescitToolbar).apply {
            title = getString(R.string.tool_mescit_title)
            setNavigationOnClickListener { finish() }
        }

        status = findViewById(R.id.mescitStatus)
        val list = findViewById<RecyclerView>(R.id.mescitList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = MosqueAdapter()
        list.adapter = adapter

        loadKnownLocation()
        findViewById<Button>(R.id.btnFind).setOnClickListener { onFind() }
    }

    private fun loadKnownLocation() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lat = p.getString("device_lat", null)?.toDoubleOrNull()
        lon = p.getString("device_lon", null)?.toDoubleOrNull()
        if (hasPermission()) {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            var best: Location? = null
            for (provider in listOf(
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )) {
                try {
                    if (!lm!!.isProviderEnabled(provider)) continue
                    val l = lm.getLastKnownLocation(provider) ?: continue
                    if (best == null || l.time > best!!.time) best = l
                } catch (_: Exception) {
                }
            }
            if (best != null) {
                lat = best!!.latitude
                lon = best!!.longitude
            }
        }
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
        loadKnownLocation()
        val la = lat
        val lo = lon
        if (la == null || lo == null) {
            status.text = getString(R.string.mescit_no_loc)
            return
        }
        query(la, lo)
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
        items.clear()
        adapter.notifyDataSetChanged()
        Thread {
            val found = try {
                fetchMosques(la, lo)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (found == null) {
                    status.text = getString(R.string.mescit_error)
                    return@runOnUiThread
                }
                items.clear()
                items.addAll(found)
                adapter.notifyDataSetChanged()
                status.text = if (found.isEmpty()) getString(R.string.mescit_none)
                else getString(R.string.mescit_count, found.size)
            }
        }.start()
    }

    private fun fetchMosques(la: Double, lo: Double): List<Mosque> {
        val q = "[out:json][timeout:40];" +
                "nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:5000,$la,$lo);" +
                "out center 60;"
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
        val conn = (URL("$endpoint?data=" + URLEncoder.encode(q, "UTF-8")).openConnection()
                as HttpURLConnection)
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.setRequestProperty("User-Agent", "KiblePusulasi/4.4")
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
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags")
            val name = tags?.let {
                it.optString("name:tr").ifBlank { "" }
                    .ifBlank { it.optString("name") }
            }.orEmpty().ifBlank { getString(R.string.mescit_unnamed) }

            var mlat = el.optDouble("lat", Double.NaN)
            var mlon = el.optDouble("lon", Double.NaN)
            if (mlat.isNaN() || mlon.isNaN()) {
                val c = el.optJSONObject("center") ?: continue
                mlat = c.optDouble("lat", Double.NaN)
                mlon = c.optDouble("lon", Double.NaN)
            }
            if (mlat.isNaN() || mlon.isNaN()) continue

            val res = FloatArray(1)
            Location.distanceBetween(la, lo, mlat, mlon, res)
            out.add(Mosque(name, mlat, mlon, res[0]))
        }
        out.sortBy { it.distance }
        return out
    }

    private fun openInMap(m: Mosque) {
        val label = Uri.encode(m.name)
        val uri = Uri.parse("geo:${m.lat},${m.lon}?q=${m.lat},${m.lon}($label)")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.mescit_no_map, Toast.LENGTH_SHORT).show()
        }
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
            val dist = if (m.distance >= 1000)
                String.format(Locale.US, "%.1f km", m.distance / 1000f)
            else String.format(Locale.US, "%d m", m.distance.toInt())
            holder.meta.text = dist
            holder.itemView.setOnClickListener { openInMap(m) }
        }
    }
}
