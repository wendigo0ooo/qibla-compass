package com.wendigo.qibla

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HadisActivity : AppCompatActivity() {

    private companion object {
        const val FAV = "__fav__"
    }

    private data class Row(val index: Int, val hadis: HadisRepository.Hadis)

    private val rows = ArrayList<Row>()
    private var all: List<HadisRepository.Hadis> = emptyList()
    private var categories: List<String> = emptyList()
    private var query = ""
    private var selectedCat: String? = null

    private lateinit var adapter: RowAdapter
    private lateinit var empty: TextView
    private lateinit var chips: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hadis)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.hadisToolbar).apply {
            title = getString(R.string.tab_hadis)
            setNavigationOnClickListener { finish() }
        }

        empty = findViewById(R.id.hadisEmpty)
        chips = findViewById(R.id.hadisChips)

        val list = findViewById<RecyclerView>(R.id.hadisList)
        list.layoutManager = LinearLayoutManager(this)
        adapter = RowAdapter()
        list.adapter = adapter

        findViewById<EditText>(R.id.hadisSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                rebuild()
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        Thread {
            val data = try {
                HadisRepository.load(applicationContext)
            } catch (_: Exception) {
                null
            }
            val items = data?.hadisler ?: emptyList()
            val cats = data?.categories ?: emptyList()
            runOnUiThread {
                all = items
                categories = cats
                buildChips()
                rebuild()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (all.isNotEmpty()) rebuild()
    }

    private fun buildChips() {
        chips.removeAllViews()
        addChip(getString(R.string.hadis_all), null)
        for (c in categories) addChip(c, c)
        addChip(getString(R.string.hadis_favs), FAV)
        refreshChips()
    }

    private fun addChip(label: String, value: String?) {
        val tv = TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            tag = value
            setOnClickListener {
                selectedCat = value
                refreshChips()
                rebuild()
            }
        }
        chips.addView(tv)
    }

    private fun refreshChips() {
        for (i in 0 until chips.childCount) {
            val tv = chips.getChildAt(i) as TextView
            val sel = (tv.tag as String?) == selectedCat
            tv.setBackgroundResource(if (sel) R.drawable.chip_bg_selected else R.drawable.chip_bg)
            tv.setTextColor(
                getColor(if (sel) android.R.color.white else R.color.text_primary)
            )
        }
    }

    private fun rebuild() {
        val tr = Locale("tr", "TR")
        val q = query.lowercase(tr)
        val out = ArrayList<Row>(all.size)
        for ((idx, h) in all.withIndex()) {
            if (selectedCat == FAV && !HadisRepository.isFav(this, idx)) continue
            if (selectedCat != null && selectedCat != FAV && h.category != selectedCat) continue
            if (q.isNotEmpty()) {
                val hit = h.turkish.lowercase(tr).contains(q) ||
                    h.reference.lowercase(tr).contains(q) ||
                    h.category.lowercase(tr).contains(q) ||
                    h.arabic.contains(query, ignoreCase = true)
                if (!hit) continue
            }
            out.add(Row(idx, h))
        }
        rows.clear()
        rows.addAll(out)
        adapter.notifyDataSetChanged()
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun shareHadis(h: HadisRepository.Hadis) {
        val text = "${h.turkish}\n\n— ${h.reference}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.hadis_share)))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.hadisText)
            val ref: TextView = v.findViewById(R.id.hadisRef)
            val star: ImageView = v.findViewById(R.id.hadisStar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_hadis, parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = rows[position]
            holder.text.text = r.hadis.turkish
            holder.ref.text = if (r.hadis.category.isBlank()) {
                r.hadis.reference
            } else {
                getString(R.string.hadis_ref_fmt, r.hadis.category, r.hadis.reference)
            }
            val fav = HadisRepository.isFav(this@HadisActivity, r.index)
            holder.star.setImageResource(
                if (fav) R.drawable.ic_star else R.drawable.ic_star_outline
            )
            holder.star.setColorFilter(
                getColor(if (fav) R.color.accent else R.color.text_dim)
            )
            holder.star.setOnClickListener {
                HadisRepository.toggleFav(this@HadisActivity, r.index)
                notifyItemChanged(position)
            }
            holder.itemView.setOnClickListener {
                startActivity(
                    Intent(this@HadisActivity, HadisDetailActivity::class.java)
                        .putExtra(HadisDetailActivity.EXTRA_INDEX, r.index)
                )
            }
            holder.itemView.setOnLongClickListener {
                shareHadis(r.hadis)
                true
            }
        }
    }
}
