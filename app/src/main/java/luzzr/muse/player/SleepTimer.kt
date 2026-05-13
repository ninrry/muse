package luzzr.muse.player

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
 * Timer modes for sleep timer feature.
 */
enum class SleepTimerMode(val label: String, val durationMs: Long?) {
    OFF("关闭", null),
    MIN_15("15分钟", 15 * 60 * 1000L),
    MIN_30("30分钟", 30 * 60 * 1000L),
    MIN_60("60分钟", 60 * 60 * 1000L),
    END_OF_TRACK("当前曲目结束", -1L)
}

/**
 * Coroutine-based countdown timer for sleep timer feature.
 *
 * Usage:
 *   sleepTimer.start(SleepTimerMode.MIN_30)
 *   sleepTimer.onTimerElapsed = { player?.pause() }
 *   sleepTimer.stop()
 */
class SleepTimer {

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private val _activeMode = MutableStateFlow<SleepTimerMode?>(null)
    val activeMode: StateFlow<SleepTimerMode?> = _activeMode.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Callback invoked when the timer reaches zero.
     * Set by MusicService to call player?.pause().
     */
    var onTimerElapsed: (() -> Unit)? = null

    /** Whether the timer is currently active. */
    val isActive: Boolean get() = _activeMode.value != null

    fun start(mode: SleepTimerMode, currentTrackRemainingMs: Long? = null) {
        stop()

        if (mode == SleepTimerMode.OFF) return

        _activeMode.value = mode

        val startMs = when (mode) {
            SleepTimerMode.OFF -> return
            SleepTimerMode.END_OF_TRACK -> currentTrackRemainingMs ?: return
            else -> mode.durationMs ?: return
        }

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
            // Restart countdown with new remaining time
            val oldJob = timerJob
            timerJob = scope.launch {
                oldJob?.cancel()
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

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        _remainingMs.value = null
        _activeMode.value = null
    }

    /** Format remaining time as "MM:SS". */
    fun formatRemaining(): String {
        val ms = _remainingMs.value ?: return ""
        val totalSec = (ms / 1000).toInt()
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
