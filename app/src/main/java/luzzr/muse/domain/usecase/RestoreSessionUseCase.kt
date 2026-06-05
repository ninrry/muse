package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.PlaybackController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreSessionUseCase @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackController: PlaybackController
) {
    suspend operator fun invoke(): List<Song> {
        if (!playbackController.hasSavedSession()) {
            MuseLog.d("RestoreSessionUseCase", "No saved session found")
            return emptyList()
        }
        val ids = playbackController.getSavedPlaylistIds()
        if (ids.isEmpty()) {
            playbackController.clearSavedSession()
            return emptyList()
        }
        val allSongs = songRepository.loadFromDatabase()
        val restored = ids.mapNotNull { id -> allSongs.find { it.id == id } }
        if (restored.isEmpty()) {
            playbackController.clearSavedSession()
            MuseLog.d("RestoreSessionUseCase", "No matching songs found, cleared stale session")
        }
        return restored
    }
}
