package com.wendigo.qibla

import android.content.Context
import org.json.JSONObject

/** Loads and caches the offline Qur'an (Arabic text + Elmalılı Turkish meal). */
object QuranRepository {

    data class Ayah(val ar: String, val tr: String)
    data class Surah(
        val number: Int,
        val name: String,
        val arName: String,
        val place: String,
        val ayahs: List<Ayah>
    )

    @Volatile
    private var cache: List<Surah>? = null
    private val lock = Any()

    fun load(context: Context): List<Surah> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val parsed = parse(context.applicationContext)
            cache = parsed
            return parsed
        }
    }

    private fun parse(context: Context): List<Surah> {
        val text = context.assets.open("quran.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val arr = root.getJSONArray("surahs")
        val result = ArrayList<Surah>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val arArr = s.getJSONArray("ar")
            val trArr = s.getJSONArray("tr")
            val ayahs = ArrayList<Ayah>(arArr.length())
            for (j in 0 until arArr.length()) {
                ayahs.add(Ayah(arArr.getString(j), trArr.getString(j)))
            }
            result.add(
                Surah(
                    number = s.getInt("n"),
                    name = s.getString("name"),
                    arName = s.optString("ar_name", ""),
                    place = s.optString("place", ""),
                    ayahs = ayahs
                )
            )
        }
        return result
    }
}
