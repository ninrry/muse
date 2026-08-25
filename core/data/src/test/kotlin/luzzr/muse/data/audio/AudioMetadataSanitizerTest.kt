package luzzr.muse.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMetadataSanitizerTest {

    @Test
    fun `sanitize removes matching artist suffix with dash`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "晴天 - 周杰伦",
            rawArtist = "周杰伦",
            rawAlbum = "叶惠美"
        )
        assertEquals("晴天", result.title)
        assertEquals("周杰伦", result.artist)
        assertEquals("叶惠美", result.album)
    }

    @Test
    fun `sanitize removes matching artist prefix with dash`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "周杰伦 - 晴天",
            rawArtist = "周杰伦",
            rawAlbum = "叶惠美"
        )
        assertEquals("晴天", result.title)
        assertEquals("周杰伦", result.artist)
    }

    @Test
    fun `sanitize removes matching artist with no space hyphen`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "晴天-周杰伦",
            rawArtist = "周杰伦"
        )
        assertEquals("晴天", result.title)
    }

    @Test
    fun `sanitize extracts artist from Artist - Title when artist is unknown`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "林俊杰 - 江南",
            rawArtist = "<unknown>",
            rawAlbum = "乐行者"
        )
        assertEquals("江南", result.title)
        assertEquals("林俊杰", result.artist)
    }

    @Test
    fun `sanitize strips leading track numbers`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "01. 晴天 - 周杰伦",
            rawArtist = "周杰伦"
        )
        assertEquals("晴天", result.title)
    }

    @Test
    fun `sanitize strips bilibili and audio quality noise`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "青花瓷_哔哩哔哩 [FLAC 192kHz]",
            rawArtist = "周杰伦"
        )
        assertEquals("青花瓷", result.title)
    }

    @Test
    fun `sanitize falls back to filename when title is blank`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "",
            rawArtist = "",
            fallbackFileName = "七里香.mp3"
        )
        assertEquals("七里香", result.title)
        assertEquals("Unknown Artist", result.artist)
        assertEquals("Unknown Album", result.album)
    }
}
