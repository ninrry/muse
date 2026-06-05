package luzzr.muse.domain.repository

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.CoverGenState
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ArtworkRepository {
    val coverGenerationCompleted: SharedFlow<Unit>
    val coverGenState: StateFlow<CoverGenState>

    fun generateDefaultCoverPreview(title: String): OperationResult<ByteArray>
    suspend fun generateDefaultCoverForSong(song: Song): OperationResult<Unit>
    suspend fun generateDefaultCoversForAll(): OperationResult<Unit>
    suspend fun generateMissingCovers(): OperationResult<Int>
    suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): OperationResult<Unit>
    suspend fun downloadBytes(url: String): OperationResult<ByteArray>
}
