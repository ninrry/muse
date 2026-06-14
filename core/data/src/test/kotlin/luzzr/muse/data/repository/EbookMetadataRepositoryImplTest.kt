package luzzr.muse.data.repository

import android.content.ContentResolver
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import luzzr.muse.core.result.OperationResult
import luzzr.muse.data.ebook.EpubMetadataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
class EbookMetadataRepositoryImplTest {
    @Test
    fun `extracts epub by content when provider metadata is generic`() = runTest {
        val file = createEpub()
        val resolver = mockk<ContentResolver>()
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: ".")
        every { resolver.openInputStream(any()) } answers { file.inputStream() }
        val repository = EbookMetadataRepositoryImpl(
            context = context,
            epubMetadataParser = EpubMetadataParser()
        )

        val result = repository.extract(
            uri = "content://test/book",
            displayName = null,
            mimeType = "application/octet-stream"
        )

        assertTrue(result is OperationResult.Success)
        assertEquals("伯恩斯新情绪疗法", (result as OperationResult.Success).value.title)
        assertEquals("大卫.伯恩斯", result.value.author)
    }

    private fun createEpub(): File {
        val file = File.createTempFile("generic-provider", ".tmp").apply { deleteOnExit() }
        val entries = mapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
                </container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>伯恩斯新情绪疗法</dc:title>
                    <dc:creator>大卫.伯恩斯</dc:creator>
                  </metadata><manifest/>
                </package>
            """.trimIndent()
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
