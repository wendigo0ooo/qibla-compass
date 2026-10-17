package com.wendigo.qibla

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Loads and caches the bundled fatwa collection (assets/fetvalar.json) and
 * provides a simple offline Turkish keyword search.
 *
 * Answers are concise summaries reflecting the Diyanet İşleri Başkanlığı view.
 */
object FetvaRepository {

    data class Fetva(
        val soru: String,
        val kategori: String,
        val cevap: String,
        val mezhep: String,
        val kaynak: String,
        val questionNorm: String,
        val allNorm: String
    )

    @Volatile
    private var cache: List<Fetva>? = null
    private val lock = Any()

    fun load(context: Context): List<Fetva> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val parsed = parse(context.applicationContext)
            cache = parsed
            return parsed
        }
    }

    private fun parse(context: Context): List<Fetva> {
        val text = context.assets.open("fetvalar.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(text).getJSONArray("fetvalar")

        val list = ArrayList<Fetva>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val soru = o.optString("s", "")
            val kategori = o.optString("k", "")
            val cevap = o.optString("c", "")
            val mezhep = o.optString("m", "")
            val kaynak = o.optString("rf", "")
            val qNorm = normalize(soru)
            val allNorm = normalize("$soru $kategori $mezhep $cevap")
            list.add(Fetva(soru, kategori, cevap, mezhep, kaynak, qNorm, allNorm))
        }
        return list
    }

    /** Ranks the collection for the given query. Empty query returns everything. */
    fun search(context: Context, query: String): List<Fetva> {
        val all = load(context)
        val q = normalize(query)
        if (q.isBlank()) return all
        val tokens = q.split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return all

        val scored = ArrayList<Pair<Fetva, Int>>()
        for (f in all) {
            var score = 0
            for (t in tokens) {
                if (f.questionNorm.contains(t)) score += 5
                else if (f.allNorm.contains(t)) score += 2
            }
            if (score > 0) scored.add(f to score)
        }
        return scored.sortedByDescending { it.second }.map { it.first }
    }

    /** Lowercases and strips Turkish diacritics so matching is forgiving. */
    fun normalize(input: String): String {
        val lower = input.lowercase(Locale("tr", "TR"))
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            when (ch) {
                'ç' -> sb.append('c')
                'ğ' -> sb.append('g')
                'ı' -> sb.append('i')
                'i' -> sb.append('i')
                'ö' -> sb.append('o')
                'ş' -> sb.append('s')
                'ü' -> sb.append('u')
                'â', 'a' -> sb.append('a')
                'î', 'í' -> sb.append('i')
                'û' -> sb.append('u')
                in 'a'..'z', in '0'..'9' -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
