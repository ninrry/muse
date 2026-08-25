package luzzr.muse.data.tag

import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagEditorTest {

    private val editor = TagEditor()

    @Test
    fun `readMetadata returns null for non-existent file`() {
        val result = editor.readMetadata("/nonexistent/path/file.mp3")
        assertNull(result)
    }

    @Test
    fun `writeMetadata returns false for non-existent file`() {
        val result = editor.writeMetadata("/nonexistent/path/file.mp3", title = "test")
        assertFalse(result)
    }

    @Test
    fun `writeMetadataResult returns not found for non-existent file`() {
        val result = editor.writeMetadataResult("/nonexistent/path/file.mp3", title = "test")

        assertTrue(result is OperationResult.Failure)
        assertEquals(OperationError.NOT_FOUND, (result as OperationResult.Failure).error)
    }

    @Test
    fun `readArtwork returns null for non-existent file`() {
        val result = editor.readArtwork("/nonexistent/path/file.mp3")
        assertNull(result)
    }

    @Test
    fun `writeArtwork returns false for non-existent file`() {
        val result = editor.writeArtwork("/nonexistent/path/file.mp3", byteArrayOf(1, 2, 3))
        assertFalse(result)
    }

    @Test
    fun `writeArtworkResult returns not found for non-existent file`() {
        val result = editor.writeArtworkResult("/nonexistent/path/file.mp3", byteArrayOf(1, 2, 3))

        assertTrue(result is OperationResult.Failure)
        assertEquals(OperationError.NOT_FOUND, (result as OperationResult.Failure).error)
    }

    @Test
    fun `deleteArtwork returns false for non-existent file`() {
        val result = editor.deleteArtwork("/nonexistent/path/file.mp3")
        assertFalse(result)
    }

    @Test
    fun `deleteArtworkResult returns not found for non-existent file`() {
        val result = editor.deleteArtworkResult("/nonexistent/path/file.mp3")

        assertTrue(result is OperationResult.Failure)
        assertEquals(OperationError.NOT_FOUND, (result as OperationResult.Failure).error)
    }

    @Test
    fun `FileMetadata default values are null`() {
        val meta = TagEditor.FileMetadata()
        assertNull(meta.title)
        assertNull(meta.artist)
        assertNull(meta.album)
        assertNull(meta.year)
        assertNull(meta.genre)
        assertNull(meta.albumArtist)
        assertNull(meta.trackNumber)
    }

    @Test
    fun `FileMetadata holds values`() {
        val meta = TagEditor.FileMetadata(
            title = "标题",
            artist = "歌手",
            album = "专辑",
            year = 2024,
            genre = "流行"
        )
        assertEquals("标题", meta.title)
        assertEquals("歌手", meta.artist)
        assertEquals("专辑", meta.album)
        assertEquals(2024, meta.year)
        assertEquals("流行", meta.genre)
    }

    @Test
    fun `writeAllMetadata returns false for non-existent file`() {
        val meta = TagEditor.FileMetadata(title = "test")
        assertFalse(editor.writeAllMetadata("/nonexistent/path/file.mp3", meta))
    }

    @Test
    fun `writeAllMetadataResult returns not found for non-existent file`() {
        val meta = TagEditor.FileMetadata(title = "test")
        val result = editor.writeAllMetadataResult("/nonexistent/path/file.mp3", meta)

        assertTrue(result is OperationResult.Failure)
        assertEquals(OperationError.NOT_FOUND, (result as OperationResult.Failure).error)
    }

    @Test
    fun `readArtworkMime returns null for non-existent file`() {
        assertNull(editor.readArtworkMime("/nonexistent/path/file.mp3"))
    }

    @Test
    fun `hasRecognizedAudioHeader recognizes various audio formats`() {
        val tempDir = java.nio.file.Files.createTempDirectory("tag_test").toFile()
        try {
            val mp3File = java.io.File(tempDir, "test.mp3").apply {
                writeBytes(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0, 0))
            }
            assertTrue(editor.hasRecognizedAudioHeader(mp3File.absolutePath))

            val flacFile = java.io.File(tempDir, "test.flac").apply {
                writeBytes(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
            }
            assertTrue(editor.hasRecognizedAudioHeader(flacFile.absolutePath))

            val wavFile = java.io.File(tempDir, "test.wav").apply {
                val bytes = ByteArray(16)
                "RIFF".toByteArray().copyInto(bytes, 0)
                "WAVE".toByteArray().copyInto(bytes, 8)
                writeBytes(bytes)
            }
            assertTrue(editor.hasRecognizedAudioHeader(wavFile.absolutePath))

            val aiffFile = java.io.File(tempDir, "test.aiff").apply {
                val bytes = ByteArray(16)
                "FORM".toByteArray().copyInto(bytes, 0)
                "AIFF".toByteArray().copyInto(bytes, 8)
                writeBytes(bytes)
            }
            assertTrue(editor.hasRecognizedAudioHeader(aiffFile.absolutePath))

            val aacFile = java.io.File(tempDir, "test.aac").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte()))
            }
            assertTrue(editor.hasRecognizedAudioHeader(aacFile.absolutePath))

            val apeFile = java.io.File(tempDir, "test.ape").apply {
                writeBytes("MAC ".toByteArray())
            }
            assertTrue(editor.hasRecognizedAudioHeader(apeFile.absolutePath))

            val dsdFile = java.io.File(tempDir, "test.dsf").apply {
                writeBytes("DSD ".toByteArray())
            }
            assertTrue(editor.hasRecognizedAudioHeader(dsdFile.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
