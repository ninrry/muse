package luzzr.muse.data.ebook

import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest

class EpubMetadataParserTest {
    private val parser = EpubMetadataParser()

    @Test
    fun `parses epub3 title authors and encoded cover path`() = runTest {
        val cover = byteArrayOf(1, 2, 3, 4)
        val file = createEpub(
            mapOf(
                "META-INF/container.xml" to container("OPS/package.opf").encodeToByteArray(),
                "OPS/package.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book</dc:title>
                        <dc:creator>Author One</dc:creator>
                        <dc:creator>Author Two</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="cover" href="images/cover%20art.jpg" media-type="image/jpeg" properties="cover-image"/>
                      </manifest>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OPS/images/cover art.jpg" to cover
            )
        )

        val result = parser.parse(file) as OperationResult.Success

        assertEquals("Test Book", result.value.title)
        assertEquals("Author One / Author Two", result.value.author)
        assertArrayEquals(cover, result.value.coverBytes)
    }

    @Test
    fun `parses epub2 metadata cover id`() = runTest {
        val cover = byteArrayOf(9, 8, 7)
        val file = createEpub(
            mapOf(
                "META-INF/container.xml" to container("content.opf").encodeToByteArray(),
                "content.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Old Book</dc:title><dc:creator>Writer</dc:creator>
                        <meta name="cover" content="cover-id"/>
                      </metadata>
                      <manifest><item id="cover-id" href="cover.png" media-type="image/png"/></manifest>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "cover.png" to cover
            )
        )

        val result = parser.parse(file) as OperationResult.Success

        assertEquals("Old Book", result.value.title)
        assertArrayEquals(cover, result.value.coverBytes)
    }

    @Test
    fun `supports guide cover page and missing metadata fields`() = runTest {
        val cover = byteArrayOf(5, 6)
        val file = createEpub(
            mapOf(
                "META-INF/container.xml" to container("OPS/book.opf").encodeToByteArray(),
                "OPS/book.opf" to """
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata/><manifest>
                        <item id="page" href="cover.xhtml" media-type="application/xhtml+xml"/>
                        <item id="image" href="images/front.webp" media-type="image/webp"/>
                      </manifest><guide><reference type="cover" href="cover.xhtml"/></guide>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OPS/cover.xhtml" to """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><img src="images/front.webp"/></body>
                    </html>
                """.trimIndent().encodeToByteArray(),
                "OPS/images/front.webp" to cover
            )
        )

        val result = parser.parse(file) as OperationResult.Success

        assertEquals("", result.value.title)
        assertEquals("", result.value.author)
        assertArrayEquals(cover, result.value.coverBytes)
    }

    @Test
    fun `rejects unsafe archive paths`() = runTest {
        val file = createEpub(
            mapOf(
                "META-INF/container.xml" to container("content.opf").encodeToByteArray(),
                "../content.opf" to "<package/>".encodeToByteArray()
            )
        )

        val result = parser.parse(file)

        assertTrue(result is OperationResult.Failure)
        assertEquals(OperationError.UNSUPPORTED_FILE, (result as OperationResult.Failure).error)
    }

    @Test
    fun `reports missing package document`() = runTest {
        val file = createEpub(mapOf("META-INF/container.xml" to container("missing.opf").encodeToByteArray()))

        val result = parser.parse(file)

        assertTrue(result is OperationResult.Failure)
        assertEquals(OperationError.UNSUPPORTED_FILE, (result as OperationResult.Failure).error)
    }

    @Test
    fun `recognizes epub content without useful name or mime type`() {
        val file = createEpub(
            mapOf(
                "mimetype" to "application/epub+zip".encodeToByteArray(),
                "META-INF/container.xml" to container("content.opf").encodeToByteArray(),
                "content.opf" to "<package/>".encodeToByteArray()
            )
        )

        assertTrue(parser.recognizes(file))
        assertTrue(!parser.supports(displayName = null, mimeType = "application/octet-stream"))
    }

    private fun container(packagePath: String) = """
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
          <rootfiles><rootfile full-path="$packagePath" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent()

    private fun createEpub(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("epub-parser-test", ".epub").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }
}
