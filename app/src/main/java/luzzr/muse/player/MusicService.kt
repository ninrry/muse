package luzzr.muse.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import luzzr.muse.MainActivity
import luzzr.muse.R
import luzzr.muse.core.log.MuseLog
import luzzr.muse.ui.theme.MuseBrandBrownInt
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MusicService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "muse_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "luzzr.muse.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "luzzr.muse.action.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "luzzr.muse.action.SKIP_PREV"
        const val ACTION_TOGGLE_FLOATING_LYRICS = "luzzr.muse.action.TOGGLE_FLOATING_LYRICS"
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var progressJob: Job? = null

    @Inject lateinit var playerState: PlayerState

    @Inject lateinit var repository: luzzr.muse.data.repository.MusicRepositoryFacade

    /** Cached album art bitmap for notification large icon (loaded async per song) */
    private var cachedArtworkBitmap: Bitmap? = null

    /** Dynamic notification color extracted from album art (falls back to theme brown) */
    private var notificationColor: Int = MuseBrandBrownInt

    override fun onCreate() {
        super.onCreate()

        // Hilt member injection is complete after super.onCreate().
        // Initialize session persistence �?allows recovery after process death
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

        // Restore last session only when the service was started without a
        // fresh playback request. Pending play operations run during attach.
        if (playerState.currentPlaylist.value.isEmpty() && playerState.hasSavedSession()) {
            restoreLastSession()
        }
    }

    /** Restore the last playing playlist from SharedPreferences after process death */
    private fun restoreLastSession() {
        val ids = playerState.getSavedPlaylistIds()
        if (ids.isEmpty()) return
        val (savedIndex, savedPos) = playerState.getSavedPlaybackInfo()
        MuseLog.w("MusicService", "restoreLastSession: restoring ${ids.size} songs, index=$savedIndex, pos=$savedPos")
        serviceScope.launch {
            try {
                val repo = repository
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
                        MuseLog.w("MusicService", "restoreLastSession: restoring shuffle mode")
                        player?.shuffleModeEnabled = true
                    }
                    // Pause at the restored position; user taps to resume
                    player?.pause()
                    MuseLog.w("MusicService", "restoreLastSession: restored ${savedSongs.size} songs, paused at $savedPos")
                } else {
                    MuseLog.w("MusicService", "restoreLastSession: no matching songs found in DB, clearing session")
                    playerState.clearSavedSession()
                }
            } catch (e: Exception) {
                MuseLog.e("MusicService", "restoreLastSession failed", e)
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
            ACTION_TOGGLE_FLOATING_LYRICS -> toggleFloatingLyrics()
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
        MuseLog.w(
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
                    playerState.updateProgress(it.currentPosition.coerceAtLeast(0))
                    val duration = if (it.duration == C.TIME_UNSET) 0L else it.duration.coerceAtLeast(0)
                    playerState.updateDuration(duration)
                    delay(if (it.isPlaying) 50 else 250)
                } ?: delay(250)
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
            val song = list.getOrNull(index)
                ?: mediaItem?.mediaId?.toLongOrNull()?.let { mediaId ->
                    list.find { it.id == mediaId }
                }

            if (song == null) {
                if (mediaItem == null && list.isEmpty()) {
                    playerState.updateCurrentSong(null)
                    cachedArtworkBitmap = null
                    notificationColor = MuseBrandBrownInt
                    updateNotification()
                }
                return
            }

            playerState.updateCurrentSong(song)
            playerState.saveSession()
            serviceScope.launch {
                try {
                    val repo = repository
                    repo.generateDefaultCoverForSong(song)
                    val refreshed = repo.songs.value.find { it.id == song.id }
                    if (refreshed != null && refreshed.artworkUri != null) {
                        playerState.updateSongInPlaylist(index, refreshed)
                    }
                } catch (e: Exception) {
                    MuseLog.e("MusicService", "cover generation failed", e)
                }
            }
            updateNotification()

            // Load album art for notification large icon
            loadArtworkBitmapAsync(song)

            if (playerState.sleepTimer.activeMode.value == SleepTimerMode.END_OF_TRACK) {
                player?.pause()
                playerState.sleepTimer.stop()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                player?.duration?.let {
                    playerState.updateDuration(if (it == C.TIME_UNSET) 0L else it.coerceAtLeast(0))
                }
            }
            if (state == Player.STATE_ENDED) {
                val p = player
                if (p != null) {
                    if (playerState.shuffleMode.value) {
                        playerState.regenerateShuffleQueueAndPlay()
                    } else {
                        // Sequential playback loop: don't stop at the end, cycle back to the first track
                        p.seekTo(0, 0)
                        p.prepare()
                        p.play()
                    }
                } else {
                    playerState.updateIsPlaying(false)
                }
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

    /**
     * Load album art bitmap asynchronously for the notification large icon.
     * Also extracts a vibrant dominant color for dynamic notification tinting.
     * Triggers notification update once loaded.
     */
    private fun loadArtworkBitmapAsync(song: luzzr.muse.data.model.Song) {
        serviceScope.launch {
            try {
                val uri = song.artworkUri
                if (uri == null) {
                    cachedArtworkBitmap = null
                    notificationColor = MuseBrandBrownInt
                    updateNotification()
                    return@launch
                }
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
                    inputStream.use { BitmapFactory.decodeStream(it) }
                }
                if (bitmap != null) {
                    cachedArtworkBitmap = bitmap
                    notificationColor = withContext(Dispatchers.Default) { extractVibrantColor(bitmap) }
                    updateNotification()
                }
            } catch (e: Exception) {
                MuseLog.w("MusicService", "Failed to load notification artwork", e)
                cachedArtworkBitmap = null
                notificationColor = MuseBrandBrownInt
                updateNotification()
            }
        }
    }

    /**
     * Extract a vibrant dominant color from a bitmap for notification tinting.
     * Samples pixels at regular intervals, favoring high-saturation medium-lightness colors.
     * Falls back gracefully: any extraction error returns a safe default.
     */
    private fun extractVibrantColor(bitmap: Bitmap): Int {
        return try {
            val step = max(1, minOf(bitmap.width, bitmap.height) / 8)
            var bestColor = MuseBrandBrownInt
            var bestScore = -1f

            for (x in 0 until bitmap.width step step) {
                for (y in 0 until bitmap.height step step) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    val mx = maxOf(r, g, b)
                    val mn = minOf(r, g, b)
                    val saturation = if (mx == 0) 0f else (mx - mn).toFloat() / mx
                    val lightness = mx / 255f

                    // Score: prefer vibrant (high sat, ~mid lightness)
                    val score = saturation * (1f - 2f * abs(lightness - 0.5f))
                    if (score > bestScore) {
                        bestScore = score
                        bestColor = Color.rgb(r, g, b)
                    }
                }
            }
            bestColor
        } catch (_: Exception) {
            MuseBrandBrownInt // fallback
        }
    }

    /**
     * Toggle floating lyrics overlay on/off.
     * Checks overlay permission and starts/stops FloatingLyricsService.
     */
    private fun toggleFloatingLyrics() {
        val enabled = !playerState.floatingLyricsEnabled.value
        playerState.updateFloatingLyricsEnabled(enabled)

        if (enabled) {
            // Check SYSTEM_ALERT_WINDOW permission on Android 6+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    MuseLog.w("MusicService", "toggleFloatingLyrics: SYSTEM_ALERT_WINDOW not granted")
                    playerState.updateFloatingLyricsEnabled(false)
                    // Fallback: open settings for the user to grant permission
                    val intent = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION.let {
                        android.content.Intent(it, android.net.Uri.parse("package:$packageName"))
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return
                }
            }
            startService(
                Intent(this, FloatingLyricsService::class.java).apply {
                    action = FloatingLyricsService.ACTION_SHOW
                }
            )
        } else {
            startService(
                Intent(this, FloatingLyricsService::class.java).apply {
                    action = FloatingLyricsService.ACTION_HIDE
                }
            )
        }
        updateNotification()
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification(): Notification {
        val song = playerState.currentSong.value
        val isPlaying = playerState.isPlaying.value
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Previous
        val prevIntent = Intent(this, MusicService::class.java).apply { action = ACTION_SKIP_PREV }
        val prevPending = PendingIntent.getForegroundService(
            this,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause
        val ppIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val ppPending = PendingIntent.getForegroundService(
            this,
            2,
            ppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Next
        val nextIntent = Intent(this, MusicService::class.java).apply { action = ACTION_SKIP_NEXT }
        val nextPending = PendingIntent.getForegroundService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: getString(R.string.app_name))
            .setContentText(song?.artist ?: "")
            .setSubText(song?.album)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(cachedArtworkBitmap)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setColorized(true)
            .setColor(notificationColor)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1)
            )
            // Progress bar for current playback position
            .also { b ->
                val dur = playerState.duration.value
                val prog = playerState.progress.value
                if (dur > 0) {
                    b.setProgress(dur.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), prog.coerceAtMost(dur).toInt(), false)
                } else {
                    b.setProgress(0, 0, false)
                }
            }
            // Play/Pause (index 0) �?always in compact view, highest priority
            .addAction(
                NotificationCompat.Action.Builder(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (isPlaying) getString(R.string.player_pause) else getString(R.string.player_play),
                    ppPending
                ).build()
            )
            // Next (index 1) — always in compact view (HyperOS priority order)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_next,
                    getString(R.string.player_next),
                    nextPending
                ).build()
            )
            // Previous (index 2) — expanded view only on HyperOS (compact shows 2 max)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_prev,
                    getString(R.string.player_prev),
                    prevPending
                ).build()
            )
            // Next (index 1) �?always in compact view (HyperOS priority order)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_next,
                    "下一首",
                    nextPending
                ).build()
            )
            // Previous (index 2) — expanded view only on HyperOS (compact shows 2 max)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_prev,
                    "上一首",
                    prevPending
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
        cachedArtworkBitmap?.recycle()
        cachedArtworkBitmap = null
        notificationColor = MuseBrandBrownInt
        playerState.detachPlayer()
        mediaSession?.release()
        player?.release()
        player = null
        super.onDestroy()
    }
}
