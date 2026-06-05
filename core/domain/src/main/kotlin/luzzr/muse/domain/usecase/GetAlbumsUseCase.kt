package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.Album
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAlbumsUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(): List<Album> = songRepository.getAlbums()
}
