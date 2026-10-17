package com.wendigo.qibla

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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

    private var player: ExoPlayer? = null
    private var hostIndex = 0
    private var surah: QuranRepository.Surah? = null
    private lateinit var list: RecyclerView
    private lateinit var adapter: AyahAdapter
    private lateinit var btnPlay: Button

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
                    (list.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(lastAyah, 0)
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
        if (player != null) {
            stopAudio()
            return
        }
        val s = surah
        if (s == null) {
            Toast.makeText(this, R.string.quran_loading, Toast.LENGTH_SHORT).show()
            return
        }
        hostIndex = 0
        startHost(s.number)
    }

    private fun startHost(number: Int) {
        if (hostIndex >= AUDIO_HOSTS.size) {
            Toast.makeText(this, R.string.quran_audio_error, Toast.LENGTH_LONG).show()
            stopAudio()
            return
        }
        val url = AUDIO_HOSTS[hostIndex](number)
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build()
        player = exo
        btnPlay.text = getString(R.string.quran_buffering)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        if (player === exo) btnPlay.text = getString(R.string.quran_stop)
                    }
                    Player.STATE_ENDED -> stopAudio()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                runCatching { exo.release() }
                if (player === exo) player = null
                hostIndex++
                startHost(number)
            }
        })
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun stopAudio() {
        val exo = player
        player = null
        runCatching { exo?.release() }
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
