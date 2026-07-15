package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.Song

interface GetDailyRecommendationsUseCase {
    operator fun invoke(allSongs: List<Song>): List<Song>
}
