package luzzr.muse.player

import luzzr.muse.media.SleepTimerController
import luzzr.muse.media.SleepTimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coroutine-based countdown timer for sleep timer feature.
 *
 * Usage:
 *   sleepTimer.start(SleepTimerMode.MIN_30)
 *   sleepTimer.onTimerElapsed = { player?.pause() }
 *   sleepTimer.stop()
 */
class SleepTimer : SleepTimerController {

    private val _remainingMs = MutableStateFlow<Long?>(null)
    override val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private val _activeMode = MutableStateFlow<SleepTimerMode?>(null)
    override val activeMode: StateFlow<SleepTimerMode?> = _activeMode.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Callback invoked when the timer reaches zero.
     * Set by MusicService to call player?.pause().
     */
    var onTimerElapsed: (() -> Unit)? = null

    /** Whether the timer is currently active. */
    override val isActive: Boolean get() = _activeMode.value != null

    override fun start(
        mode: SleepTimerMode,
        currentTrackRemainingMs: Long?,
        customDurationMs: Long?
    ) {
        stop()

        if (mode == SleepTimerMode.OFF) return

        val startMs = when (mode) {
            SleepTimerMode.OFF -> return
            SleepTimerMode.END_OF_TRACK -> currentTrackRemainingMs ?: return
            SleepTimerMode.CUSTOM -> {
                val ms = customDurationMs ?: return
                if (ms <= 0L) return
                ms
            }
            else -> mode.durationMs ?: return
        }

        _activeMode.value = mode
        _remainingMs.value = startMs
        timerJob = scope.launch {
            var remaining = startMs
            while (remaining > 0) {
                delay(1000L)
                remaining = (remaining - 1000L).coerceAtLeast(0L)
                _remainingMs.value = remaining
            }
            onTimerElapsed?.invoke()
            stop()
        }
    }

    /**
     * Update remaining time for END_OF_TRACK mode when track changes.
     */
    fun updateTrackRemaining(ms: Long) {
        if (_activeMode.value == SleepTimerMode.END_OF_TRACK) {
            _remainingMs.value = ms
            // Cancel old job synchronously first to avoid overlap race conditions
            timerJob?.cancel()
            timerJob = scope.launch {
                var remaining = ms
                while (remaining > 0) {
                    delay(1000L)
                    remaining = (remaining - 1000L).coerceAtLeast(0L)
                    _remainingMs.value = remaining
                }
                onTimerElapsed?.invoke()
                stop()
            }
        }
    }

    override fun stop() {
        timerJob?.cancel()
        timerJob = null
        _remainingMs.value = null
        _activeMode.value = null
    }

    /** Format remaining time as "MM:SS". */
    override fun formatRemaining(): String {
        val ms = _remainingMs.value ?: return ""
        val totalSec = (ms / 1000).toInt()
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
