package luzzr.muse.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.database.ReadAlongBookEntity
import luzzr.muse.data.database.ReadAlongDao
import luzzr.muse.data.library.LibraryMediaInvalidation
import luzzr.muse.domain.model.ReadAlongImportSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest

class ReadAlongRepositoryImplTest {
    @Test
    fun `wifi import rejects a display name that escapes staging`() = runTest {
        val cacheDir = java.io.File.createTempFile("muse-cache", "").apply {
            delete()
            mkdirs()
        }
        val filesDir = java.io.File.createTempFile("muse-files", "").apply {
            delete()
            mkdirs()
        }
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        val repository = ReadAlongRepositoryImpl(
            context = context,
            dao = mockk(relaxed = true),
            libraryMediaInvalidation = LibraryMediaInvalidation()
        )

        val result = repository.importFromWifi(
            payload = byteArrayOf(1, 2, 3),
            displayName = "../escaped.readalong.zip"
        )

        assertTrue(result is OperationResult.Failure)
        assertTrue(filesDir.walkTopDown().none { it.name == "escaped.readalong.zip" })
    }

    @Test
    fun `imports epub package, resolves generated chapter audio and reads alignment`() = runTest {
        val cacheDir = java.io.File.createTempFile("muse-cache", "").apply {
            delete()
            mkdirs()
        }
        val filesDir = java.io.File.createTempFile("muse-files", "").apply {
            delete()
            mkdirs()
        }
        val contentResolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://muse/test-readalong"
        every { contentResolver.openInputStream(any()) } returns createPackage().inputStream()
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        every { context.contentResolver } returns contentResolver

        val dao = mockk<ReadAlongDao>(relaxed = true)
        val savedBook = slot<ReadAlongBookEntity>()
        coEvery { dao.insertBook(capture(savedBook)) } just Runs
        coEvery { dao.getBook(any()) } answers {
            if (savedBook.isCaptured) savedBook.captured else null
        }
        coEvery { dao.getProgress(any()) } returns null

        val repository = ReadAlongRepositoryImpl(context, dao, LibraryMediaInvalidation())
        val imported = repository.importSources(
            listOf(
                ReadAlongImportSource(
                    uri = uri.toString(),
                    displayName = "sample.readalong.zip",
                    mimeType = "application/zip"
                )
            )
        )
        assertTrue("import failed: $imported", imported is OperationResult.Success)
        val result = (imported as OperationResult.Success).value
        val book = result.book

        assertEquals("测试书", book.title)
        assertEquals(1, book.chapters.size)
        assertTrue(book.chapters.single().audioPath?.endsWith("ch001.m4a") == true)
        assertTrue(book.chapters.single().htmlPath.isNotBlank())
        assertTrue(book.sourceFingerprint.isNotBlank())
        assertTrue(result.hasAlignment)
        coVerify { dao.insertBook(any()) }

        val chapter = repository.loadChapterData(book.id, 0)
        assertTrue(chapter is OperationResult.Success)
        val data = (chapter as OperationResult.Success).value
        assertEquals("你好", data.sentences.single().sourceText)
        assertEquals(2, data.units.size)
        assertEquals(500L, data.units[1].startMs)

        val searchHits = repository.searchBook(book.id, "金柳")
        assertEquals(1, searchHits.size)
        assertTrue(searchHits.single().excerpt.contains("金柳"))
    }

