package luzzr.muse.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearLyricsCacheUseCase @Inject constructor(
    private val fetchLyricsUseCase: FetchLyricsUseCase
) {
    operator fun invoke() {
        fetchLyricsUseCase.clearCache()
    }
}
