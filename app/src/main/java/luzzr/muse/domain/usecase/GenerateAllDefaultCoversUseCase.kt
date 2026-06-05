package luzzr.muse.domain.usecase

import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import luzzr.muse.domain.artwork.DefaultCoverGenerationController
import luzzr.muse.domain.model.CoverGenState
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.SongRepository
import luzzr.muse.media.PlaybackController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class GenerateAllDefaultCoversUseCase @Inject constructor(
    private val artworkRepository: ArtworkRepository,
    private val songRepository: SongRepository,
    private val playbackController: PlaybackController
) : DefaultCoverGenerationController {

    override val coverGenState: StateFlow<CoverGenState> = artworkRepository.coverGenState

    suspend operator fun invoke(): OperationResult<Unit> = generateAll()

    override suspend fun generateAll(): OperationResult<Unit> {
        val result = artworkRepository.generateDefaultCoversForAll()
        if (result.isSuccess) {
            refreshCurrentSongArtwork()
        }
        return result
    }

    private fun refreshCurrentSongArtwork() {
        val current = playbackController.state.value.currentSong ?: return
        val refreshed = songRepository.songs.value.find { it.id == current.id } ?: return
        if (refreshed.artworkUri != null) {
            playbackController.refreshCurrentSong(refreshed)
        }
    }
}
