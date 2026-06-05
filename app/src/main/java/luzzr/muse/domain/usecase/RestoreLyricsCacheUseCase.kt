package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.LyricsResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreLyricsCacheUseCase @Inject constructor(
    private val fetchLyricsUseCase: FetchLyricsUseCase
) {
    operator fun invoke(songId: Long, result: LyricsResult) {
        fetchLyricsUseCase.restore(songId, result)
    }
}
