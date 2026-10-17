package com.wendigo.qibla

import android.content.Context
import org.json.JSONObject

/** Loads and caches the 99 Beautiful Names of Allah (Turkish, MIT source). */
object EsmaRepository {

    data class Esma(val arabic: String, val name: String, val meaning: String)

    @Volatile
    private var cache: List<Esma>? = null
    private val lock = Any()

    fun load(context: Context): List<Esma> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val parsed = parse(context.applicationContext)
            cache = parsed
            return parsed
        }
    }

    private fun parse(context: Context): List<Esma> {
        val text = context.assets.open("esma.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(text).getJSONArray("names")
        val result = ArrayList<Esma>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            result.add(
                Esma(
                    arabic = e.optString("ar", ""),
                    name = e.optString("ad", ""),
                    meaning = e.optString("an", "")
                )
            )
        }
        return result
    }
}
