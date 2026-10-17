package com.wendigo.qibla

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit

class SurahActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SURAH = "surah_index"
        private const val PREFS = "qibla_prefs"
        private const val KEY_LAST_SURAH = "last_surah"
        private const val KEY_LAST_AYAH = "last_ayah"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playing = false
    private var surah: QuranRepository.Surah? = null
    private lateinit var listView: ListView
    private lateinit var btnPlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah)

        val index = intent.getIntExtra(EXTRA_SURAH, 0)
        listView = findViewById(R.id.ayahList)
        btnPlay = findViewById(R.id.btnPlaySurah)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.surahToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSurah = prefs.getInt(KEY_LAST_SURAH, -1)
        val lastAyah = prefs.getInt(KEY_LAST_AYAH, 0)

        Thread {
            val list = QuranRepository.load(applicationContext)
            val s = list.getOrNull(index) ?: return@Thread
            runOnUiThread {
                surah = s
                bind(s)
                if (lastSurah == index && lastAyah in s.ayahs.indices) {
                    listView.setSelection(lastAyah)
                }
            }
        }.start()

        btnPlay.setOnClickListener { toggleAudio() }
    }

    private fun bind(s: QuranRepository.Surah) {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.surahToolbar)
        toolbar.title = "${s.number}. ${s.name}"
        findViewById<TextView>(R.id.surahTitle).text = s.name
        findViewById<TextView>(R.id.surahSub).text =
            "$s.arName\n" + getString(R.string.surah_meta_fmt, s.ayahs.size, s.place)
        listView.adapter = AyahAdapter(s.ayahs)
    }

    private fun toggleAudio() {
        if (playing) {
            stopAudio()
            return
        }
        val s = surah ?: return
        val url = "https://cdn.islamic.network/quran/audio-surah/128/ar.alafasy/${s.number}.mp3"
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                it.start()
                playing = true
                btnPlay.text = getString(R.string.quran_stop)
            }
            mp.setOnCompletionListener {
                stopAudio()
            }
            mp.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, R.string.quran_audio_error, Toast.LENGTH_SHORT).show()
                stopAudio()
                true
            }
            mediaPlayer = mp
            mp.prepareAsync()
            Toast.makeText(this, R.string.quran_loading, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.quran_audio_error, Toast.LENGTH_SHORT).show()
            stopAudio()
        }
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
        playing = false
        btnPlay.text = getString(R.string.quran_play)
    }

    override fun onPause() {
        super.onPause()
        val s = surah
        if (s != null) {
            val pos = listView.firstVisiblePosition
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putInt(KEY_LAST_SURAH, s.number - 1)
                putInt(KEY_LAST_AYAH, pos)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }

    private inner class AyahAdapter(private val ayahs: List<QuranRepository.Ayah>) : BaseAdapter() {
        override fun getCount() = ayahs.size
        override fun getItem(position: Int) = ayahs[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_ayah, parent, false)
            val a = ayahs[position]
            v.findViewById<TextView>(R.id.ayahNum).text = "${position + 1}"
            v.findViewById<TextView>(R.id.ayahArabic).text = a.ar
            v.findViewById<TextView>(R.id.ayahTr).text = a.tr
            return v
        }
    }
}
