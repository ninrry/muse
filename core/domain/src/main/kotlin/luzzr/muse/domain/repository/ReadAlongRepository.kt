package luzzr.muse.domain.repository

import luzzr.muse.core.result.OperationResult
import luzzr.muse.domain.model.AnnotationExportFormat
import luzzr.muse.domain.model.ReadAlongAnnotation
import luzzr.muse.domain.model.ReadAlongBook
import luzzr.muse.domain.model.ReadAlongBookSummary
import luzzr.muse.domain.model.ReadAlongBookmark
import luzzr.muse.domain.model.ReadAlongChapterData
import luzzr.muse.domain.model.ReadAlongImportResult
import luzzr.muse.domain.model.ReadAlongImportSource
import luzzr.muse.domain.model.ReadAlongMarker
import luzzr.muse.domain.model.ReadAlongProgress
import luzzr.muse.domain.model.ReadAlongReadingStats
import luzzr.muse.domain.model.ReadAlongSearchHit
import luzzr.muse.domain.model.ReadAlongShelfFilter
import luzzr.muse.domain.model.ReadAlongSortOrder
import luzzr.muse.domain.model.ReadAlongTextIndex
import kotlinx.coroutines.flow.Flow

interface ReadAlongRepository {
    fun observeBooks(): Flow<List<ReadAlongBook>>
    fun observeSummaries(sort: ReadAlongSortOrder, filter: ReadAlongShelfFilter): Flow<List<ReadAlongBookSummary>>
    fun observeProgress(): Flow<Map<String, ReadAlongProgress>>
    fun observeStats(): Flow<ReadAlongReadingStats>
    fun observeAnnotations(bookId: String): Flow<List<ReadAlongAnnotation>>
    fun observeBookmarks(bookId: String): Flow<List<ReadAlongBookmark>>

    suspend fun getBook(bookId: String): ReadAlongBook?
    suspend fun getSummary(bookId: String): ReadAlongBookSummary?
    suspend fun getProgress(bookId: String): ReadAlongProgress
    suspend fun saveProgress(progress: ReadAlongProgress)
    suspend fun deleteBook(bookId: String)

    suspend fun importSources(sources: List<ReadAlongImportSource>): OperationResult<ReadAlongImportResult>
    suspend fun importDocumentTree(treeUri: String): OperationResult<List<ReadAlongImportResult>>
    suspend fun importFromWifi(payload: ByteArray, displayName: String): OperationResult<ReadAlongImportResult>
    suspend fun attachSources(bookId: String, sources: List<ReadAlongImportSource>): OperationResult<ReadAlongImportResult>

    suspend fun loadChapterData(bookId: String, chapterIndex: Int): OperationResult<ReadAlongChapterData>
    suspend fun loadTextIndex(bookId: String, chapterHref: String): OperationResult<ReadAlongTextIndex>
    suspend fun prefetchChapter(bookId: String, chapterIndex: Int): OperationResult<ReadAlongChapterData>

    suspend fun upsertAnnotation(annotation: ReadAlongAnnotation)
    suspend fun deleteAnnotation(annotationId: String)
    suspend fun annotationsForChapter(bookId: String, chapterHref: String): List<ReadAlongAnnotation>

    suspend fun upsertBookmark(bookmark: ReadAlongBookmark)
    suspend fun deleteBookmark(bookmarkId: String)

    suspend fun recordMarker(marker: ReadAlongMarker)
    suspend fun lastMarker(bookId: String, chapterId: String): ReadAlongMarker?

    suspend fun searchBook(bookId: String, query: String): List<ReadAlongSearchHit>
    suspend fun searchAllBooks(query: String): List<ReadAlongSearchHit>

    suspend fun exportAnnotations(bookId: String, format: AnnotationExportFormat): String
}
