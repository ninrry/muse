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
import androidx.core.graphics.get
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Format
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.Renderer
import android.os.Handler
import java.util.ArrayList
import dagger.hilt.android.AndroidEntryPoint
import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.MediaClassifier
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.R
import luzzr.muse.media.SleepTimerMode
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

    @Inject lateinit var songRepository: SongRepository

    @Inject lateinit var artworkRepository: ArtworkRepository

    /** Cached album art bitmap for notification large icon (loaded async per song) */
    private var cachedArtworkBitmap: Bitmap? = null

    /** Dynamic notification color extracted from album art (falls back to theme brown) */
    private var notificationColor: Int = DefaultMediaNotificationColor

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Hilt member injection is complete after super.onCreate().
        // Initialize session persistence ->allows recovery after process death
        playerState.initSessionPrefs(getSharedPreferences("player_session", MODE_PRIVATE))

        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioProcessorChain(
                            arrayOf(ToInt16PcmAudioProcessor()),
                            SilenceSkippingAudioProcessor(),
                            SonicAudioProcessor()
                        )
                    )
                    .build()
            }

            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>
            ) {
                val tempOut = ArrayList<Renderer>()
                super.buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    eventListener,
                    tempOut
                )
                for (renderer in tempOut) {
                    if (renderer is MediaCodecAudioRenderer && renderer !is CustomMediaCodecAudioRenderer) {
                        out.add(
                            CustomMediaCodecAudioRenderer(
                                context,
                                mediaCodecSelector,
                                enableDecoderFallback,
                                eventHandler,
                                eventListener,
                                audioSink
                            )
                        )
                    } else {
                        out.add(renderer)
                    }
                }
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
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
                // Ensure songs are scanned first
                val allSongs = if (songRepository.songs.value.isEmpty()) {
                    songRepository.scanAll()
                } else {
                    songRepository.songs.value
                }
                val savedSongs = ids.mapNotNull { id -> allSongs.find { it.id == id } }
                if (savedSongs.isNotEmpty()) {
                    playerState.playSongs(savedSongs, savedIndex.coerceIn(0, savedSongs.size - 1))
                    if (savedPos > 0) {
                        player?.seekTo(savedPos)
                    }
                    // Restore shuffle mode and repeat mode from saved session
                    val savedShuffle = playerState.getSavedShuffleMode()
                    player?.shuffleModeEnabled = savedShuffle
                    val savedRepeat = playerState.getSavedRepeatMode()
                    playerState.setRepeatMode(savedRepeat)
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
            var ticks = 0
            while (isActive) {
                player?.let { p ->
                    val pos = p.currentPosition.coerceAtLeast(0)
                    playerState.updateProgress(pos)
                    val duration = if (p.duration == C.TIME_UNSET) 0L else p.duration.coerceAtLeast(0)
                    playerState.updateDuration(duration)

                    if (p.isPlaying) {
                        ticks++
                        if (ticks >= 12) {
                            ticks = 0
                            val currentSong = playerState.currentSong.value
                            if (currentSong != null && MediaClassifier.isAudiobook(currentSong)) {
                                val savedPos = if (pos >= duration - 10_000) 0L else pos
                                playerState.saveSongProgress(currentSong.id, savedPos)
                            }
                        }
                    }

                    delay(if (p.isPlaying) 250 else 500)
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
            val p = player
            val index = p?.currentMediaItemIndex ?: -1
            val list = playerState.currentPlaylist.value

            // 1. Save progress of the previous audiobook before transitioning
            val previousSong = playerState.currentSong.value
            if (previousSong != null && MediaClassifier.isAudiobook(previousSong)) {
                val lastPos = playerState.progress.value
                val lastDur = playerState.duration.value
                val savedPos = if (lastDur > 0 && lastPos >= lastDur - 10_000) 0L else lastPos
                playerState.saveSongProgress(previousSong.id, savedPos)
            }

            val song = list.getOrNull(index)
                ?: mediaItem?.mediaId?.toLongOrNull()?.let { mediaId ->
                    list.find { it.id == mediaId }
                }

            if (song == null) {
                if (mediaItem == null && list.isEmpty()) {
                    playerState.updateCurrentSong(null)
                    cachedArtworkBitmap = null
                    notificationColor = DefaultMediaNotificationColor
                    updateNotification()
                }
                return
            }

            playerState.updateCurrentSong(song)
            playerState.saveSession()
            if (MediaClassifier.isAudiobook(song)) {
                playerState.updateSongLastPlayedTime(song.id)
            }

            // 2. Restore progress for the new audiobook
            if (MediaClassifier.isAudiobook(song) && p != null) {
                val savedProgress = playerState.getSavedSongProgress(song.id)
                if (savedProgress > 0 && kotlin.math.abs(p.currentPosition - savedProgress) > 5000) {
                    p.seekTo(index, savedProgress)
                }
            }
            serviceScope.launch {
                try {
                    artworkRepository.generateDefaultCoverForSong(song)
                    val refreshed = songRepository.songs.value.find { it.id == song.id }
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
                    p.pause()
                    p.seekTo(0, 0)
                }
                playerState.updateIsPlaying(false)
            }
        }
    }

    private fun createNotificationChannel() {
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

    /**
     * Load album art bitmap asynchronously for the notification large icon.
     * Also extracts a vibrant dominant color for dynamic notification tinting.
     * Triggers notification update once loaded.
     */
    private fun loadArtworkBitmapAsync(song: Song) {
        serviceScope.launch {
            try {
                val uri = song.artworkUri
                if (uri == null) {
                    cachedArtworkBitmap = null
                    notificationColor = DefaultMediaNotificationColor
                    updateNotification()
                    return@launch
                }
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri.toUri()) ?: return@withContext null
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
                notificationColor = DefaultMediaNotificationColor
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
            var bestColor = DefaultMediaNotificationColor
            var bestScore = -1f

            for (x in 0 until bitmap.width step step) {
                for (y in 0 until bitmap.height step step) {
                    val pixel = bitmap[x, y]
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
            DefaultMediaNotificationColor
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
            if (!android.provider.Settings.canDrawOverlays(this)) {
                MuseLog.w("MusicService", "toggleFloatingLyrics: SYSTEM_ALERT_WINDOW not granted")
                playerState.updateFloatingLyricsEnabled(false)
                val intent = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION.let {
                    android.content.Intent(it, "package:$packageName".toUri())
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
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
            appLaunchIntent(),
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
                mediaSession?.let {
                    MediaStyleNotificationHelper.MediaStyle(it)
                        .setShowActionsInCompactView(0, 1)
                }
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
            // Play/Pause (index 0) ->always in compact view, highest priority
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
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        val currentSong = playerState.currentSong.value
        val p = player
        if (currentSong != null && MediaClassifier.isAudiobook(currentSong) && p != null) {
            val pos = p.currentPosition
            val dur = p.duration
            val savedPos = if (dur > 0 && pos >= dur - 10_000) 0L else pos
            playerState.saveSongProgress(currentSong.id, savedPos)
        }

        playerState.saveSession()
        progressJob?.cancel()
        serviceScope.cancel()
        cachedArtworkBitmap?.recycle()
        cachedArtworkBitmap = null
        notificationColor = DefaultMediaNotificationColor
        playerState.detachPlayer()
        mediaSession?.release()
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun appLaunchIntent(): Intent = packageManager
        .getLaunchIntentForPackage(packageName)
        ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
}

@OptIn(UnstableApi::class)
private class CustomMediaCodecAudioRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    audioSink: AudioSink
) : MediaCodecAudioRenderer(
    context,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink
) {
    override fun getCodecMaxInputSize(
        codecInfo: MediaCodecInfo,
        format: Format,
        codecs: Array<out Format>
    ): Int {
        val superSize = super.getCodecMaxInputSize(codecInfo, format, codecs)
        return maxOf(superSize, 256 * 1024)
    }
}
