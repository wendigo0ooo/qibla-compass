package com.wendigo.qibla

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
                    h.itemView.setOnClickListener { DuaDetail.show(this@DuaListActivity, r.dua) }
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
