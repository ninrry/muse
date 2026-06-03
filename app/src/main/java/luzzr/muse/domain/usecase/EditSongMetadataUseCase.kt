package luzzr.muse.domain.usecase

import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditSongMetadataUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(song: Song, title: String, artist: String, album: String, year: Int?, genre: String): Boolean {
        return songRepository.updateSongTags(song, title, artist, album, year, genre)
    }
}
