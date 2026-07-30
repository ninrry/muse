package luzzr.muse.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.domain.model.MediaUsageType
import luzzr.muse.domain.model.ReadAlongUnit
import luzzr.muse.domain.model.readAlongActiveUnitIndex
import luzzr.muse.domain.repository.MediaUsageRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ExoPlayer-backed engine for audio already imported with a read-along package.
 * It never generates speech and never invents an audio path.
 */
@Singleton
class ReadAlongPlaybackEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val usageRepository: MediaUsageRepository
) : ReadAlongAudioSource {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ReadAlongPlaybackState())
    override val state: StateFlow<ReadAlongPlaybackState> = _state.asStateFlow()
    private var ticker: Job? = null
    private var currentBookId: String? = null
    private var currentChapterId: String? = null
    private var currentSentenceUnits: List<ReadAlongUnit> = emptyList()
    private var currentSentenceStartMs: Long = 0L
    private var currentSentenceEndMs: Long = 0L
    private val usageTracker = BatchedUsageTracker()
    private var usageStarted = false
    private var completedChapterId: String? = null
    private var audioSource: AudioSource = AudioSource.None

    private sealed interface AudioSource {
        data object None : AudioSource
        data class File(val path: String) : AudioSource
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
            override fun onPlaybackStateChanged(playbackState: Int) = publish()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
        })
        ticker = scope.launch {
            while (isActive) {
                publish()
                delay(if (player.isPlaying) 80L else 300L)
            }
        }
    }

    override fun load(
        bookId: String,
        chapterId: String,
        units: List<ReadAlongUnit>,
        sentenceStartMs: Long,
        sentenceEndMs: Long,
        audioFile: String?,
        initialPositionMs: Long,
        autoPlay: Boolean
    ) {
        flushUsage()
        usageTracker.reset()
        usageStarted = false
        currentBookId = bookId
        currentChapterId = chapterId
        currentSentenceUnits = units
        currentSentenceStartMs = sentenceStartMs
        currentSentenceEndMs = sentenceEndMs
        // Fix ④: use the chapter's actual audio file path from the repository (not a hard-coded one)
        val path = audioFile?.takeIf { it.isNotBlank() && File(it).isFile }
        if (path == null) {
            flushUsage()
            audioSource = AudioSource.None
            player.stop()
            stopService()
            _state.value = ReadAlongPlaybackState(currentChapterId = chapterId)
            return
        }
        audioSource = AudioSource.File(path)
        val requestedUri = Uri.fromFile(File(path))
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        val shouldReplace = currentUri != requestedUri.toString()
        val needsPrepare = shouldPrepareReadAlongMedia(
            currentUri = currentUri,
            requestedUri = requestedUri.toString(),
            isIdle = player.playbackState == Player.STATE_IDLE
        )
        if (shouldReplace) {
            player.setMediaItem(
                MediaItem.Builder()
                    .setMediaId(chapterId)
                    .setUri(requestedUri)
                    .build()
            )
        }
        if (needsPrepare) {
            player.prepare()
        }
        player.seekTo(initialPositionMs.coerceAtLeast(0L))
        player.playWhenReady = autoPlay
        if (autoPlay) startService(bookId, chapterId)
        publish()
    }

    override fun seekTo(positionMs: Long) {
        flushUsage()
        player.seekTo(positionMs.coerceAtLeast(0L))
        publish()
    }

    override fun setRate(rate: Float) {
        player.playbackParameters = PlaybackParameters(rate.coerceIn(0.5f, 3f))
        publish()
    }

    override fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
            stopService()
        } else if (player.currentMediaItem != null) {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            startService(currentBookId, currentChapterId)
            player.play()
        }
        publish()
    }

    override fun stop() {
        flushUsage()
        usageTracker.reset()
        usageStarted = false
        player.stop()
        currentChapterId = null
        stopService()
        publish()
    }

    override fun unitIndexAt(positionMs: Long, units: List<ReadAlongUnit>): Int = readAlongActiveUnitIndex(units, positionMs)

    private fun startService(bookId: String?, chapterId: String?) {
        val title = chapterId?.let { "$bookId / $it" } ?: bookId ?: "同步阅读"
        val intent = Intent(appContext, ReadAlongPlaybackService::class.java)
            .putExtra(ReadAlongPlaybackService.EXTRA_TITLE, title)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun stopService() {
        appContext.stopService(Intent(appContext, ReadAlongPlaybackService::class.java))
    }

    private fun publish() {
        val isPlaying = player.isPlaying
        if (isPlaying && usageTracker.start()) {
            usageStarted = true
            currentBookId?.let { bookId ->
                scope.launch { usageRepository.recordPlayStart(MediaUsageType.AUDIOBOOK, bookId) }
            }
        } else if (isPlaying) {
            flushUsageBatch()
        } else if (!isPlaying) {
            flushUsage()
        }
        val duration = player.duration.takeIf { it >= 0L } ?: 0L
        _state.value = ReadAlongPlaybackState(
            currentChapterId = currentChapterId,
            isPlaying = isPlaying,
            isEnded = player.playbackState == Player.STATE_ENDED,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            speed = player.playbackParameters.speed
        )
        if (player.playbackState == Player.STATE_ENDED && completedChapterId != currentChapterId) {
            completedChapterId = currentChapterId
            currentBookId?.let { bookId ->
                scope.launch { usageRepository.recordCompletion(MediaUsageType.AUDIOBOOK, bookId) }
            }
            usageStarted = false
            usageTracker.reset()
        } else if (player.playbackState != Player.STATE_ENDED) {
            completedChapterId = null
        }
    }

    private fun flushUsageBatch() {
        val bookId = currentBookId ?: return
        val delta = usageTracker.takeBatch()
        if (delta > 0L && usageStarted) {
            scope.launch { usageRepository.recordListened(MediaUsageType.AUDIOBOOK, bookId, delta) }
        }
    }

    private fun flushUsage() {
        val delta = usageTracker.pause()
        val bookId = currentBookId
        if (delta > 0L && usageStarted && bookId != null) {
            scope.launch { usageRepository.recordListened(MediaUsageType.AUDIOBOOK, bookId, delta) }
        }
    }
}

data class ReadAlongPlaybackState(
    val currentChapterId: String? = null,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f
)
