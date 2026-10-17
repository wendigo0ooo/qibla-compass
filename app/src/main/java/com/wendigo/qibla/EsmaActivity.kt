package com.wendigo.qibla

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EsmaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_esma)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.esmaToolbar)
        toolbar.title = getString(R.string.tab_esma)
        toolbar.setNavigationOnClickListener { finish() }

        val list = findViewById<RecyclerView>(R.id.esmaList)
        list.layoutManager = LinearLayoutManager(this)

        Thread {
            val names = try {
                EsmaRepository.load(applicationContext)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread { list.adapter = EsmaAdapter(names) }
        }.start()
    }

    private inner class EsmaAdapter(private val items: List<EsmaRepository.Esma>) :
        RecyclerView.Adapter<EsmaAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val num: TextView = v.findViewById(R.id.esmaNum)
            val name: TextView = v.findViewById(R.id.esmaName)
            val meaning: TextView = v.findViewById(R.id.esmaMeaning)
            val arabic: TextView = v.findViewById(R.id.esmaArabic)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_esma, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.num.text = (position + 1).toString()
            holder.name.text = e.name
            holder.meaning.text = e.meaning
            holder.arabic.text = e.arabic
        }
    }
}
