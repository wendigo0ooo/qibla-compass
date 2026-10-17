package com.wendigo.qibla

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

class HadisActivity : AppCompatActivity() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    private sealed class Row {
        data class Header(val title: String) : Row()
        data class Item(val hadis: HadisRepository.Hadis) : Row()
    }

    private val rows = ArrayList<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hadis)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.hadisToolbar).apply {
            title = getString(R.string.tab_hadis)
            setNavigationOnClickListener { finish() }
        }

        val list = findViewById<RecyclerView>(R.id.hadisList)
        list.layoutManager = LinearLayoutManager(this)
        val adapter = RowAdapter()
        list.adapter = adapter

        Thread {
            val data = try {
                HadisRepository.load(applicationContext)
            } catch (_: Exception) {
                null
            }
            val built = ArrayList<Row>()
            if (data != null) {
                for (cat in data.categories) {
                    val items = data.hadisler.filter { it.category == cat }
                    if (items.isEmpty()) continue
                    built.add(Row.Header(cat))
                    items.forEach { built.add(Row.Item(it)) }
                }
            }
            runOnUiThread {
                rows.clear()
                rows.addAll(built)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun showHadis(h: HadisRepository.Hadis) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        if (h.arabic.isNotBlank()) {
            col.addView(TextView(this).apply {
                text = h.arabic
                textSize = 24f
                setTextColor(getColor(R.color.text_primary))
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = Gravity.RIGHT
                setLineSpacing(10f, 1f)
                setPadding(0, 0, 0, pad / 2)
            })
        }

        col.addView(TextView(this).apply {
            text = h.turkish
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setLineSpacing(4f, 1f)
            setPadding(0, 0, 0, pad / 2)
        })

        col.addView(TextView(this).apply {
            text = h.reference
            textSize = 12f
            setTextColor(getColor(R.color.text_dim))
        })

        val scroll = ScrollView(this).apply { addView(col) }
        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class RowAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_ITEM

        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderVH(inf.inflate(R.layout.item_dua_header, parent, false))
            } else {
                HadisVH(inf.inflate(R.layout.item_hadis, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is Row.Header -> (holder as HeaderVH).t.text = r.title
                is Row.Item -> {
                    val h = holder as HadisVH
                    h.text.text = r.hadis.turkish
                    h.ref.text = r.hadis.reference
                    h.itemView.setOnClickListener { showHadis(r.hadis) }
                }
            }
        }

        inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
            val t: TextView = v.findViewById(R.id.headerTitle)
        }

        inner class HadisVH(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.hadisText)
            val ref: TextView = v.findViewById(R.id.hadisRef)
        }
    }
}
