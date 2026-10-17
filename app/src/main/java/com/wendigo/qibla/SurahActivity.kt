package com.wendigo.qibla

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    }

    private var player: ExoPlayer? = null
    private var surah: QuranRepository.Surah? = null
    private var ayahOffset = 0
    private var currentPlaying = -1
    private lateinit var list: RecyclerView
    private lateinit var adapter: AyahAdapter
    private lateinit var btnPlay: Button
    private var playColor = Color.TRANSPARENT
    private var accentColor = Color.BLACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah)

        val index = intent.getIntExtra(EXTRA_SURAH, 0)
        list = findViewById(R.id.ayahList)
        btnPlay = findViewById(R.id.btnPlaySurah)

        playColor = ContextCompat.getColor(this, R.color.ayah_playing)
        accentColor = ContextCompat.getColor(this, R.color.accent)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.surahToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = AyahAdapter(emptyList()) { pos -> playFrom(pos) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSurah = prefs.getInt(KEY_LAST_SURAH, -1)
        val lastAyah = prefs.getInt(KEY_LAST_AYAH, 0)

        Thread {
            val all = try {
                QuranRepository.load(applicationContext)
            } catch (_: Exception) {
                emptyList()
            }
            val s = all.getOrNull(index) ?: return@Thread
            val offset = all.take(index).sumOf { it.ayahs.size }
            runOnUiThread {
                surah = s
                ayahOffset = offset
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
        adapter = AyahAdapter(s.ayahs) { pos -> playFrom(pos) }
        list.adapter = adapter
    }

    private fun toggleAudio() {
        if (player != null) {
            stopAudio()
            return
        }
        if (surah == null) {
            Toast.makeText(this, R.string.quran_loading, Toast.LENGTH_SHORT).show()
            return
        }
        buildAndPlay(0, 0)
    }

    private fun playFrom(position: Int) {
        stopAudio()
        buildAndPlay(0, position)
    }

    private fun perAyahUrl(host: Int, position: Int): String {
        val s = surah!!
        return if (host == 0) {
            "https://everyayah.com/data/Alafasy_128kbps/${"%03d%03d".format(s.number, position + 1)}.mp3"
        } else {
            val global = ayahOffset + position + 1
            "https://cdn.islamic.network/quran/audio/128/ar.alafasy/$global.mp3"
        }
    }

    private fun buildAndPlay(host: Int, startPosition: Int) {
        val s = surah ?: return
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

        val items = (s.ayahs.indices).map { MediaItem.fromUri(perAyahUrl(host, it)) }
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                setPlaying(exo.currentMediaItemIndex)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        if (player === exo) btnPlay.text = getString(R.string.quran_stop)
                    }
                    Player.STATE_ENDED -> stopAudio()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val resume = exo.currentMediaItemIndex.coerceAtLeast(0)
                runCatching { exo.release() }
                if (player === exo) player = null
                if (host == 0) {
                    buildAndPlay(1, resume)
                } else {
                    Toast.makeText(
                        this@SurahActivity, R.string.quran_audio_error, Toast.LENGTH_LONG
                    ).show()
                    stopAudio()
                }
            }
        })
        exo.setMediaItems(items, startPosition.coerceIn(0, items.size - 1), 0L)
        exo.prepare()
        exo.playWhenReady = true
        setPlaying(startPosition)
    }

    private fun setPlaying(position: Int) {
        if (position == currentPlaying) return
        val old = currentPlaying
        currentPlaying = position
        adapter.playing = position
        if (old in 0 until adapter.itemCount) adapter.notifyItemChanged(old)
        if (position in 0 until adapter.itemCount) {
            adapter.notifyItemChanged(position)
            list.smoothScrollToPosition(position)
        }
    }

    private fun stopAudio() {
        val exo = player
        player = null
        runCatching { exo?.release() }
        btnPlay.text = getString(R.string.quran_play)
        val old = currentPlaying
        currentPlaying = -1
        if (old in 0 until adapter.itemCount) {
            adapter.playing = -1
            adapter.notifyItemChanged(old)
        }
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

    private inner class AyahAdapter(
        private val ayahs: List<QuranRepository.Ayah>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<AyahAdapter.VH>() {

        var playing = -1

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val row: View = v.findViewById(R.id.ayahRow)
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
            val on = position == playing
            holder.row.setBackgroundColor(if (on) playColor else Color.TRANSPARENT)
            holder.num.setTextColor(accentColor)
            holder.row.setOnClickListener { onClick(position) }
        }
    }
}
