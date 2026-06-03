package luzzr.muse.domain.repository

import luzzr.muse.domain.model.CoverGenState
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ArtworkRepository {
    val coverGenerationCompleted: SharedFlow<Unit>
    val coverGenState: StateFlow<CoverGenState>

    suspend fun generateDefaultCoverForSong(song: Song): Boolean
    suspend fun generateDefaultCoversForAll(): Boolean
    suspend fun generateMissingCovers()
    suspend fun updateSongArtwork(song: Song, artworkBytes: ByteArray): Boolean
    suspend fun downloadBytes(url: String): ByteArray?
}
