package luzzr.muse.media

import kotlinx.coroutines.flow.StateFlow

interface SleepTimerController {
    val remainingMs: StateFlow<Long?>
    val activeMode: StateFlow<SleepTimerMode?>
    val isActive: Boolean

    /**
     * @param customDurationMs 当 [mode] 为 [SleepTimerMode.CUSTOM] 时使用的毫秒数
     */
    fun start(mode: SleepTimerMode, currentTrackRemainingMs: Long? = null, customDurationMs: Long? = null)
    fun stop()
    fun formatRemaining(): String
}

enum class SleepTimerMode(val durationMs: Long?) {
    OFF(null),
    MIN_15(15 * 60 * 1000L),
    MIN_30(30 * 60 * 1000L),
    MIN_45(45 * 60 * 1000L),
    MIN_60(60 * 60 * 1000L),
    MIN_90(90 * 60 * 1000L),
    END_OF_TRACK(-1L),

    /** 用户自定义分钟数，需配合 customDurationMs */
    CUSTOM(null)
}
