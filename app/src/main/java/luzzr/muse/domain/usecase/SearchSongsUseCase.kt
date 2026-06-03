package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchSongsUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(query: String): List<Song> = songRepository.search(query)
}