    @Test
    fun `front matter is retained but import opens first synchronized content chapter atomically`() = runTest {
        val cacheDir = java.io.File.createTempFile("muse-cache", "").apply {
            delete()
            mkdirs()
        }
        val filesDir = java.io.File.createTempFile("muse-files", "").apply {
            delete()
            mkdirs()
        }
        val contentResolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://muse/front-matter"
        every { contentResolver.openInputStream(any()) } returns createFrontMatterPackage().inputStream()
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        every { context.contentResolver } returns contentResolver

        val dao = mockk<ReadAlongDao>(relaxed = true)
        val savedBook = slot<ReadAlongBookEntity>()
        val savedProgress = slot<luzzr.muse.data.database.ReadAlongProgressEntity>()
        coEvery { dao.insertBook(capture(savedBook)) } answers {
            val entity = savedBook.captured
            assertTrue(java.io.File(entity.epubPath).isFile)
            assertTrue(entity.coverPath?.let { path -> java.io.File(path).isFile } == true)
        }
        coEvery { dao.saveProgress(capture(savedProgress)) } just Runs
        coEvery { dao.getBook(any()) } answers { if (savedBook.isCaptured) savedBook.captured else null }
        coEvery { dao.getProgress(any()) } returns null

        val repository = ReadAlongRepositoryImpl(context, dao, LibraryMediaInvalidation())
        val result = repository.importSources(
            listOf(ReadAlongImportSource(uri.toString(), "front-matter.readalong.zip", "application/zip"))
        )
        assertTrue("import failed: $result", result is OperationResult.Success)
        val book = (result as OperationResult.Success).value.book

        assertEquals(listOf("ch001", "ch002", "ch003", "ch004"), book.chapters.map { it.id })
        assertTrue(book.chapters.take(3).none { it.isReadingContent })
        assertTrue(book.chapters[3].isReadingContent)
        assertTrue(book.chapters[3].hasAlignment)
        assertEquals(3, book.initialChapterIndex)
        assertEquals(listOf("ch004"), book.toc.map { book.chapters[it.chapterIndex].id })
        assertEquals(luzzr.muse.domain.model.ReadAlongSyncStatus.READY, book.syncStatus)
        assertTrue(savedProgress.isCaptured)
        assertEquals(3, savedProgress.captured.chapterIndex)
        assertEquals("ch004", savedProgress.captured.chapterId)
    }

