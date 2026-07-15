package luzzr.muse.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.database.PlaylistDao
import luzzr.muse.data.database.PlaylistEntity
import luzzr.muse.data.database.PlaylistItemEntity
import luzzr.muse.data.database.SongDao
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.PlaylistRepository
import luzzr.muse.data.mapper.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Implementation of PlaylistRepository using dedicated playlists table.
 * Separated from BookCollection to avoid data confusion.
 */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
    @Named("application_scope") private val scope: CoroutineScope
) : PlaylistRepository {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())

    init {
        scope.launch {
            refreshPlaylists()
        }
    }

    private suspend fun refreshPlaylists() {
        playlistDao.getAllPlaylists().collect { entities ->
            _playlists.value = entities.map { entity ->
                val itemCount = playlistDao.getItemCount(entity.id)
                entity.toPlaylist(itemCount)
            }
        }
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> = _playlists.asStateFlow()

    override suspend fun getPlaylist(playlistId: Long): Playlist? {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return null
        val itemCount = playlistDao.getItemCount(playlistId)
        return entity.toPlaylist(itemCount)
    }

    override suspend fun createPlaylist(name: String, description: String, artworkUri: String?): Long {
        val now = System.currentTimeMillis()
        val entity = PlaylistEntity(
            name = name,
            description = description,
            artworkUri = artworkUri,
            createdAt = now,
            updatedAt = now
        )
        return playlistDao.insertPlaylist(entity)
    }

    override suspend fun updatePlaylist(playlist: Playlist): OperationResult<Unit> {
        return try {
            val existing = playlistDao.getPlaylistById(playlist.id)
            val entity = PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                artworkUri = playlist.artworkUri,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            playlistDao.updatePlaylist(entity)
            refreshPlaylists()
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            MuseLog.e("PlaylistRepository", "Failed to update playlist", e)
            OperationResult.Failure(OperationError.DATABASE, e.message)
        }
    }

    override suspend fun deletePlaylist(playlistId: Long): OperationResult<Unit> {
        return try {
            playlistDao.deletePlaylistItems(playlistId)
            playlistDao.deletePlaylist(playlistId)
            refreshPlaylists()
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            MuseLog.e("PlaylistRepository", "Failed to delete playlist", e)
            OperationResult.Failure(OperationError.DATABASE, e.message)
        }
    }

    override fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return playlistDao.getItemsForPlaylist(playlistId).map { items ->
            items.mapNotNull { item ->
                songDao.getSongById(item.songId)?.toSong()
            }
        }
    }

    override suspend fun getPlaylistSongsSync(playlistId: Long): List<Song> {
        return playlistDao.getItemsForPlaylistSync(playlistId).mapNotNull { item ->
            songDao.getSongById(item.songId)?.toSong()
        }
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val existingItems = playlistDao.getItemsForPlaylistSync(playlistId)
        val maxSortOrder = existingItems.maxOfOrNull { it.sortOrder } ?: 0
        val item = PlaylistItemEntity(
            playlistId = playlistId,
            songId = songId,
            sortOrder = maxSortOrder + 1
        )
        playlistDao.insertPlaylistItem(item)
        refreshPlaylists()
    }

    override suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        val existingItems = playlistDao.getItemsForPlaylistSync(playlistId)
        val maxSortOrder = existingItems.maxOfOrNull { it.sortOrder } ?: 0
        val items = songIds.mapIndexed { index, songId ->
            PlaylistItemEntity(
                playlistId = playlistId,
                songId = songId,
                sortOrder = maxSortOrder + index + 1
            )
        }
        playlistDao.insertPlaylistItems(items)
        refreshPlaylists()
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.deletePlaylistItem(playlistId, songId)
        refreshPlaylists()
    }

    override suspend fun reorderPlaylist(playlistId: Long, songIds: List<Long>) {
        songIds.forEachIndexed { index, songId ->
            playlistDao.updateItemSortOrder(playlistId, songId, index)
        }
    }

    override suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean {
        val item = playlistDao.getPlaylistItem(playlistId, songId)
        return item != null
    }

    override suspend fun getPlaylistsForSong(songId: Long): List<Playlist> {
        val allPlaylists = playlistDao.getAllPlaylists().first()
        return allPlaylists.filter { playlist ->
            val items = playlistDao.getItemsForPlaylistSync(playlist.id)
            items.any { it.songId == songId }
        }.map { it.toPlaylist(0) }
    }

    private fun PlaylistEntity.toPlaylist(songCount: Int): Playlist {
        return Playlist(
            id = id,
            name = name,
            description = description,
            artworkUri = artworkUri,
            songCount = songCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}