package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSongsByAlbumUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(album: String): List<Song> = songRepository.getSongsByAlbum(album)
}
