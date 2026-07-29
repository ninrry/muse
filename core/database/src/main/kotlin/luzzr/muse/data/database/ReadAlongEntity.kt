package luzzr.muse.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "readalong_books")
data class ReadAlongBookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val language: String,
    val publisher: String,
    val isbn: String,
    val description: String,
    val pubDate: String,
    val tags: String,
    val wordCount: Int,
    val totalChapters: Int,
    val epubPath: String,
    val packageRoot: String,
    val coverPath: String?,
    val chaptersJson: String,
    val tocJson: String,
    val sourceFingerprint: String,
    @ColumnInfo(name = "synchronized") val hasAlignment: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "readalong_progress")
data class ReadAlongProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int,
    val chapterId: String?,
    val audioPositionMs: Long,
    val textLocator: String?,
    val characterIndex: Int,
    val scrollProgress: Float,
    val pageProgress: Float,
    val playbackSpeed: Float,
    val fontScale: Float,
    val lineHeightScale: Float,
    val fontFamily: String,
    val fontWeight: String,
    val paragraphSpacing: Float,
    val pagerMode: String,
    val theme: String,
    val autoFollow: Boolean,
    val totalListenedMs: Long,
    val sessionStartMs: Long,
    val consecutiveDays: Int,
    val lastReadAt: Long,
    val lastListenedAt: Long,
    val completed: Boolean
)

@Entity(tableName = "readalong_annotations")
data class ReadAlongAnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterHref: String,
    val elementId: String?,
    val charStart: Int,
    val charEnd: Int,
    val color: String,
    val note: String,
    val quote: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "readalong_bookmarks")
data class ReadAlongBookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterHref: String,
    val label: String,
    val charOffset: Int,
    val audioPositionMs: Long,
    val createdAt: Long
)

@Entity(tableName = "readalong_markers", primaryKeys = ["bookId", "chapterId"])
data class ReadAlongMarkerEntity(
    val bookId: String,
    val chapterId: String,
    val charOffset: Int,
    val unitIndex: Int,
    val updatedAt: Long
)

@Dao
interface ReadAlongDao {
    @Query("SELECT * FROM readalong_books ORDER BY updatedAt DESC")
    fun observeBooks(): Flow<List<ReadAlongBookEntity>>

    @Query("SELECT * FROM readalong_progress")
    fun observeProgress(): Flow<List<ReadAlongProgressEntity>>

    @Query("SELECT * FROM readalong_books WHERE id = :bookId")
    suspend fun getBook(bookId: String): ReadAlongBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: ReadAlongBookEntity)

    @Query("UPDATE readalong_books SET coverPath = :coverPath WHERE id = :bookId")
    suspend fun updateCoverPath(bookId: String, coverPath: String)

    @Query("DELETE FROM readalong_books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    @Query("SELECT * FROM readalong_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): ReadAlongProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadAlongProgressEntity)

    @Query("DELETE FROM readalong_progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: String)

    @Query("SELECT * FROM readalong_annotations WHERE bookId = :bookId ORDER BY createdAt ASC")
    fun observeAnnotations(bookId: String): Flow<List<ReadAlongAnnotationEntity>>

    @Query("SELECT * FROM readalong_annotations WHERE bookId = :bookId AND chapterHref = :chapterHref ORDER BY charStart ASC")
    suspend fun annotationsForChapter(bookId: String, chapterHref: String): List<ReadAlongAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnnotation(annotation: ReadAlongAnnotationEntity)

    @Query("DELETE FROM readalong_annotations WHERE id = :id")
    suspend fun deleteAnnotation(id: String)

    @Query("SELECT COUNT(*) FROM readalong_annotations WHERE bookId = :bookId")
    suspend fun annotationCount(bookId: String): Int

    @Query("SELECT * FROM readalong_bookmarks WHERE bookId = :bookId ORDER BY createdAt ASC")
    fun observeBookmarks(bookId: String): Flow<List<ReadAlongBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: ReadAlongBookmarkEntity)

    @Query("DELETE FROM readalong_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("SELECT COUNT(*) FROM readalong_bookmarks WHERE bookId = :bookId")
    suspend fun bookmarkCount(bookId: String): Int

    @Query("SELECT * FROM readalong_markers WHERE bookId = :bookId AND chapterId = :chapterId LIMIT 1")
    suspend fun getMarker(bookId: String, chapterId: String): ReadAlongMarkerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarker(marker: ReadAlongMarkerEntity)

    @Query("SELECT IFNULL(SUM(totalListenedMs), 0) FROM readalong_progress")
    fun observeTotalListenedMs(): Flow<Long>

    @Query("SELECT IFNULL(SUM(totalListenedMs), 0) FROM readalong_progress WHERE lastListenedAt >= :sinceMs")
    suspend fun totalListenedSince(sinceMs: Long): Long

    @Query("SELECT IFNULL(MAX(consecutiveDays), 0) FROM readalong_progress")
    fun observeMaxStreak(): Flow<Int>

    @Query("SELECT IFNULL(SUM(characterIndex), 0) FROM readalong_progress")
    fun observeTotalCharacters(): Flow<Long>
}
