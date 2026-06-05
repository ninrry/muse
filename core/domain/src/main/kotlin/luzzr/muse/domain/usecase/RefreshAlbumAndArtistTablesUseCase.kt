package luzzr.muse.domain.usecase

import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshAlbumAndArtistTablesUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke() = songRepository.refreshAlbumAndArtistTables()
}
