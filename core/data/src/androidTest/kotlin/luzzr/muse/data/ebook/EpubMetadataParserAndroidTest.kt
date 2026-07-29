package luzzr.muse.data.ebook

import androidx.test.ext.junit.runners.AndroidJUnit4
import luzzr.muse.core.result.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class EpubMetadataParserAndroidTest {
    @Test
    fun parsesEpubOnAndroidRuntime() = runBlocking {
        val file = File.createTempFile("android-epub-parser", ".epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.writeEntry("mimetype", "application/epub+zip")
                zip.writeEntry(
                    "META-INF/container.xml",
                    """
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
                    </container>
                    """.trimIndent()
                )
                zip.writeEntry(
                    "OEBPS/content.opf",
                    """
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Android EPUB</dc:title>
                        <dc:creator>Test Author</dc:creator>
                      </metadata>
                      <manifest/>
                    </package>
                    """.trimIndent()
                )
            }

            val result = EpubMetadataParser().parse(file)

            assertTrue(result is OperationResult.Success)
            result as OperationResult.Success
            assertEquals("Android EPUB", result.value.title)
            assertEquals("Test Author", result.value.author)
        } finally {
            file.delete()
        }
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.encodeToByteArray())
        closeEntry()
    }
}
