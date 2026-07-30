package luzzr.muse.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Implementation of PlaylistRepository using dedicated playlists table.
 * Single long-lived Room collector keeps [getAllPlaylists] in sync; mutations
 * must NOT re-collect the Flow (it never completes).
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
            playlistDao.getAllPlaylists().collect { entities ->
                _playlists.value = entities.map { entity ->
                    val itemCount = playlistDao.getItemCount(entity.id)
                    entity.toPlaylist(itemCount)
                }
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
        val tempEntity = PlaylistEntity(
            name = name,
            description = description,
            artworkUri = null,
            createdAt = now,
            updatedAt = now
        )
        val id = playlistDao.insertPlaylist(tempEntity)
        val persistedUri = savePlaylistCover(id, artworkUri)
        if (persistedUri != null) {
            playlistDao.updatePlaylist(tempEntity.copy(id = id, artworkUri = persistedUri))
        }
        return id
    }

    override suspend fun updatePlaylist(playlist: Playlist): OperationResult<Unit> {
        return try {
            val existing = playlistDao.getPlaylistById(playlist.id)
            val persistedUri = if (playlist.artworkUri != existing?.artworkUri) {
                savePlaylistCover(playlist.id, playlist.artworkUri).also { newUri ->
                    if (newUri != existing?.artworkUri) {
                        existing?.artworkUri?.let(::deleteOwnedArtwork)
                    }
                }
            } else {
                existing?.artworkUri
            }
            val entity = PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                artworkUri = persistedUri,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            playlistDao.updatePlaylist(entity)
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            MuseLog.e("PlaylistRepository", "Failed to update playlist", e)
            OperationResult.Failure(OperationError.DATABASE, e.message)
        }
    }

    override suspend fun deletePlaylist(playlistId: Long): OperationResult<Unit> {
        return try {
            val existing = playlistDao.getPlaylistById(playlistId)
            existing?.artworkUri?.let(::deleteOwnedArtwork)
            playlistDao.deletePlaylistItems(playlistId)
            playlistDao.deletePlaylist(playlistId)
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
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.deletePlaylistItem(playlistId, songId)
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

    private fun savePlaylistCover(playlistId: Long, sourceUriString: String?): String? {
        if (sourceUriString == null) return null
        val sourceUri = Uri.parse(sourceUriString)
        val coverDirectory = File(context.filesDir, PLAYLIST_COVER_DIRECTORY).apply { mkdirs() }
        if (sourceUri.scheme == "file" && sourceUri.path?.startsWith(coverDirectory.absolutePath) == true) {
            return sourceUriString
        }

        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, sourceUri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, sourceUri)
            } ?: return null

            val cropped = cropToSquare(bitmap)
            val file = File(coverDirectory, "playlist_${playlistId}_${System.currentTimeMillis()}.png")
            file.outputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            MuseLog.e("PlaylistRepository", "Failed to save playlist cover", e)
            sourceUriString
        }
    }

    private fun cropToSquare(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        return when {
            h > w -> {
                // 竖屏图片（高 > 宽）：裁切留取上部分（Top Crop），正方形边长 = w，起点 (0, 0)
                Bitmap.createBitmap(source, 0, 0, w, w)
            }
            w > h -> {
                // 横屏图片（宽 > 高）：以中轴线为准（Center Crop），将两侧对称裁切，留取中间部分
                val left = (w - h) / 2
                Bitmap.createBitmap(source, left, 0, h, h)
            }
            else -> source
        }
    }

    private fun deleteOwnedArtwork(uri: String) {
        val file = runCatching { File(Uri.parse(uri).path.orEmpty()) }.getOrNull() ?: return
        val coverDirectory = File(context.filesDir, PLAYLIST_COVER_DIRECTORY)
        if (file.parentFile == coverDirectory) file.delete()
    }

    private companion object {
        const val PLAYLIST_COVER_DIRECTORY = "playlist_covers"
    }
}
