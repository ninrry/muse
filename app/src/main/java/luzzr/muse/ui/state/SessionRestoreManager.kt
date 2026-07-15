package luzzr.muse.ui.state

import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.PlaybackController
import luzzr.muse.media.PlaybackServiceStarter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class SessionRestoreManager @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackController: PlaybackController,
    private val playbackServiceStarter: PlaybackServiceStarter
) : SessionRestoreController {

    override suspend fun restoreIfNeeded() {
        if (playbackController.state.value.playlist.isNotEmpty()) return
        if (!playbackController.hasSavedSession()) return

        MuseLog.w("SessionRestoreManager", "restoreIfNeeded: playlist empty, restoring saved session")
        val ids = playbackController.getSavedPlaylistIds()
        if (ids.isEmpty()) return
        val (savedIndex, savedPos) = playbackController.getSavedPlaybackInfo()
        MuseLog.w("SessionRestoreManager", "restoreSessionFromPrefs: ids=${ids.size} idx=$savedIndex pos=$savedPos")
        try {
            val allSongs = songRepository.songs.value.let { songs ->
                if (songs.isEmpty()) songRepository.loadFromDatabaseFast() else songs
            }
            val savedSongs = ids.mapNotNull { id -> allSongs.find { it.id == id } }
            if (savedSongs.isEmpty()) {
                MuseLog.w("SessionRestoreManager", "restoreSessionFromPrefs: no matching songs, clearing")
                playbackController.clearSavedSession()
                return
            }
            playbackServiceStarter.startForegroundService()
            delay(200)
            playbackController.playSongs(savedSongs, savedIndex.coerceIn(0, savedSongs.size - 1))
            if (savedPos > 0) {
                delay(100)
                playbackController.seekTo(savedPos)
            }
            if (playbackController.getSavedShuffleMode()) {
                playbackController.toggleShuffle()
            }
            delay(200)
            if (playbackController.state.value.isPlaying) {
                playbackController.togglePlayPause()
            }
            MuseLog.w("SessionRestoreManager", "restoreSessionFromPrefs: done, ${savedSongs.size} songs restored")
        } catch (e: Exception) {
            MuseLog.e("SessionRestoreManager", "restoreSessionFromPrefs failed", e)
        }
    }
}
