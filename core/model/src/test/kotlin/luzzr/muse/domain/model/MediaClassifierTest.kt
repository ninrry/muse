package luzzr.muse.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {
    private val uri = "content://test/song"

    @Test
    fun `classifies OGG codec as audiobook`() {
        assertTrue(MediaClassifier.isAudiobook(Song(codec = "ogg", filePath = "/Music/chapter.bin", uri = uri)))
    }

    @Test
    fun `classifies OGG extension as audiobook`() {
        assertTrue(MediaClassifier.isAudiobook(Song(codec = "Vorbis", filePath = "/Books/chapter.OGG", uri = uri)))
    }

    @Test
    fun `does not classify long music by duration`() {
        assertFalse(
            MediaClassifier.isAudiobook(
                Song(codec = "FLAC", filePath = "/Music/long-track.flac", duration = 3_600_000, uri = uri)
            )
        )
    }
}
