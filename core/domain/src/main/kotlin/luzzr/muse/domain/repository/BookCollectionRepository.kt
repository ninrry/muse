package luzzr.muse.domain.repository

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.BookCollection
import luzzr.muse.domain.model.BookCollectionItem
import kotlinx.coroutines.flow.Flow

interface BookCollectionRepository {
    fun getAllCollections(): Flow<List<BookCollection>>
    suspend fun getCollection(collectionId: Long): BookCollection?
    suspend fun createCollection(name: String): Long
    suspend fun deleteCollection(collectionId: Long)
    suspend fun updateCollectionMetadata(collectionId: Long, name: String, author: String, artworkBytes: ByteArray?): OperationResult<Unit>

    fun getItemsForCollection(collectionId: Long): Flow<List<BookCollectionItem>>
    suspend fun getItemsForCollectionSync(collectionId: Long): List<BookCollectionItem>
    suspend fun addItemsToCollection(collectionId: Long, songIds: List<Long>)
    suspend fun removeItemFromCollection(collectionId: Long, songId: Long)
    suspend fun updateItemSortOrder(collectionId: Long, songId: Long, sortOrder: Int)
}
