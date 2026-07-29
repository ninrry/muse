package luzzr.muse.domain.repository

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for music playlists.
 */
interface PlaylistRepository {
    /**
     * Get all playlists as a flow.
     */
    fun getAllPlaylists(): Flow<List<Playlist>>

    /**
     * Get a specific playlist by ID.
     */
    suspend fun getPlaylist(playlistId: Long): Playlist?

    /**
     * Create a new playlist.
     * @return the ID of the newly created playlist
     */
    suspend fun createPlaylist(name: String, description: String = "", artworkUri: String? = null): Long

    /**
     * Update playlist metadata (name, description, artwork).
     */
    suspend fun updatePlaylist(playlist: Playlist): OperationResult<Unit>

    /**
     * Delete a playlist.
     */
    suspend fun deletePlaylist(playlistId: Long): OperationResult<Unit>

    /**
     * Get all songs in a playlist.
     */
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>

    /**
     * Get all songs in a playlist synchronously.
     */
    suspend fun getPlaylistSongsSync(playlistId: Long): List<Song>

    /**
     * Add a song to a playlist.
     */
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long)

    /**
     * Add multiple songs to a playlist.
     */
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>)

    /**
     * Remove a song from a playlist.
     */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    /**
     * Reorder songs in a playlist.
     */
    suspend fun reorderPlaylist(playlistId: Long, songIds: List<Long>)

    /**
     * Check if a song is in a playlist.
     */
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean

    /**
     * Get all playlists containing a specific song.
     */
    suspend fun getPlaylistsForSong(songId: Long): List<Playlist>
}