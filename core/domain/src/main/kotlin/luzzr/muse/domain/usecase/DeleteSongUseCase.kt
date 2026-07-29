package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
import luzzr.muse.domain.repository.SongRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteSongUseCase @Inject constructor(
    private val songRepository: SongRepository,
    private val lyricsRepository: LyricsRepository
) {
    suspend operator fun invoke(song: Song): OperationResult<Unit> {
        val deleted = songRepository.deleteSong(song)
        if (deleted.isSuccess) {
            lyricsRepository.deleteLyrics(song.id)
            cleanCacheFiles(song)
            MuseLog.d("DeleteSongUseCase", "Deleted song: ${song.title} (id=${song.id})")
        }
        return deleted
    }

    private fun cleanCacheFiles(song: Song) {
        try {
            val coverFile = File("covers/muse_art_${song.id}.png")
            if (coverFile.exists()) coverFile.delete()
        } catch (e: Exception) {
            MuseLog.e("DeleteSongUseCase", "Failed to clean cache for song ${song.id}", e)
        }
    }
}
