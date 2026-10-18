package com.wendigo.qibla

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DuaActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private val adapter = RowAdapter()
    private val rows = ArrayList<Row>()

    private var categories: List<DuaRepository.Category> = emptyList()
    private var entries: List<Entry> = emptyList()

    private sealed class Row {
        data class Cat(val name: String, val url: String) : Row()
        data class Dua(val dua: DuaRepository.Dua, val subtitle: String) : Row()
    }

    /** Aramada kullanılan düzleştirilmiş dua kaydı. */
    private class Entry(
        val dua: DuaRepository.Dua,
        val subtitle: String,
        val haystack: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dua)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.duaToolbar)
        toolbar.title = getString(R.string.tab_dua)
        toolbar.setNavigationOnClickListener { finish() }

        list = findViewById(R.id.duaList)
        empty = findViewById(R.id.duaEmpty)
        search = findViewById(R.id.duaSearch)

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyQuery(search.text.toString())
                true
            } else false
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyQuery(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        Thread {
            val loaded = try {
                DuaRepository.load(applicationContext)
            } catch (_: Exception) {
                null
            }
            val cats = loaded?.categories ?: emptyList()
            val built = loaded?.let { buildEntries(it) } ?: emptyList()
            runOnUiThread {
                categories = cats
                entries = built
                applyQuery(search.text.toString())
            }
        }.start()
    }

    private fun buildEntries(d: DuaRepository.Data): List<Entry> {
        val names = HashMap<String, String>()
        d.categories.forEach { names[it.url] = it.name }

        val meta = HashMap<Int, StringBuilder>()
        for (sub in d.subCategories) {
            val catName = names[sub.category] ?: sub.category
            for (id in sub.duaIds) {
                val sb = meta.getOrPut(id) { StringBuilder() }
                sb.append(' ').append(sub.title).append(' ').append(catName)
            }
        }
        val out = ArrayList<Entry>()
        for ((id, dua) in d.duas) {
            val extra = meta[id]?.toString().orEmpty()
            val haystack = FetvaRepository.normalize(
                dua.title + " " + dua.okunus + " " + dua.meal + " " + dua.intro + extra
            )
            val sub = extra.trim().replace(Regex("\\s+"), " ").let {
                if (it.length > 60) it.substring(0, 60) else it
            }
            out.add(Entry(dua, sub, haystack))
        }
        return out
    }

    private fun showCategories() {
        rows.clear()
        categories.forEach { rows.add(Row.Cat(it.name, it.url)) }
        empty.visibility = View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun applyQuery(raw: String) {
        val q = FetvaRepository.normalize(raw)
        if (q.isBlank()) {
            showCategories()
            return
        }
        val tokens = q.split(" ").filter { it.isNotBlank() }
        val contentTokens = tokens.filter { it !in STOPWORDS }
        val required = if (contentTokens.isEmpty()) tokens else contentTokens

        val strict = ArrayList<Pair<Entry, Int>>()
        val loose = ArrayList<Pair<Entry, Int>>()
        for (e in entries) {
            var matchedRequired = 0
            var score = 0
            for (t in tokens) {
                val alts = synonyms(t)
                var hit = false
                for (a in alts) {
                    if (e.haystack.contains(a)) {
                        hit = true
                        score += if (a.length >= 4) 2 else 1
                    }
                }
                if (hit && t in required) matchedRequired++
            }
            if (score > 0) {
                if (matchedRequired >= required.size) strict.add(e to score) else loose.add(e to score)
            }
        }
        val chosen = if (strict.isNotEmpty()) strict else loose
        chosen.sortWith(compareByDescending<Pair<Entry, Int>> { it.second }
            .thenBy { it.first.dua.title })

        rows.clear()
        chosen.forEach { rows.add(Row.Dua(it.first.dua, it.first.subtitle)) }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun synonyms(token: String): Set<String> {
        val extra = SYNONYMS.entries.firstOrNull { token in it.value }?.value
        return if (extra == null) setOf(token) else extra
    }

    private inner class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) =
            if (rows[position] is Row.Cat) TYPE_CAT else TYPE_DUA

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_CAT) {
                CatVH(inf.inflate(R.layout.item_dua_cat, parent, false))
            } else {
                DuaVH(inf.inflate(R.layout.item_dua, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is Row.Cat -> {
                    val h = holder as CatVH
                    h.name.text = r.name
                    h.itemView.setOnClickListener {
                        startActivity(
                            Intent(this@DuaActivity, DuaListActivity::class.java)
                                .putExtra(DuaListActivity.EXTRA_URL, r.url)
                                .putExtra(DuaListActivity.EXTRA_NAME, r.name)
                        )
                    }
                }
                is Row.Dua -> {
                    val h = holder as DuaVH
                    h.title.text = r.dua.title
                    h.ref.text = if (r.subtitle.isBlank()) r.dua.reference
                    else r.dua.reference + "  •  " + r.subtitle
                    h.itemView.setOnClickListener { DuaDetail.show(this@DuaActivity, r.dua) }
                }
            }
        }

        inner class CatVH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.catName)
        }

        inner class DuaVH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.duaTitle)
            val ref: TextView = v.findViewById(R.id.duaRef)
        }
    }

    companion object {
        private const val TYPE_CAT = 0
        private const val TYPE_DUA = 1

        private val STOPWORDS = setOf(
            "dua", "dualar", "duasi", "okunacak", "okunan", "okunur", "okunurken",
            "okurken", "ne", "nasil", "zaman", "icin", "ile", "ve", "bir", "mi", "mu", "olan"
        )

        /** Eşanlamlı sözcük grupları (kullanıcı deyimi -> veri deyimi). */
        private val SYNONYMS: Map<String, Set<String>> = mapOf(
            "tuvalet" to setOf("tuvalet", "lavabo", "lavaboya", "wc", "hela", "abdest"),
            "ezan" to setOf("ezan", "ikamet", "kamet"),
            "yolculuk" to setOf("yolculuk", "seyahat", "sefer", "yol"),
            "uyku" to setOf("uyku", "uyumak", "yatak", "yatarken", "uyanmak", "uyanma"),
            "yemek" to setOf("yemek", "taam", "sofra"),
            "abdest" to setOf("abdest", "taharet", "gusul", "gustil"),
            "kabir" to setOf("kabir", "mezar", "kabristan"),
            "namaz" to setOf("namaz", "salat"),
            "hastalik" to setOf("hastalik", "hasta", "sifa", "derman")
        )
    }
}
