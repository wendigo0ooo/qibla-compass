package com.wendigo.qibla

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HadisDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INDEX = "hadis_index"
    }

    private var index = -1
    private var hadis: HadisRepository.Hadis? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hadis_detail)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.detailToolbar).apply {
            title = getString(R.string.tab_hadis)
            setNavigationOnClickListener { finish() }
        }

        index = intent.getIntExtra(EXTRA_INDEX, -1)

        val category = findViewById<TextView>(R.id.detailCategory)
        val arabic = findViewById<TextView>(R.id.detailArabic)
        val turkish = findViewById<TextView>(R.id.detailTurkish)
        val ref = findViewById<TextView>(R.id.detailRef)
        val btnFav = findViewById<Button>(R.id.btnFav)

        Thread {
            val data = try {
                HadisRepository.load(applicationContext)
            } catch (_: Exception) {
                null
            }
            val h = data?.hadisler?.getOrNull(index)
            runOnUiThread {
                if (h == null) {
                    finish()
                    return@runOnUiThread
                }
                hadis = h
                category.text = h.category
                category.visibility = if (h.category.isBlank()) View.GONE else View.VISIBLE
                arabic.text = h.arabic
                arabic.visibility = if (h.arabic.isBlank()) View.GONE else View.VISIBLE
                turkish.text = h.turkish
                ref.text = h.reference
                updateFavButton(btnFav)
            }
        }.start()

        btnFav.setOnClickListener {
            if (index >= 0) {
                HadisRepository.toggleFav(this, index)
                updateFavButton(btnFav)
            }
        }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { hadis?.let { copyHadis(it) } }
        findViewById<Button>(R.id.btnShare).setOnClickListener { hadis?.let { shareHadis(it) } }
    }

    private fun updateFavButton(b: Button) {
        val fav = index >= 0 && HadisRepository.isFav(this, index)
        b.setText(if (fav) R.string.hadis_fav_remove else R.string.hadis_fav_add)
    }

    private fun copyHadis(h: HadisRepository.Hadis) {
        val text = "${h.turkish}\n\n— ${h.reference}"
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.tab_hadis), text))
        Toast.makeText(this, R.string.hadis_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareHadis(h: HadisRepository.Hadis) {
        val text = "${h.turkish}\n\n— ${h.reference}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.hadis_share)))
    }
}