    private fun createPackage(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "manifest.json",
                """
                {"version":1,"title":"测试书","author":"作者","epub":"book.epub","alignment":"alignment.jsonl","chapters":[{"id":"ch001","title":"第一章","index":0,"href":"OEBPS/ch001.xhtml","audio":"audio/ch001.m4a"}]}
                """.trimIndent()
            )
            zip.putNextEntry(ZipEntry("book.epub"))
            zip.write(createEpub())
            zip.closeEntry()
            put("audio/ch001.m4a", "not-audio")
            put(
                "alignment.jsonl",
                """
                {"chapter_id":"ch001","source_locator":{"epub_href":"OEBPS/ch001.xhtml","element_id":"p1","text_quote":{"exact":"你好"}},"source_text":"你好","spoken_text":"你好","audio_locator":{"chapter_start_seconds":0,"chapter_end_seconds":1},"unit_timings":[{"text":"你","start":0,"end":0.5},{"text":"好","start":0.5,"end":1}]}
                """.trimIndent()
            )
        }
        return output.toByteArray()
    }

    private fun createFrontMatterPackage(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "manifest.json",
                """
                {"version":1,"title":"前置页测试","epub":"book.epub","alignment":"alignment.jsonl","chapters":[{"id":"ch004","href":"OEBPS/chapter-1.xhtml","audio":"audio/ch004.m4a"}]}
                """.trimIndent()
            )
            zip.putNextEntry(ZipEntry("book.epub"))
            zip.write(createFrontMatterEpub())
            zip.closeEntry()
            put("audio/ch004.m4a", "audio-bytes")
            put(
                "alignment.jsonl",
                """
                {"chapter_id":"ch004","source_locator":{"epub_href":"OEBPS/chapter-1.xhtml","element_id":"p1","text_quote":{"exact":"正文"}},"source_text":"正文","spoken_text":"正文","audio_locator":{"chapter_start_seconds":0,"chapter_end_seconds":1},"unit_timings":[{"text":"正","start":0,"end":0.5},{"text":"文","start":0.5,"end":1}]}
                """.trimIndent()
            )
        }
        return output.toByteArray()
    }

    private fun createFrontMatterEpub(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
                """.trimIndent()
            )
            put(
                "OEBPS/content.opf",
                """
                <?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>前置页测试</dc:title></metadata><manifest><item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/><item id="front" href="front.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="chapter" href="chapter-1.xhtml" media-type="application/xhtml+xml"/><item id="cover-image" href="Media/cover.jpg" media-type="image/jpeg" properties="cover-image"/></manifest><spine><itemref idref="cover-page"/><itemref idref="front"/><itemref idref="nav"/><itemref idref="chapter"/></spine></package>
                """.trimIndent()
            )
            put(
                "OEBPS/cover.xhtml",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<body><img src=\"Media/cover.jpg\"/></body></html>"
            )
            put(
                "OEBPS/front.xhtml",
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" " +
                    "\"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">" +
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<body><p>版权</p></body></html>"
            )
            put(
                "OEBPS/nav.xhtml",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<body><nav><ol></ol></nav></body></html>"
            )
            put(
                "OEBPS/chapter-1.xhtml",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<body><p id=\"p1\">正文</p></body></html>"
            )
            zip.putNextEntry(ZipEntry("OEBPS/Media/cover.jpg"))
            zip.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun createEpub(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
                """.trimIndent()
            )
            put(
                "OEBPS/content.opf",
                """
                <?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>测试书</dc:title><dc:creator>作者</dc:creator></metadata><manifest><item id="page" href="ch001.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="page"/></spine></package>
                """.trimIndent()
            )
            put(
                "OEBPS/ch001.xhtml",
                "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<head><title>第一章</title></head>" +
                    "<body><p id=\"p1\">你好，金柳</p></body></html>"
            )
        }
        return output.toByteArray()
    }

    /**
     * Regression for ②: a package whose manifest has no `chapters[]` array
     * (the zaibiekangqiao shape) must still bind the chapter audio by scanning
     * the audio/ directory and matching by filename.
     */
    @Test
    fun `imports package without chapters array by scanning audio dir`() = runTest {
        val cacheDir = java.io.File.createTempFile("muse-cache", "").apply {
            delete()
            mkdirs()
        }
        val filesDir = java.io.File.createTempFile("muse-files", "").apply {
            delete()
            mkdirs()
        }
        val contentResolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://muse/test-readalong-bare"
        every { contentResolver.openInputStream(any()) } returns createBarePackage().inputStream()
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns filesDir
        every { context.contentResolver } returns contentResolver

        val dao = mockk<ReadAlongDao>(relaxed = true)
        val savedBook = slot<ReadAlongBookEntity>()
        coEvery { dao.insertBook(capture(savedBook)) } just Runs
        coEvery { dao.getBook(any()) } answers {
            if (savedBook.isCaptured) savedBook.captured else null
        }
        coEvery { dao.getProgress(any()) } returns null

        val repository = ReadAlongRepositoryImpl(context, dao, LibraryMediaInvalidation())
        val imported = repository.importSources(
            listOf(
                ReadAlongImportSource(
                    uri = uri.toString(),
                    displayName = "bare.readalong.zip",
                    mimeType = "application/zip"
                )
            )
        )
        assertTrue("import failed: $imported", imported is OperationResult.Success)
        val book = (imported as OperationResult.Success).value.book
        assertEquals(1, book.chapters.size)
        // ①+② fix: audioPath must be bound even when manifest has no chapters[]
        val chapter = book.chapters.single()
        val audioPath = chapter.audioPath
        assertTrue("audioPath should be bound, got: $audioPath", audioPath != null && audioPath.endsWith("ch001.m4a"))
        assertTrue(chapter.audioByteSize > 0L)
        assertTrue(!chapter.audioSha256.isNullOrBlank())
    }

    private fun createBarePackage(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            // Manifest has no `chapters` array and no `audio_root` (zaibiekangqiao shape)
            put(
                "manifest.json",
                """
                {"version":1,"title":"裸书","author":"裸","epub":"book.epub","alignment":"alignment.jsonl","audio_root":"audio"}
                """.trimIndent()
            )
            zip.putNextEntry(ZipEntry("book.epub"))
            zip.write(createEpub())
            zip.closeEntry()
            put("audio/ch001.m4a", "not-audio-but-presence-is-enough")
            put(
                "alignment.jsonl",
                """
                {"chapter_id":"ch001","source_locator":{"epub_href":"OEBPS/ch001.xhtml","element_id":"p1","text_quote":{"exact":"你好"}},"source_text":"你好","spoken_text":"你好","audio_locator":{"chapter_start_seconds":0,"chapter_end_seconds":1},"unit_timings":[{"text":"你","start":0,"end":0.5},{"text":"好","start":0.5,"end":1}]}
                """.trimIndent()
            )
        }
        return output.toByteArray()
    }
}
