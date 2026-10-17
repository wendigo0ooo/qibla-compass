package com.wendigo.qibla

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FetvaActivity : AppCompatActivity() {

    private lateinit var queryField: EditText
    private lateinit var status: TextView
    private val adapter = FetvaAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fetva)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.fetvaToolbar).apply {
            title = getString(R.string.tab_fetva)
            setNavigationOnClickListener { finish() }
        }

        queryField = findViewById(R.id.etFetvaQuery)
        status = findViewById(R.id.fetvaStatus)

        val list = findViewById<RecyclerView>(R.id.fetvaList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<Button>(R.id.btnFetvaSearch).setOnClickListener { performSearch() }

        queryField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
        queryField.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = performSearch()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        Thread {
            val all = try {
                FetvaRepository.load(applicationContext)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread { adapter.setItems(all) }
        }.start()
    }

    private fun performSearch() {
        val q = queryField.text.toString()
        val res = FetvaRepository.search(applicationContext, q)
        adapter.setItems(res)
        status.text = when {
            q.isBlank() -> getString(R.string.fetva_sub)
            res.isEmpty() -> getString(R.string.fetva_none)
            else -> getString(R.string.fetva_count, res.size)
        }
    }

    private fun showFetva(f: FetvaRepository.Fetva) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        col.addView(TextView(this).apply {
            text = f.soru
            textSize = 16f
            setTextColor(getColor(R.color.accent))
            setPadding(0, 0, 0, pad / 2)
        })

        if (f.mezhep.isNotBlank()) {
            col.addView(TextView(this).apply {
                text = getString(R.string.fetva_mezhep) + ": " + f.mezhep
                textSize = 12f
                setTextColor(getColor(R.color.text_dim))
                setPadding(0, 0, 0, pad / 3)
            })
        }

        col.addView(TextView(this).apply {
            text = f.cevap
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            setLineSpacing(5f, 1f)
            setPadding(0, 0, 0, pad / 2)
        })

        col.addView(TextView(this).apply {
            text = f.kaynak
            textSize = 12f
            setTextColor(getColor(R.color.text_dim))
            gravity = Gravity.END
        })

        val scroll = ScrollView(this).apply { addView(col) }
        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class FetvaAdapter : RecyclerView.Adapter<FetvaAdapter.VH>() {
        private val items = ArrayList<FetvaRepository.Fetva>()

        fun setItems(list: List<FetvaRepository.Fetva>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val question: TextView = v.findViewById(R.id.fetvaQuestion)
            val tag: TextView = v.findViewById(R.id.fetvaTag)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_fetva, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            holder.question.text = f.soru
            holder.tag.text = if (f.mezhep.isBlank()) f.kategori else f.kategori + " • " + f.mezhep
            holder.itemView.setOnClickListener { showFetva(f) }
        }
    }
}
