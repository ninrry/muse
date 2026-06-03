package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.player.PlayerState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreSessionUseCase @Inject constructor(
    private val songRepository: SongRepository,
    private val playerState: PlayerState
) {
    suspend operator fun invoke(): List<Song> {
        if (!playerState.hasSavedSession()) {
            MuseLog.d("RestoreSessionUseCase", "No saved session found")
            return emptyList()
        }
        val ids = playerState.getSavedPlaylistIds()
        if (ids.isEmpty()) {
            playerState.clearSavedSession()
            return emptyList()
        }
        val allSongs = songRepository.loadFromDatabase()
        val restored = ids.mapNotNull { id -> allSongs.find { it.id == id } }
        if (restored.isEmpty()) {
            playerState.clearSavedSession()
            MuseLog.d("RestoreSessionUseCase", "No matching songs found, cleared stale session")
        }
        return restored
    }
}
