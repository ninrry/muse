package luzzr.muse.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import kotlinx.coroutines.test.runTest
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.database.ReadAlongDao
import luzzr.muse.data.library.LibraryMediaInvalidation
import luzzr.muse.domain.model.ReadAlongImportSource
import luzzr.muse.domain.model.ReadAlongSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in contract for a real generated package. Run with:
 * -Dmuse.readalong.package=/absolute/path/to/book.readalong.zip
 */
class ReadAlongExternalPackageContractTest {
    @Test
    fun `imports external package through the production repository path`() = runTest {
        val packagePath = System.getProperty("muse.readalong.package")
            .orEmpty()
            .ifBlank { System.getenv("MUSE_READALONG_PACKAGE").orEmpty() }
        val packageFile = File(packagePath)
        assumeTrue("pass -Dmuse.readalong.package to run this opt-in contract", packageFile.isFile)

        val cacheDir = File.createTempFile("muse-package-cache", "").apply { delete(); mkdirs() }
        val filesDir = File.createTempFile("muse-package-files", "").apply { delete(); mkdirs() }
        try {
            val contentResolver = mockk<ContentResolver>()
            val uri = mockk<Uri>()
            every { uri.toString() } returns "content://muse/external-package"
            every { contentResolver.openInputStream(any()) } answers { packageFile.inputStream() }
            val context = mockk<Context>()
            every { context.cacheDir } returns cacheDir
            every { context.filesDir } returns filesDir
            every { context.contentResolver } returns contentResolver

            val dao = mockk<ReadAlongDao>(relaxed = true)
            val savedBook = slot<luzzr.muse.data.database.ReadAlongBookEntity>()
            coEvery { dao.insertBook(capture(savedBook)) } just Runs
            coEvery { dao.getBook(any()) } answers { if (savedBook.isCaptured) savedBook.captured else null }
            coEvery { dao.getProgress(any()) } returns null
            val repository = ReadAlongRepositoryImpl(context, dao, LibraryMediaInvalidation())

            val import = repository.importSources(
                listOf(
                    ReadAlongImportSource(
                        uri = uri.toString(),
                        displayName = packageFile.name,
                        mimeType = "application/zip"
                    )
                )
            )
            assertTrue("import failed: $import", import is OperationResult.Success)
            val book = (import as OperationResult.Success).value.book

            assertEquals(15, book.chapters.size)
            assertEquals(12, book.chapters.count { it.audioPath != null })
            assertEquals(3, book.initialChapterIndex)
            assertEquals("ch004", book.chapters[book.initialChapterIndex].id)
            assertEquals(12, book.readingChapterCount)
            assertEquals(4, book.nextReadingChapterIndex(3))
            assertEquals(3, book.previousReadingChapterIndex(4))
            assertEquals(1, book.readingChapterOrdinal(3))
            assertTrue(book.chapters.take(3).none { it.isReadingContent })
            assertTrue(book.chapters.drop(3).all { it.isReadingContent })
            assertTrue(book.coverPath?.let(::File)?.isFile == true)
            assertEquals(ReadAlongSyncStatus.READY, book.syncStatus)

            val chapter = repository.loadChapterData(book.id, book.initialChapterIndex)
            assertTrue("chapter load failed: $chapter", chapter is OperationResult.Success)
            val data = (chapter as OperationResult.Success).value
            assertTrue(data.sentences.isNotEmpty())
            assertTrue(data.units.isNotEmpty())

            val index = repository.loadTextIndex(book.id, data.chapter.href)
            assertTrue("text index failed: $index", index is OperationResult.Success)
            val textIndex = (index as OperationResult.Success).value
            assertEquals(data.units.size, textIndex.unitRanges.size)
            val resolvedUnits = textIndex.unitRanges.count { !it.isEmpty() }
            assertTrue("too few resolved units: $resolvedUnits/${data.units.size}", resolvedUnits.toDouble() / data.units.size >= 0.98)
        } finally {
            cacheDir.deleteRecursively()
            filesDir.deleteRecursively()
        }
    }
}
