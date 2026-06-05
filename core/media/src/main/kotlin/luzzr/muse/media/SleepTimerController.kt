package luzzr.muse.media

import kotlinx.coroutines.flow.StateFlow

interface SleepTimerController {
    val remainingMs: StateFlow<Long?>
    val activeMode: StateFlow<SleepTimerMode?>
    val isActive: Boolean

    fun start(mode: SleepTimerMode, currentTrackRemainingMs: Long? = null)
    fun stop()
    fun formatRemaining(): String
}

enum class SleepTimerMode(val durationMs: Long?) {
    OFF(null),
    MIN_15(15 * 60 * 1000L),
    MIN_30(30 * 60 * 1000L),
    MIN_60(60 * 60 * 1000L),
    END_OF_TRACK(-1L)
}
