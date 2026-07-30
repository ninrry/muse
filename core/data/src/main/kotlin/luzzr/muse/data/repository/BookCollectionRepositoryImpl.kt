package luzzr.muse.data.repository

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.database.BookCollectionDao
import luzzr.muse.data.database.BookCollectionEntity
import luzzr.muse.data.database.BookCollectionItemEntity
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.SongRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class BookCollectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookCollectionDao: BookCollectionDao,
    private val songRepository: SongRepository
) : BookCollectionRepository {

    override fun getAllCollections(): Flow<List<BookCollection>> {
        return bookCollectionDao.getAllCollections().map { entities ->
            entities.map { it.toModel() }
        }
    }

    override suspend fun getCollection(collectionId: Long): BookCollection? = bookCollectionDao.getCollectionById(collectionId)?.toModel()

    override suspend fun createCollection(name: String): Long {
        return bookCollectionDao.insertCollection(BookCollectionEntity(name = name))
    }

    override suspend fun deleteCollection(collectionId: Long) {
        bookCollectionDao.getCollectionById(collectionId)?.artworkUri?.let(::deleteOwnedArtwork)
        bookCollectionDao.deleteCollection(collectionId)
        bookCollectionDao.deleteItemsByCollectionId(collectionId)
    }

    override suspend fun updateCollectionMetadata(
        collectionId: Long,
        name: String,
        author: String,
        artworkBytes: ByteArray?
    ): OperationResult<Unit> {
        val current = bookCollectionDao.getCollectionById(collectionId)
            ?: return OperationResult.Failure(OperationError.NOT_FOUND, "Collection $collectionId was not found")
        var newArtworkFile: File? = null
        return try {
            val artworkUri = if (artworkBytes != null) {
                val directory = File(context.filesDir, COLLECTION_COVER_DIRECTORY).apply { mkdirs() }
                val file = File(directory, "collection_${collectionId}_${System.nanoTime()}.img")
                file.outputStream().use { it.write(artworkBytes) }
                newArtworkFile = file
                file.toUri().toString()
            } else {
                current.artworkUri
            }
            bookCollectionDao.updateCollectionMetadata(collectionId, name, author, artworkUri)
            if (newArtworkFile != null) current.artworkUri?.let(::deleteOwnedArtwork)
            OperationResult.Success(Unit)
        } catch (e: IOException) {
            newArtworkFile?.delete()
            OperationResult.Failure(OperationError.IO, e.message)
        } catch (e: Exception) {
            newArtworkFile?.delete()
            OperationResult.Failure(OperationError.DATABASE, e.message)
        }
    }

    override fun getItemsForCollection(collectionId: Long): Flow<List<BookCollectionItem>> {
        return combine(
            bookCollectionDao.getItemsForCollection(collectionId),
            songRepository.songs,
            songRepository.audiobooks
        ) { entities, songs, audiobooks ->
            val songMap = (songs + audiobooks).associateBy { it.id }
            entities.mapNotNull { entity ->
                val song = songMap[entity.songId] ?: return@mapNotNull null
                BookCollectionItem(song, entity.sortOrder)
            }
        }
    }

    override suspend fun getItemsForCollectionSync(collectionId: Long): List<BookCollectionItem> {
        val entities = bookCollectionDao.getItemsForCollectionSync(collectionId)
        val songMap = (songRepository.songs.value + songRepository.audiobooks.value).associateBy { it.id }
        return entities.mapNotNull { entity ->
            val song = songMap[entity.songId] ?: return@mapNotNull null
            BookCollectionItem(song, entity.sortOrder)
        }
    }

    override suspend fun addItemsToCollection(collectionId: Long, songIds: List<Long>) {
        val existing = bookCollectionDao.getItemsForCollectionSync(collectionId)
        var maxOrder = existing.maxOfOrNull { it.sortOrder } ?: 0
        val newItems = songIds.map { songId ->
            maxOrder++
            BookCollectionItemEntity(collectionId, songId, maxOrder)
        }
        bookCollectionDao.insertCollectionItems(newItems)
    }

    override suspend fun removeItemFromCollection(collectionId: Long, songId: Long) {
        bookCollectionDao.deleteCollectionItem(collectionId, songId)
    }

    override suspend fun updateItemSortOrder(collectionId: Long, songId: Long, sortOrder: Int) {
        bookCollectionDao.updateItemSortOrder(collectionId, songId, sortOrder)
    }

    private fun deleteOwnedArtwork(uri: String) {
        val file = runCatching { File(uri.toUri().path.orEmpty()) }.getOrNull() ?: return
        val coverDirectory = File(context.filesDir, COLLECTION_COVER_DIRECTORY)
        if (file.parentFile == coverDirectory) file.delete()
    }

    private fun BookCollectionEntity.toModel() = BookCollection(
        id = id,
        name = name,
        author = author,
        artworkUri = artworkUri,
        createdAt = createdAt
    )

    private companion object {
        const val COLLECTION_COVER_DIRECTORY = "collection_covers"
    }
}
