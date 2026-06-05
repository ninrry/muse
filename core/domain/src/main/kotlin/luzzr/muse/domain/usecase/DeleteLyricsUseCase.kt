package luzzr.muse.domain.usecase

import luzzr.muse.domain.repository.LyricsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteLyricsUseCase @Inject constructor(
    private val lyricsRepository: LyricsRepository
) {
    suspend operator fun invoke(songId: Long) = lyricsRepository.deleteLyrics(songId)
}
