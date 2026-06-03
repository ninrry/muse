package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.player.PlayerState
import luzzr.muse.player.SleepTimerMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetSleepTimerUseCase @Inject constructor(
    private val playerState: PlayerState
) {
    operator fun invoke(mode: SleepTimerMode, trackRemainingMs: Long? = null) {
        if (mode == SleepTimerMode.OFF) {
            playerState.sleepTimer.stop()
            MuseLog.d("SetSleepTimerUseCase", "Sleep timer stopped")
            return
        }
        if (playerState.sleepTimer.isActive) {
            MuseLog.d("SetSleepTimerUseCase", "Replacing active timer with mode: ${mode.label}")
        }
        playerState.sleepTimer.start(mode, trackRemainingMs)
    }
}
