package com.wendigo.qibla

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture

class SurahActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SURAH = "surah_index"
        const val EXTRA_AUTOPLAY = "autoplay_ayah"
        const val KEY_AUDIO_SURAH = "audio_surah"
        const val KEY_AUDIO_AYAH = "audio_ayah"
        private const val PREFS = "qibla_prefs"
        private const val KEY_LAST_SURAH = "last_surah"
        private const val KEY_LAST_AYAH = "last_ayah"
    }

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var surah: QuranRepository.Surah? = null
    private var ayahOffset = 0
    private var currentPlaying = -1
    private var autoplayAyah = -1
    private var pendingStart: Int? = null
    private var currentHost = 0
    private lateinit var list: RecyclerView
    private lateinit var adapter: AyahAdapter
    private lateinit var btnPlay: Button
    private lateinit var btnRestart: Button
    private var playColor = Color.TRANSPARENT
    private var accentColor = Color.BLACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah)

        val index = intent.getIntExtra(EXTRA_SURAH, 0)
        autoplayAyah = intent.getIntExtra(EXTRA_AUTOPLAY, -1)
        list = findViewById(R.id.ayahList)
        btnPlay = findViewById(R.id.btnPlaySurah)
        btnRestart = findViewById(R.id.btnRestartSurah)

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
                if (autoplayAyah in s.ayahs.indices) {
                    pendingStart = autoplayAyah
                }
                maybePlayPending()
                updatePlayButton()
                if (pendingStart == null && isThisSurah()) {
                    setPlaying(controller?.currentMediaItemIndex ?: -1)
                }
            }
        }.start()

        btnPlay.setOnClickListener { onPlayClicked() }
        btnRestart.setOnClickListener { onRestartClicked() }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, QuranPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (_: Exception) {
                null
            }
            if (c != null && controllerFuture === future) {
                controller = c
                c.addListener(playerListener)
                onControllerConnected()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        super.onStop()
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun onControllerConnected() {
        updatePlayButton()
        if (pendingStart != null) {
            maybePlayPending()
        } else if (isThisSurah()) {
            setPlaying(controller?.currentMediaItemIndex ?: -1)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isThisSurah()) return
            val idx = controller?.currentMediaItemIndex ?: return
            if (idx >= 0) {
                setPlaying(idx)
                saveAudioPos(idx)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayButton()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                val old = currentPlaying
                currentPlaying = -1
                if (old in 0 until adapter.itemCount) {
                    adapter.playing = -1
                    adapter.notifyItemChanged(old)
                }
            }
            updatePlayButton()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError()
        }
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

    private fun onPlayClicked() {
        val s = surah
        if (s == null) {
            Toast.makeText(this, R.string.quran_loading, Toast.LENGTH_SHORT).show()
            return
        }
        val c = controller
        if (c == null) {
            pendingStart = savedStartIndex()
            btnPlay.text = getString(R.string.quran_buffering)
            return
        }
        if (isThisSurah() && c.mediaItemCount > 0 && c.playbackState != Player.STATE_ENDED) {
            if (c.isPlaying) c.pause() else c.play()
            updatePlayButton()
        } else {
            startList(c, s, savedStartIndex())
        }
    }

    private fun onRestartClicked() {
        val s = surah
        if (s == null) {
            Toast.makeText(this, R.string.quran_loading, Toast.LENGTH_SHORT).show()
            return
        }
        val c = controller
        if (c == null) {
            pendingStart = 0
            btnPlay.text = getString(R.string.quran_buffering)
            return
        }
        startList(c, s, 0)
    }

    private fun maybePlayPending() {
        val s = surah ?: return
        val c = controller ?: return
        val start = pendingStart ?: return
        pendingStart = null
        startList(c, s, start)
    }

    private fun startList(c: MediaController, s: QuranRepository.Surah, index: Int) {
        currentHost = 0
        val items = buildItems(s, 0)
        c.setMediaItems(items, index.coerceIn(0, items.size - 1), 0L)
        c.prepare()
        c.play()
        saveAudioPos(index.coerceIn(0, s.ayahs.size - 1))
        setPlaying(index)
        updatePlayButton()
    }

    private fun playFrom(position: Int) {
        val s = surah
        val c = controller
        if (s == null || c == null) {
            pendingStart = position
            return
        }
        startList(c, s, position)
    }

    private fun savedStartIndex(): Int {
        val s = surah ?: return 0
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedSurah = prefs.getInt(KEY_AUDIO_SURAH, -1)
        val savedAyah = prefs.getInt(KEY_AUDIO_AYAH, 0)
        return if (savedSurah == s.number - 1) savedAyah.coerceIn(0, s.ayahs.size - 1) else 0
    }

    private fun isThisSurah(): Boolean {
        val s = surah ?: return false
        return controller?.currentMediaItem?.mediaId == (s.number - 1).toString()
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

    private fun buildItems(s: QuranRepository.Surah, host: Int): List<MediaItem> {
        val reciter = getString(R.string.quran_reciter)
        return s.ayahs.indices.map { i ->
            val meta = MediaMetadata.Builder()
                .setTitle("${s.number}. ${s.name}")
                .setArtist(getString(R.string.quran_notif_ayah, i + 1) + " · " + reciter)
                .build()
            MediaItem.Builder()
                .setUri(perAyahUrl(host, i))
                .setMediaId((s.number - 1).toString())
                .setMediaMetadata(meta)
                .build()
        }
    }

    private fun handlePlaybackError() {
        val c = controller ?: return
        val s = surah
        if (currentHost == 0 && s != null) {
            currentHost = 1
            val resume = c.currentMediaItemIndex.coerceAtLeast(0)
            val items = buildItems(s, 1)
            c.setMediaItems(items, resume.coerceIn(0, items.size - 1), 0L)
            c.prepare()
            c.play()
            setPlaying(resume)
            return
        }
        Toast.makeText(this, R.string.quran_audio_error, Toast.LENGTH_LONG).show()
        c.stop()
        updatePlayButton()
    }

    private fun updatePlayButton() {
        if (!::btnPlay.isInitialized) return
        val c = controller
        btnPlay.text = if (c != null && isThisSurah() && c.isPlaying) {
            getString(R.string.quran_stop)
        } else {
            getString(R.string.quran_play)
        }
    }

    private fun saveAudioPos(position: Int) {
        val s = surah ?: return
        if (position < 0) return
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_AUDIO_SURAH, s.number - 1)
            putInt(KEY_AUDIO_AYAH, position)
        }
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

    override fun onPause() {
        super.onPause()
        val s = surah
        if (s != null) {
            if (currentPlaying >= 0) saveAudioPos(currentPlaying)
            val lm = list.layoutManager as? LinearLayoutManager
            val pos = lm?.findFirstVisibleItemPosition() ?: 0
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putInt(KEY_LAST_SURAH, s.number - 1)
                putInt(KEY_LAST_AYAH, pos.coerceAtLeast(0))
            }
        }
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
