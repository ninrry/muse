package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.Song
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackServiceStarter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerControlUseCase @Inject constructor(
    private val playbackController: PlaybackController,
    private val playbackServiceStarter: PlaybackServiceStarter
) : PlaybackActionController {
    private fun startService() {
        playbackServiceStarter.startForegroundService()
    }

    override fun togglePlayPause() {
        startService()
        playbackController.togglePlayPause()
    }

    override fun skipToNext() {
        startService()
        playbackController.skipToNext()
    }

    override fun skipToPrevious() {
        startService()
        playbackController.skipToPrevious()
    }

    override fun seekTo(position: Long) {
        startService()
        playbackController.seekTo(position)
    }

    override fun playSongAtIndex(list: List<Song>, index: Int) {
        if (index in list.indices) {
            startService()
            playbackController.playSongs(list, index)
        }
    }

    override fun playShuffled(list: List<Song>) {
        if (list.isNotEmpty()) {
            startService()
            playbackController.playShuffled(list)
        }
    }
}
