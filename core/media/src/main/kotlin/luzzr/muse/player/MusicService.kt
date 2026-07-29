package luzzr.muse.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Format
import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.session.MediaSessionService
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
import luzzr.muse.domain.model.MediaUsageType
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.MediaUsageRepository
import luzzr.muse.media.MonotonicUsageTracker
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.R
import luzzr.muse.media.SleepTimerMode
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MusicService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "muse_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "luzzr.muse.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "luzzr.muse.action.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "luzzr.muse.action.SKIP_PREVIOUS"
        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 30_000
        const val BUFFER_FOR_PLAYBACK_MS = 500
        const val BUFFER_FOR_REBUFFER_MS = 1_000
        const val AUDIOBOOK_PROGRESS_SAVE_INTERVAL_TICKS = 12
        const val AUDIOBOOK_END_THRESHOLD_MS = 10_000L
        const val AUDIOBOOK_SEEK_THRESHOLD_MS = 5_000L
    }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var progressJob: Job? = null

    @Inject lateinit var playerState: PlayerState

    @Inject lateinit var songRepository: SongRepository

    @Inject lateinit var artworkRepository: ArtworkRepository

    @Inject lateinit var mediaUsageRepository: MediaUsageRepository

    private val usageTracker = MonotonicUsageTracker()
    private var usageSongId: Long? = null
    private var usageType = MediaUsageType.MUSIC
    private var usageSinceFlushMs = 0L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Hilt member injection is complete after super.onCreate().
        // Initialize session persistence ->allows recovery after process death
        playerState.initSession(getSharedPreferences("player_session", MODE_PRIVATE))

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

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_REBUFFER_MS
            )
            .build()

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build()
        exoPlayer.addListener(ServicePlayerListener())
        player = exoPlayer

        // Build the MediaSession before draining pending playback operations.
        // Otherwise the player can enter playWhenReady while the service still
        // has no session/notification, which HyperOS treats as an ordinary
        // killable background service.
        mediaSession = MediaSession.Builder(this, exoPlayer).apply {
                // The system media notification should return to the existing
                // activity instead of launching a second task on HyperOS.
                packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    setSessionActivity(
                        PendingIntent.getActivity(
                            this@MusicService,
                            0,
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            }.build()

        playerState.attachPlayer(exoPlayer)

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playerState.togglePlayPause()
            ACTION_SKIP_NEXT -> playerState.skipToNext()
            ACTION_SKIP_PREVIOUS -> playerState.skipToPrevious()
        }

        // Do not wait for Media3's asynchronous notification controller here.
        // startForegroundService() requires the service to promote itself
        // immediately; the explicit MediaStyle notification was also the
        // behavior used by the last known-good Muse releases on HyperOS.
        startMediaForeground()
        return START_STICKY
    }

    /** Restore the last playing playlist from SharedPreferences after process death */
    private fun restoreLastSession() {
        val ids = playerState.getSavedPlaylistIds()
        if (ids.isEmpty()) return
        val (savedIndex, savedPos) = playerState.getSavedPlaybackInfo()
        MuseLog.i("MusicService", "restoreLastSession: restoring ${ids.size} songs, index=$savedIndex, pos=$savedPos")
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
                    MuseLog.i("MusicService", "restoreLastSession: restored ${savedSongs.size} songs, paused at $savedPos")
                } else {
                    MuseLog.i("MusicService", "restoreLastSession: no matching songs found in DB, clearing session")
                    playerState.clearSavedSession()
                }
            } catch (e: Exception) {
                MuseLog.e("MusicService", "restoreLastSession failed", e)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        MuseLog.w(
            "MusicService",
            "onTaskRemoved: playWhenReady=${player?.playWhenReady} isPlaying=${player?.isPlaying}"
        )
        playerState.clearPendingOperations()

        // Save current session before potential process death
        flushMusicUsage()
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
                        val song = playerState.currentSong.value
                        if (song != null) {
                            val type = if (MediaClassifier.isAudiobook(song)) {
                                MediaUsageType.AUDIOBOOK
                            } else {
                                MediaUsageType.MUSIC
                            }
                            if (usageSongId != song.id || usageType != type || !usageTracker.isActive) {
                                flushMusicUsage()
                                usageSongId = song.id
                                usageType = type
                                if (usageTracker.start()) {
                                    serviceScope.launch {
                                        mediaUsageRepository.recordPlayStart(type, song.id.toString())
                                    }
                                }
                            }
                            usageSinceFlushMs += usageTracker.takeDelta()
                            if (usageSinceFlushMs >= 1_000L) flushMusicUsage(pauseTracker = false)
                        } else {
                            flushMusicUsage()
                        }
                        ticks++
                        if (ticks >= AUDIOBOOK_PROGRESS_SAVE_INTERVAL_TICKS) {
                            ticks = 0
                            val currentSong = playerState.currentSong.value
                            if (currentSong != null && MediaClassifier.isAudiobook(currentSong)) {
                                val savedPos =                             if (pos >= duration - AUDIOBOOK_END_THRESHOLD_MS) 0L else pos
                                playerState.saveSongProgress(currentSong.id, savedPos)
                            }
                        }
                    }

                    delay(if (p.isPlaying) 80L else 400L)
                } ?: delay(400L)
            }
        }
    }

    private inner class ServicePlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) flushMusicUsage()
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
            flushMusicUsage()
            usageSongId = null
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
                    updateNotification()
                }
                return
            }

            playerState.updateCurrentSong(song)
            playerState.saveSession()
            updateNotification()
            if (MediaClassifier.isAudiobook(song)) {
                playerState.updateSongLastPlayedTime(song.id)
            }

            // 2. Restore progress for the new audiobook
            if (MediaClassifier.isAudiobook(song) && p != null) {
                val savedProgress = playerState.getSavedSongProgress(song.id)
                if (savedProgress > 0 && kotlin.math.abs(p.currentPosition - savedProgress) > AUDIOBOOK_SEEK_THRESHOLD_MS) {
                    p.seekTo(index, savedProgress)
                }
            }
            serviceScope.launch {
                try {
                    artworkRepository.generateDefaultCoverForSong(song)
                    val refreshed = songRepository.songs.value.find { it.id == song.id }
                    if (refreshed != null && refreshed.artworkUri != null) {
                        // Keep the MediaItem metadata and the system media
                        // notification in sync with the in-memory queue.
                        playerState.refreshCurrentSong(refreshed)
                        updateNotification()
                    }
                } catch (e: Exception) {
                    MuseLog.e("MusicService", "cover generation failed", e)
                }
            }
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
                val completed = playerState.currentSong.value
                if (completed != null) {
                    flushMusicUsage()
                    serviceScope.launch {
                        mediaUsageRepository.recordCompletion(
                            if (MediaClassifier.isAudiobook(completed)) {
                                MediaUsageType.AUDIOBOOK
                            } else {
                                MediaUsageType.MUSIC
                            },
                            completed.id.toString()
                        )
                    }
                }
                val p = player
                if (p != null) {
                    p.pause()
                    p.seekTo(0, 0)
                }
                playerState.updateIsPlaying(false)
            }
            updateNotification()
        }
    }

    private fun flushMusicUsage(pauseTracker: Boolean = true) {
        val songId = usageSongId ?: return
        if (pauseTracker) {
            val delta = usageTracker.pause()
            if (delta > 0L) usageSinceFlushMs += delta
        }
        val pending = usageSinceFlushMs
        usageSinceFlushMs = 0L
        if (pending > 0L) {
            serviceScope.launch {
                mediaUsageRepository.recordListened(usageType, songId.toString(), pending)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun startMediaForeground() {
        MuseLog.w(
            "MusicService",
            "startMediaForeground: song=${playerState.currentSong.value?.id} " +
                "playWhenReady=${player?.playWhenReady} state=${player?.playbackState}"
        )
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification(): Notification {
        val song = playerState.currentSong.value
        val isPlaying = player?.playWhenReady == true
        val contentIntent = mediaSession?.sessionActivity
            ?: PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val previousIntent = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, MusicService::class.java).setAction(ACTION_SKIP_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getForegroundService(
            this,
            2,
            Intent(this, MusicService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getForegroundService(
            this,
            3,
            Intent(this, MusicService::class.java).setAction(ACTION_SKIP_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(currentArtworkBitmap())
            .setContentTitle(song?.title ?: getString(R.string.app_name))
            .setContentText(song?.artist.orEmpty())
            .setSubText(song?.album.orEmpty())
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_prev,
                    getString(R.string.player_prev),
                    previousIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (isPlaying) getString(R.string.player_pause) else getString(R.string.player_play),
                    playPauseIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_skip_next,
                    getString(R.string.player_next),
                    nextIntent
                ).build()
            )
            .setStyle(
                mediaSession?.let { session ->
                    MediaStyleNotificationHelper.MediaStyle(session)
                        .setShowActionsInCompactView(0, 1, 2)
                }
            )
            .build()
    }

    private fun currentArtworkBitmap(): Bitmap? {
        val artwork = player?.currentMediaItem?.mediaMetadata?.artworkData ?: return null
        return BitmapFactory.decodeByteArray(artwork, 0, artwork.size)
    }

    private fun updateNotification() {
        if (mediaSession == null) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
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

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        flushMusicUsage()
        val currentSong = playerState.currentSong.value
        val p = player
        if (currentSong != null && MediaClassifier.isAudiobook(currentSong) && p != null) {
            val pos = p.currentPosition
            val dur = p.duration
            val savedPos = if (dur > 0 && pos >= dur - AUDIOBOOK_END_THRESHOLD_MS) 0L else pos
            playerState.saveSongProgress(currentSong.id, savedPos)
        }

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
