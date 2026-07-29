package luzzr.muse.domain.usecase

import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenameSongUseCase @Inject constructor(
    private val songRepository: SongRepository,
    private val lyricsRepository: LyricsRepository,
    private val clearLyricsCacheUseCase: ClearLyricsCacheUseCase
) {
    suspend operator fun invoke(song: Song, newTitle: String): OperationResult<Unit> {
        val success = songRepository.renameSong(song, newTitle)
        if (success.isSuccess && newTitle != song.title) {
            lyricsRepository.deleteLyrics(song.id)
            clearLyricsCacheUseCase()
        }
        return success
    }
}
