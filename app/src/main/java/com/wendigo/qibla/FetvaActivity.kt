package com.wendigo.qibla

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URLEncoder

class FetvaActivity : AppCompatActivity() {

    private lateinit var queryField: EditText
    private lateinit var status: TextView
    private lateinit var webButton: Button
    private lateinit var chips: LinearLayout
    private val adapter = FetvaAdapter()

    private var all: List<FetvaRepository.Fetva> = emptyList()
    private var selectedCat: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fetva)

        AppBackground.apply(this, findViewById(R.id.bgImage), findViewById(R.id.bgScrim))

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.fetvaToolbar).apply {
            title = getString(R.string.tab_fetva)
            setNavigationOnClickListener { finish() }
        }

        queryField = findViewById(R.id.etFetvaQuery)
        status = findViewById(R.id.fetvaStatus)
        webButton = findViewById(R.id.btnFetvaWeb)
        chips = findViewById(R.id.fetvaChips)

        val list = findViewById<RecyclerView>(R.id.fetvaList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<Button>(R.id.btnFetvaSearch).setOnClickListener { performSearch() }
        webButton.setOnClickListener { openWebSearch() }

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
            val loaded = try {
                FetvaRepository.load(applicationContext)
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                all = loaded
                buildChips(loaded.map { it.kategori }.distinct())
                performSearch()
            }
        }.start()
    }

    private fun buildChips(categories: List<String>) {
        chips.removeAllViews()
        addChip(getString(R.string.fetva_all), null)
        for (c in categories) addChip(c, c)
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
                performSearch()
            }
        }
        chips.addView(tv)
    }

    private fun refreshChips() {
        for (i in 0 until chips.childCount) {
            val tv = chips.getChildAt(i) as TextView
            val sel = (tv.tag as String?) == selectedCat
            tv.setBackgroundResource(if (sel) R.drawable.chip_bg_selected else R.drawable.chip_bg)
            tv.setTextColor(getColor(if (sel) android.R.color.white else R.color.text_primary))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun performSearch() {
        val q = queryField.text.toString()
        var res = FetvaRepository.search(applicationContext, q)
        val cat = selectedCat
        if (cat != null) res = res.filter { it.kategori == cat }
        adapter.setItems(res)
        status.text = when {
            q.isBlank() && cat == null -> getString(R.string.fetva_sub)
            res.isEmpty() -> getString(R.string.fetva_none)
            else -> getString(R.string.fetva_count, res.size)
        }
        webButton.visibility = if (q.isBlank()) View.GONE else View.VISIBLE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Lets the user pick a web source for the current query, then opens the browser. */
    private fun openWebSearch() {
        val q = queryField.text.toString().trim()
        if (q.isBlank()) {
            Toast.makeText(this, getString(R.string.fetva_web_query), Toast.LENGTH_SHORT).show()
            return
        }
        if (!isOnline()) {
            Toast.makeText(this, getString(R.string.fetva_no_internet), Toast.LENGTH_LONG).show()
            return
        }
        val options = arrayOf(
            getString(R.string.fetva_web_general),
            getString(R.string.fetva_web_diyanet),
            getString(R.string.fetva_web_youtube)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.fetva_web_title)
            .setItems(options) { _, which ->
                val url = when (which) {
                    1 -> googleUrl("$q site:diyanet.gov.tr")
                    2 -> "https://www.youtube.com/results?search_query=${URLEncoder.encode(q, "UTF-8")}"
                    else -> googleUrl(q)
                }
                openUrl(url)
            }
            .show()
    }

    private fun googleUrl(query: String): String =
        "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.fetva_no_browser), Toast.LENGTH_SHORT).show()
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
