package com.wendigo.qibla

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SurahActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SURAH = "surah_index"
        private const val PREFS = "qibla_prefs"
        private const val KEY_LAST_SURAH = "last_surah"
        private const val KEY_LAST_AYAH = "last_ayah"

        private val AUDIO_HOSTS = listOf(
            { n: Int -> "https://cdn.islamic.network/quran/audio-surah/128/ar.alafasy/$n.mp3" },
            { n: Int -> "https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaasee/${"%03d".format(n)}.mp3" },
            { n: Int -> "https://cdn.mp3quran.net/audio/mishary-alafasy/r1/${"%03d".format(n)}.mp3" }
        )
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playing = false
    private var surah: QuranRepository.Surah? = null
    private lateinit var list: RecyclerView
    private lateinit var adapter: AyahAdapter
    private lateinit var btnPlay: Button
    private var urlIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah)

        val index = intent.getIntExtra(EXTRA_SURAH, 0)
        list = findViewById(R.id.ayahList)
        btnPlay = findViewById(R.id.btnPlaySurah)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.surahToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = AyahAdapter(emptyList())
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSurah = prefs.getInt(KEY_LAST_SURAH, -1)
        val lastAyah = prefs.getInt(KEY_LAST_AYAH, 0)

        Thread {
            val s = try {
                QuranRepository.load(applicationContext).getOrNull(index)
            } catch (_: Exception) {
                null
            } ?: return@Thread
            runOnUiThread {
                surah = s
                bind(s)
                if (lastSurah == index && lastAyah in s.ayahs.indices) {
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(lastAyah, 0)
                }
            }
        }.start()

        btnPlay.setOnClickListener { toggleAudio() }
    }

    private fun bind(s: QuranRepository.Surah) {
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.surahToolbar).title =
            "${s.number}. ${s.name}"
        findViewById<TextView>(R.id.surahTitle).text = s.name
        findViewById<TextView>(R.id.surahSub).text =
            s.arName + "\n" + getString(R.string.surah_meta_fmt, s.ayahs.size, s.place)
        adapter = AyahAdapter(s.ayahs)
        list.adapter = adapter
    }

    private fun toggleAudio() {
        if (playing) {
            stopAudio()
            return
        }
        val s = surah ?: return
        urlIndex = 0
        tryStart(s.number)
    }

    private fun tryStart(number: Int) {
        if (urlIndex >= AUDIO_HOSTS.size) {
            Toast.makeText(this, R.string.quran_audio_error, Toast.LENGTH_SHORT).show()
            stopAudio()
            return
        }
        val url = AUDIO_HOSTS[urlIndex](number)
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
            mp.setOnCompletionListener { stopAudio() }
            mp.setOnErrorListener { _, _, _ ->
                mediaPlayer = null
                runCatching { mp.release() }
                urlIndex++
                tryStart(number)
                true
            }
            mediaPlayer = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            mediaPlayer = null
            urlIndex++
            tryStart(number)
        }
    }

    private fun stopAudio() {
        val mp = mediaPlayer
        mediaPlayer = null
        playing = false
        runCatching {
            if (mp != null) {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        btnPlay.text = getString(R.string.quran_play)
    }

    override fun onPause() {
        super.onPause()
        val s = surah
        if (s != null) {
            val lm = list.layoutManager as? LinearLayoutManager
            val pos = lm?.findFirstVisibleItemPosition() ?: 0
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putInt(KEY_LAST_SURAH, s.number - 1)
                putInt(KEY_LAST_AYAH, pos.coerceAtLeast(0))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }

    private class AyahAdapter(private val ayahs: List<QuranRepository.Ayah>) :
        RecyclerView.Adapter<AyahAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val num: TextView = v.findViewById(R.id.ayahNum)
            val arabic: TextView = v.findViewById(R.id.ayahArabic)
            val turkish: TextView = v.findViewById(R.id.ayahTr)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ayah, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = ayahs.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val a = ayahs[position]
            holder.num.text = (position + 1).toString()
            holder.arabic.text = a.ar
            holder.turkish.text = a.tr
        }
    }
}
