package com.wendigo.qibla

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps the Qur'an recitation playing in the background and shows the system
 * media notification (title = surah, subtitle = ayah) with play/pause and stop.
 *
 * The current surah/ayah is persisted here (not only from the UI) so the
 * "resume" card keeps tracking progress even while the app is in the background.
 */
class QuranPlaybackService : MediaSessionService() {

    companion object {
        private const val PREFS = "qibla_prefs"
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                savePosition(player)
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    private fun savePosition(player: Player) {
        val surahIndex = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        val ayahIndex = player.currentMediaItemIndex
        if (ayahIndex < 0) return
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(SurahActivity.KEY_AUDIO_SURAH, surahIndex)
            putInt(SurahActivity.KEY_AUDIO_AYAH, ayahIndex)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
