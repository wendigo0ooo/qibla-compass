package com.wendigo.qibla

import android.content.Context
import org.json.JSONObject

/** Loads and caches the bundled hadith collection (assets/hadis.json). */
object HadisRepository {

    data class Hadis(
        val category: String,
        val arabic: String,
        val turkish: String,
        val reference: String
    )

    data class Data(val hadisler: List<Hadis>, val categories: List<String>)

    private const val PREFS = "qibla_prefs"
    private const val KEY_FAVS = "hadis_favs"

    fun favIndexes(context: Context): Set<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getStringSet(KEY_FAVS, emptySet()) ?: emptySet()
    }

    fun isFav(context: Context, index: Int): Boolean = favIndexes(context).contains(index.toString())

    fun toggleFav(context: Context, index: Int): Boolean {
        val set = favIndexes(context).toMutableSet()
        val key = index.toString()
        val nowFav = if (set.contains(key)) {
            set.remove(key); false
        } else {
            set.add(key); true
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_FAVS, set).apply()
        return nowFav
    }

    @Volatile
    private var cache: Data? = null
    private val lock = Any()

    fun load(context: Context): Data {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val parsed = parse(context.applicationContext)
            cache = parsed
            return parsed
        }
    }

    private fun parse(context: Context): Data {
        val text = context.assets.open("hadis.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(text).getJSONArray("hadisler")

        val hadisler = ArrayList<Hadis>(arr.length())
        val categories = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val h = arr.getJSONObject(i)
            val category = h.optString("k", "")
            categories.add(category)
            hadisler.add(
                Hadis(
                    category = category,
                    arabic = h.optString("ar", ""),
                    turkish = h.optString("tr", ""),
                    reference = h.optString("rf", "")
                )
            )
        }
        return Data(hadisler, categories.toList())
    }
}
