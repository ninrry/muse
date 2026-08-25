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

    @Test
    fun `sanitize keeps title and artist intact when already separate`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "晴天",
            rawArtist = "周杰伦",
            rawAlbum = "叶惠美"
        )
        assertEquals("晴天", result.title)
        assertEquals("周杰伦", result.artist)
        assertEquals("叶惠美", result.album)
    }

    @Test
    fun `sanitize correctly detects Title (Live) - Artist when artist is unknown`() {
        val result = AudioMetadataSanitizer.sanitize(
            rawTitle = "晴天 (Live) - 周杰伦",
            rawArtist = "未知艺术家"
        )
        assertEquals("晴天 (Live)", result.title)
        assertEquals("周杰伦", result.artist)
    }

    @Test
    fun `hasSongVersionMarkers recognizes live and accompaniment tags`() {
        org.junit.Assert.assertTrue(AudioMetadataSanitizer.hasSongVersionMarkers("晴天 (Live)"))
        org.junit.Assert.assertTrue(AudioMetadataSanitizer.hasSongVersionMarkers("晴天 (伴奏)"))
        org.junit.Assert.assertTrue(AudioMetadataSanitizer.hasSongVersionMarkers("青花瓷 (Remix)"))
        org.junit.Assert.assertTrue(AudioMetadataSanitizer.hasSongVersionMarkers("夜曲 (feat. 潘玮柏)"))
        org.junit.Assert.assertFalse(AudioMetadataSanitizer.hasSongVersionMarkers("周杰伦"))
    }

    @Test
    fun `splitTitleArtist preserves hyphenated artist names like T-ara and AC-DC`() {
        val result1 = AudioMetadataSanitizer.splitTitleArtist("T-ara - Day By Day")
        assertEquals(listOf("T-ara", "Day By Day"), result1)

        val result2 = AudioMetadataSanitizer.splitTitleArtist("AC-DC - Back In Black")
        assertEquals(listOf("AC-DC", "Back In Black"), result2)

        val result3 = AudioMetadataSanitizer.splitTitleArtist("林俊杰——江南")
        assertEquals(listOf("林俊杰", "江南"), result3)
    }

    @Test
    fun `fixMojibake recovers GBK encoded Chinese characters from Latin-1 bytes`() {
        // "周杰伦" in GB18030 bytes: D6 D0 BD DC C2 D7
        val gbkBytes = "周杰伦".toByteArray(java.nio.charset.Charset.forName("GB18030"))
        val mojibake = String(gbkBytes, Charsets.ISO_8859_1)

        val recovered = AudioMetadataSanitizer.fixMojibake(mojibake)
        assertEquals("周杰伦", recovered)

        // Standard text remains unchanged
        assertEquals("晴天", AudioMetadataSanitizer.fixMojibake("晴天"))
        assertEquals("Taylor Swift", AudioMetadataSanitizer.fixMojibake("Taylor Swift"))
    }
}
