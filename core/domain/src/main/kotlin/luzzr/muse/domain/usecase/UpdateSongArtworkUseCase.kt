package luzzr.muse.domain.usecase

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.ArtworkRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateSongArtworkUseCase @Inject constructor(
    private val artworkRepository: ArtworkRepository
) {
    suspend operator fun invoke(song: Song, artworkBytes: ByteArray): OperationResult<Unit> =
        artworkRepository.updateSongArtwork(song, artworkBytes)
}
