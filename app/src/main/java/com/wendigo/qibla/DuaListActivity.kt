package com.wendigo.qibla

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DuaListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "cat_url"
        const val EXTRA_NAME = "cat_name"
        private const val TYPE_HEADER = 0
        private const val TYPE_DUA = 1
    }

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Item(val dua: DuaRepository.Dua) : Row()
    }

    private val rows = ArrayList<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dua_list)

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.dlToolbar)
        toolbar.title = name
        toolbar.setNavigationOnClickListener { finish() }

        val list = findViewById<RecyclerView>(R.id.dlList)
        list.layoutManager = LinearLayoutManager(this)
        val adapter = RowAdapter()
        list.adapter = adapter

        Thread {
            val data = try {
                DuaRepository.load(applicationContext)
            } catch (_: Exception) {
                null
            }
            val built = ArrayList<Row>()
            if (data != null) {
                for (sub in data.subCategories) {
                    if (sub.category != url) continue
                    val duas = sub.duaIds.mapNotNull { data.duas[it] }
                    if (duas.isEmpty()) continue
                    built.add(Row.Header(sub.title))
                    duas.forEach { built.add(Row.Item(it)) }
                }
            }
            runOnUiThread {
                rows.clear()
                rows.addAll(built)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun showDua(d: DuaRepository.Dua) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun add(text: String, size: Float, colorRes: Int, rtl: Boolean = false, bold: Boolean = false) {
            if (text.isBlank()) return
            col.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(getColor(colorRes))
                if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (rtl) {
                    textDirection = View.TEXT_DIRECTION_RTL
                    gravity = Gravity.RIGHT
                    setLineSpacing(10f, 1f)
                } else {
                    setLineSpacing(4f, 1f)
                }
                setPadding(0, 0, 0, pad / 2)
            })
        }

        add(d.title, 17f, R.color.accent, bold = true)
        add(d.arabic, 24f, R.color.text_primary, rtl = true)
        if (d.okunus.isNotBlank()) add(d.okunus, 15f, R.color.text_dim)
        add(d.meal, 16f, R.color.text_primary)
        if (d.arabic.isBlank() && d.intro.isNotBlank()) add(d.intro, 15f, R.color.text_dim)
        add(d.reference, 12f, R.color.text_dim)

        val scroll = ScrollView(this).apply { addView(col) }
        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_DUA

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(inf.inflate(R.layout.item_dua_header, parent, false))
            } else {
                DuaVH(inf.inflate(R.layout.item_dua, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is Row.Header -> (holder as HeaderVH).t.text = r.title
                is Row.Item -> {
                    val h = holder as DuaVH
                    h.title.text = r.dua.title
                    h.ref.text = r.dua.reference
                    h.itemView.setOnClickListener { showDua(r.dua) }
                }
            }
        }

        inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val t: TextView = v.findViewById(R.id.headerTitle)
        }

        inner class DuaVH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.duaTitle)
            val ref: TextView = v.findViewById(R.id.duaRef)
        }
    }
}
