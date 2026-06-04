package luzzr.muse.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "book_collections")
data class BookCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "book_collection_items",
    primaryKeys = ["collectionId", "songId"]
)
data class BookCollectionItemEntity(
    val collectionId: Long,
    val songId: Long,
    val sortOrder: Int
)

@Dao
interface BookCollectionDao {
    @Query("SELECT * FROM book_collections ORDER BY createdAt DESC")
    fun getAllCollections(): Flow<List<BookCollectionEntity>>

    @Query("SELECT * FROM book_collections WHERE id = :id")
    suspend fun getCollectionById(id: Long): BookCollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: BookCollectionEntity): Long

    @Query("DELETE FROM book_collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItem(item: BookCollectionItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItems(items: List<BookCollectionItemEntity>)

    @Query("DELETE FROM book_collection_items WHERE collectionId = :collectionId AND songId = :songId")
    suspend fun deleteCollectionItem(collectionId: Long, songId: Long)

    @Query("DELETE FROM book_collection_items WHERE collectionId = :collectionId")
    suspend fun deleteItemsByCollectionId(collectionId: Long)

    @Query("SELECT * FROM book_collection_items WHERE collectionId = :collectionId ORDER BY sortOrder ASC, songId ASC")
    fun getItemsForCollection(collectionId: Long): Flow<List<BookCollectionItemEntity>>

    @Query("SELECT * FROM book_collection_items WHERE collectionId = :collectionId ORDER BY sortOrder ASC, songId ASC")
    suspend fun getItemsForCollectionSync(collectionId: Long): List<BookCollectionItemEntity>

    @Query("UPDATE book_collection_items SET sortOrder = :sortOrder WHERE collectionId = :collectionId AND songId = :songId")
    suspend fun updateItemSortOrder(collectionId: Long, songId: Long, sortOrder: Int)
}
