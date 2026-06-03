package luzzr.muse.domain.usecase

import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RenameSongUseCase @Inject constructor(
    private val songRepository: SongRepository,
    private val lyricsRepository: LyricsRepository,
    private val lyricsFetcher: LyricsFetcher
) {
    suspend operator fun invoke(song: Song, newTitle: String): Boolean {
        val success = songRepository.renameSong(song, newTitle)
        if (success && newTitle != song.title) {
            lyricsRepository.deleteLyrics(song.id)
            lyricsFetcher.clearCache()
        }
        return success
    }
}
