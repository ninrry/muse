package luzzr.muse.domain.usecase

import luzzr.muse.core.log.MuseLog
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplyMetadataUseCase @Inject constructor(
    private val songRepository: SongRepository
) {
    suspend operator fun invoke(song: Song, result: MetadataResult): Song {
        if (!validateMetadata(result)) {
            MuseLog.w("ApplyMetadataUseCase", "Metadata validation failed for: ${result.title}")
            return song
        }
        return songRepository.updateSongWithMetadata(song, result)
    }

    private fun validateMetadata(result: MetadataResult): Boolean {
        if (result.title.isBlank()) return false
        if (result.artist.isBlank()) return false
        return true
    }
}
