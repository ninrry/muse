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
}
