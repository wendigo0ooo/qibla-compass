package com.wendigo.qibla

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DuaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dua)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.duaToolbar)
        toolbar.title = getString(R.string.tab_dua)
        toolbar.setNavigationOnClickListener { finish() }

        val list = findViewById<RecyclerView>(R.id.duaList)
        list.layoutManager = LinearLayoutManager(this)

        Thread {
            val cats = try {
                DuaRepository.load(applicationContext).categories
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread { list.adapter = CatAdapter(cats) }
        }.start()
    }

    private inner class CatAdapter(private val items: List<DuaRepository.Category>) :
        RecyclerView.Adapter<CatAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.catName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_dua_cat, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.name.text = c.name
            holder.itemView.setOnClickListener {
                startActivity(
                    Intent(this@DuaActivity, DuaListActivity::class.java)
                        .putExtra(DuaListActivity.EXTRA_URL, c.url)
                        .putExtra(DuaListActivity.EXTRA_NAME, c.name)
                )
            }
        }
    }
}
