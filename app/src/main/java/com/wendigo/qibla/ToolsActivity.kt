package com.wendigo.qibla

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ToolsActivity : AppCompatActivity() {

    private data class Tool(
        val titleRes: Int,
        val subRes: Int,
        val iconRes: Int,
        val target: Class<*>
    )

    private val tools = listOf(
        Tool(R.string.tool_hijri_title, R.string.tool_hijri_sub, R.drawable.ic_calendar, HijriActivity::class.java),
        Tool(R.string.tool_zakat_title, R.string.tool_zakat_sub, R.drawable.ic_zakat, ZakatActivity::class.java),
        Tool(R.string.tool_mescit_title, R.string.tool_mescit_sub, R.drawable.ic_place, MescitActivity::class.java)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolsToolbar).apply {
            title = getString(R.string.menu_tools)
            setNavigationOnClickListener { finish() }
        }

        findViewById<RecyclerView>(R.id.toolsList).apply {
            layoutManager = LinearLayoutManager(this@ToolsActivity)
            adapter = ToolAdapter()
        }
    }

    private inner class ToolAdapter : RecyclerView.Adapter<ToolAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.toolIcon)
            val title: TextView = v.findViewById(R.id.toolTitle)
            val sub: TextView = v.findViewById(R.id.toolSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false))

        override fun getItemCount() = tools.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = tools[position]
            holder.icon.setImageResource(t.iconRes)
            holder.title.setText(t.titleRes)
            holder.sub.setText(t.subRes)
            holder.itemView.setOnClickListener {
                startActivity(Intent(this@ToolsActivity, t.target))
            }
        }
    }
}
