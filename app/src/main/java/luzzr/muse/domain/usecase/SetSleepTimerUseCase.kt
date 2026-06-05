package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.SleepTimerMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetSleepTimerUseCase @Inject constructor(
    private val playbackController: PlaybackController
) {
    operator fun invoke(mode: SleepTimerMode, trackRemainingMs: Long? = null) {
        if (mode == SleepTimerMode.OFF) {
            playbackController.sleepTimer.stop()
            MuseLog.d("SetSleepTimerUseCase", "Sleep timer stopped")
            return
        }
        if (playbackController.sleepTimer.isActive) {
            MuseLog.d("SetSleepTimerUseCase", "Replacing active timer with mode: ${mode.name}")
        }
        playbackController.sleepTimer.start(mode, trackRemainingMs)
    }
}
