package luzzr.muse.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import luzzr.muse.data.database.BookCollectionDao
import luzzr.muse.data.database.BookCollectionEntity
import luzzr.muse.data.database.BookCollectionItemEntity
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.BookCollectionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookCollectionRepositoryImpl @Inject constructor(
    private val bookCollectionDao: BookCollectionDao,
    private val musicRepo: MusicRepositoryFacade
) : BookCollectionRepository {

    override fun getAllCollections(): Flow<List<BookCollection>> {
        return bookCollectionDao.getAllCollections().map { entities ->
            entities.map { BookCollection(it.id, it.name, it.createdAt) }
        }
    }

    override suspend fun createCollection(name: String): Long {
        return bookCollectionDao.insertCollection(BookCollectionEntity(name = name))
    }

    override suspend fun deleteCollection(collectionId: Long) {
        bookCollectionDao.deleteCollection(collectionId)
        bookCollectionDao.deleteItemsByCollectionId(collectionId)
    }

    override fun getItemsForCollection(collectionId: Long): Flow<List<BookCollectionItem>> {
        return combine(
            bookCollectionDao.getItemsForCollection(collectionId),
            musicRepo.songs,
            musicRepo.audiobooks
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
        val songMap = (musicRepo.songs.value + musicRepo.audiobooks.value).associateBy { it.id }
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
}
