package com.wendigo.qibla

import android.content.Context
import org.json.JSONObject

/** Loads and caches the offline dua collection (islamicapi/masnun-dua, free). */
object DuaRepository {

    data class Dua(
        val id: Int,
        val title: String,
        val arabic: String,
        val okunus: String,
        val meal: String,
        val reference: String,
        val intro: String
    )

    data class SubCategory(val id: Int, val title: String, val category: String, val duaIds: List<Int>)
    data class Category(val name: String, val url: String)
    data class Data(
        val categories: List<Category>,
        val subCategories: List<SubCategory>,
        val duas: Map<Int, Dua>
    )

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
        val text = context.assets.open("dualar.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)

        val catArr = root.getJSONArray("categories")
        val categories = ArrayList<Category>(catArr.length())
        for (i in 0 until catArr.length()) {
            val c = catArr.getJSONObject(i)
            categories.add(Category(c.optString("n", ""), c.optString("u", "")))
        }

        val subArr = root.getJSONArray("subs")
        val subs = ArrayList<SubCategory>(subArr.length())
        for (i in 0 until subArr.length()) {
            val s = subArr.getJSONObject(i)
            val idsArr = s.optJSONArray("d")
            val ids = ArrayList<Int>(idsArr?.length() ?: 0)
            if (idsArr != null) for (j in 0 until idsArr.length()) ids.add(idsArr.getInt(j))
            subs.add(SubCategory(s.optInt("i", 0), s.optString("t", ""), s.optString("c", ""), ids))
        }

        val duaObj = root.getJSONObject("duas")
        val duas = HashMap<Int, Dua>()
        val keys = duaObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val d = duaObj.getJSONObject(key)
            val id = key.toIntOrNull() ?: continue
            duas[id] = Dua(
                id = id,
                title = d.optString("t", ""),
                arabic = d.optString("ar", ""),
                okunus = d.optString("ok", ""),
                meal = d.optString("me", ""),
                reference = d.optString("rf", ""),
                intro = d.optString("in", "")
            )
        }

        return Data(categories, subs, duas)
    }
}
