package luzzr.muse.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import luzzr.muse.MainActivity
import luzzr.muse.MuseApp
import luzzr.muse.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "muse_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "luzzr.muse.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "luzzr.muse.action.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "luzzr.muse.action.SKIP_PREV"
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var progressJob: Job? = null
    private lateinit var playerState: PlayerState

    override fun onCreate() {
        super.onCreate()

        playerState = (application as MuseApp).playerState

        // Initialize session persistence — allows recovery after process death
        playerState.initSessionPrefs(getSharedPreferences("player_session", MODE_PRIVATE))

        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().also {
                it.addListener(ServicePlayerListener())
                playerState.attachPlayer(it)
            }

        mediaSession = player?.let { MediaSession.Builder(this, it).build() }

        startProgressUpdate()

        // Sleep timer hook: pause when countdown reaches zero
        playerState.sleepTimer.onTimerElapsed = {
            player?.pause()
        }

        // Restore last session if process was killed while playing
        if (playerState.hasSavedSession()) {
            restoreLastSession()
        }
    }

    /** Restore the last playing playlist from SharedPreferences after process death */
    private fun restoreLastSession() {
        val ids = playerState.getSavedPlaylistIds()
        if (ids.isEmpty()) return
        val (savedIndex, savedPos) = playerState.getSavedPlaybackInfo()
        android.util.Log.w("MusicService", "restoreLastSession: restoring ${ids.size} songs, index=$savedIndex, pos=$savedPos")
        serviceScope.launch {
            try {
                val repo = luzzr.muse.data.repository.MusicRepository.getInstance(this@MusicService)
                // Ensure songs are scanned first
                val allSongs = if (repo.songs.value.isEmpty()) repo.scanAll() else repo.songs.value
                val savedSongs = ids.mapNotNull { id -> allSongs.find { it.id == id } }
                if (savedSongs.isNotEmpty()) {
                    playerState.playSongs(savedSongs, savedIndex.coerceIn(0, savedSongs.size - 1))
                    if (savedPos > 0) {
                        player?.seekTo(savedPos)
                    }
                    // Restore shuffle mode from saved session
                    if (playerState.getSavedShuffleMode()) {
                        android.util.Log.w("MusicService", "restoreLastSession: restoring shuffle mode")
                        player?.shuffleModeEnabled = true
                    }
                    // Pause at the restored position; user taps to resume
                    player?.pause()
                    android.util.Log.w("MusicService", "restoreLastSession: restored ${savedSongs.size} songs, paused at $savedPos")
                } else {
                    android.util.Log.w("MusicService", "restoreLastSession: no matching songs found in DB, clearing session")
                    playerState.clearSavedSession()
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "restoreLastSession failed", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Handle notification action intents
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playerState.togglePlayPause()
            ACTION_SKIP_NEXT -> playerState.skipToNext()
            ACTION_SKIP_PREV -> playerState.skipToPrevious()
        }

        // Start foreground immediately to prevent ANR on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        android.util.Log.w(
            "MusicService",
            "onTaskRemoved: playWhenReady=${player?.playWhenReady} isPlaying=${player?.isPlaying}"
        )
        playerState.clearPendingOperations()

        // Save current session before potential process death
        playerState.saveSession()

        val p = player
        if (p != null && !p.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startProgressUpdate() {
        progressJob = serviceScope.launch {
            while (isActive) {
                player?.let {
                    if (it.isPlaying) {
                        playerState.updateProgress(it.currentPosition.coerceAtLeast(0))
                        playerState.updateDuration(it.duration.coerceAtLeast(0))
                    }
                }
                delay(500)
            }
        }
    }

    private inner class ServicePlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playerState.updateIsPlaying(isPlaying)
            updateNotification()
        }

        override fun onRepeatModeChanged(mode: Int) {
            playerState.updateRepeatMode(mode)
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            playerState.updateShuffleMode(enabled)
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val index = player?.currentMediaItemIndex ?: -1
            val list = playerState.currentPlaylist.value
            if (index in list.indices) {
                playerState.updateCurrentSong(list[index])
                playerState.saveSession()
                serviceScope.launch {
                    try {
                        val repo = luzzr.muse.data.repository.MusicRepository.getInstance(this@MusicService)
                        repo.generateDefaultCoverForSong(list[index])
                        val refreshed = repo.songs.value.find { it.id == list[index].id }
                        if (refreshed != null && refreshed.artworkUri != null) {
                            playerState.updateSongInPlaylist(index, refreshed)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicService", "cover generation failed", e)
                    }
                }
            }
            updateNotification()

            if (playerState.sleepTimer.activeMode.value == SleepTimerMode.END_OF_TRACK) {
                player?.pause()
                playerState.sleepTimer.stop()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                player?.duration?.coerceAtLeast(0)?.let { playerState.updateDuration(it) }
            }
            if (state == Player.STATE_ENDED) {
                playerState.updateIsPlaying(false)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val song = playerState.currentSong.value
        val isPlaying = playerState.isPlaying.value
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous
        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_SKIP_PREV }
        val prevPending = PendingIntent.getForegroundService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause
        val ppIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val ppPending = PendingIntent.getForegroundService(
            this, 2, ppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next
        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_SKIP_NEXT }
        val nextPending = PendingIntent.getForegroundService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: getString(R.string.app_name))
            .setContentText(song?.artist ?: "")
            .setSubText(song?.album)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setColorized(true)
            .setColor(0xFF8B7355.toInt())
            // Previous
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_previous, "上一首", prevPending
                ).build()
            )
            // Play/Pause
            .addAction(
                NotificationCompat.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "暂停" else "播放",
                    ppPending
                ).build()
            )
            // Next
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_next, "下一首", nextPending
                ).build()
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        playerState.saveSession()
        progressJob?.cancel()
        serviceScope.cancel()
        playerState.detachPlayer()
        mediaSession?.release()
        player?.release()
        player = null
        super.onDestroy()
    }
}
