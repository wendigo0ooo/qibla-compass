package com.wendigo.qibla

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * "Günün içeriği": a daily ayah, hadith and dua, picked deterministically from the
 * bundled offline data sets by the day of the year.
 */
class GununActivity : AppCompatActivity() {

    private data class AyahItem(
        val surahName: String,
        val surahNumber: Int,
        val ayahNumber: Int,
        val arabic: String,
        val turkish: String
    )

    private val exec = Executors.newSingleThreadExecutor()
    private var mode = 0
    private var boost = 0

    private var ayahs: List<AyahItem> = emptyList()
    private var hadis: List<HadisRepository.Hadis> = emptyList()
    private var duas: List<DuaRepository.Dua> = emptyList()

    private lateinit var chips: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvArabic: TextView
    private lateinit var tvTurkish: TextView
    private lateinit var tvSource: TextView
    private lateinit var progress: ProgressBar

    private var currentShare = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gunun)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.gununToolbar).apply {
            title = getString(R.string.gunun_title)
            setNavigationOnClickListener { finish() }
        }

        chips = findViewById(R.id.gununChips)
        tvTitle = findViewById(R.id.gununTitle)
        tvArabic = findViewById(R.id.gununArabic)
        tvTurkish = findViewById(R.id.gununTurkish)
        tvSource = findViewById(R.id.gununSource)
        progress = findViewById(R.id.gununProgress)

        buildChips()

        findViewById<Button>(R.id.gununRefresh).setOnClickListener { boost += 1; render() }
        findViewById<Button>(R.id.gununCopy).setOnClickListener { copyCurrent() }
        findViewById<Button>(R.id.gununShare).setOnClickListener { shareCurrent() }

        progress.visibility = View.VISIBLE
        exec.execute {
            val a = loadAyahs()
            val h = try {
                HadisRepository.load(applicationContext).hadisler
            } catch (_: Exception) {
                emptyList()
            }
            val d = try {
                DuaRepository.load(applicationContext).duas.values.sortedBy { it.id }
            } catch (_: Exception) {
                emptyList()
            }
            runOnUiThread {
                ayahs = a
                hadis = h
                duas = d
                progress.visibility = View.GONE
                render()
            }
        }
    }

    private fun loadAyahs(): List<AyahItem> = try {
        val list = ArrayList<AyahItem>()
        for (s in QuranRepository.load(applicationContext)) {
            s.ayahs.forEachIndexed { i, ay ->
                list.add(AyahItem(s.name, s.number, i + 1, ay.ar, ay.tr))
            }
        }
        list
    } catch (_: Exception) {
        emptyList()
    }

    private fun buildChips() {
        chips.removeAllViews()
        addChip(getString(R.string.gunun_tab_ayah), 0)
        addChip(getString(R.string.gunun_tab_hadis), 1)
        addChip(getString(R.string.gunun_tab_dua), 2)
    }

    private fun addChip(label: String, value: Int) {
        val tv = TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
            setOnClickListener {
                mode = value
                boost = 0
                render()
            }
        }
        chips.addView(tv)
    }

    private fun refreshChips() {
        for (i in 0 until chips.childCount) {
            val tv = chips.getChildAt(i) as TextView
            val sel = i == mode
            tv.setBackgroundResource(if (sel) R.drawable.chip_bg_selected else R.drawable.chip_bg)
            tv.setTextColor(getColor(if (sel) android.R.color.white else R.color.text_primary))
        }
    }

    private fun dayIndex(mod: Int): Int {
        if (mod <= 0) return 0
        val doy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return (doy + boost) % mod
    }

    private fun render() {
        refreshChips()
        when (mode) {
            0 -> renderAyah()
            1 -> renderHadis()
            else -> renderDua()
        }
    }

    private fun renderAyah() {
        if (ayahs.isEmpty()) {
            showEmpty()
            return
        }
        val a = ayahs[dayIndex(ayahs.size)]
        tvTitle.text = getString(R.string.gunun_ayah_header, a.surahName, a.ayahNumber)
        tvArabic.text = a.arabic
        tvArabic.visibility = View.VISIBLE
        tvTurkish.text = a.turkish
        tvSource.text = "Elmalılı Hamdi Yazır"
        currentShare = "${tvTitle.text}\n\n${a.arabic}\n\n${a.turkish}\n\n${tvSource.text}"
    }

    private fun renderHadis() {
        if (hadis.isEmpty()) {
            showEmpty()
            return
        }
        val h = hadis[dayIndex(hadis.size)]
        tvTitle.text = if (h.category.isBlank()) {
            getString(R.string.gunun_tab_hadis)
        } else {
            getString(R.string.gunun_hadis_header, h.category)
        }
        tvArabic.text = h.arabic
        tvArabic.visibility = if (h.arabic.isBlank()) View.GONE else View.VISIBLE
        tvTurkish.text = h.turkish
        tvSource.text = h.reference
        currentShare = "${tvTitle.text}\n\n${h.arabic}\n\n${h.turkish}\n\n${h.reference}"
    }

    private fun renderDua() {
        if (duas.isEmpty()) {
            showEmpty()
            return
        }
        val d = duas[dayIndex(duas.size)]
        tvTitle.text = getString(R.string.gunun_dua_header, d.title)
        tvArabic.text = d.arabic
        tvArabic.visibility = if (d.arabic.isBlank()) View.GONE else View.VISIBLE
        val body = buildString {
            if (d.okunus.isNotBlank()) append(d.okunus).append("\n\n")
            append(d.meal)
        }
        tvTurkish.text = body
        tvSource.text = d.reference
        currentShare = "${tvTitle.text}\n\n${d.arabic}\n\n$body\n\n${d.reference}"
    }

    private fun showEmpty() {
        tvTitle.text = getString(R.string.gunun_title)
        tvArabic.visibility = View.GONE
        tvTurkish.text = getString(R.string.gunun_loading)
        tvSource.text = ""
        currentShare = ""
    }

    private fun copyCurrent() {
        if (currentShare.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.gunun_title), currentShare))
        Toast.makeText(this, R.string.gunun_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrent() {
        if (currentShare.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentShare)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.gunun_share)))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdown()
    }
}
