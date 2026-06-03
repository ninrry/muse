package luzzr.muse.data.repository

import luzzr.muse.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class ArtworkRepositoryDelegate @Inject constructor(
    private val artworkRepository: ArtworkRepository
) {
    val coverGenerationCompleted: SharedFlow<Unit> = artworkRepository.coverGenerationCompleted
    val coverGenState: StateFlow<CoverGenState> = artworkRepository.coverGenState

    suspend fun generateDefaultCoverForSong(song: Song): Boolean =
        artworkRepository.generateDefaultCoverForSong(song)

    suspend fun generateDefaultCoversForAll(): Boolean =
        artworkRepository.generateDefaultCoversForAll()

    suspend fun generateMissingCovers() = artworkRepository.generateMissingCovers()

    suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): Boolean =
        artworkRepository.updateSongArtwork(song, artworkBytes)

    suspend fun downloadBytes(url: String): ByteArray? = artworkRepository.downloadBytes(url)
}
