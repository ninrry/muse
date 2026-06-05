package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetSongsByArtistUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(artist: String): List<Song> = songRepository.getSongsByArtist(artist)
}
