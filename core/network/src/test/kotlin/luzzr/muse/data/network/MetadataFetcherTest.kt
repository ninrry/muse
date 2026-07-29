package luzzr.muse.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataFetcherTest {

    private val fetcher = MetadataFetcher(okhttp3.OkHttpClient())

    @Test
    fun `sanitizeQuery removes bilibili suffix`() {
        val result = fetcher.sanitizeQuery("周杰伦 - 青花瓷_哔哩哔哩")
        assertEquals("青花瓷", result.title)
        assertEquals("周杰伦", result.artist)
    }

    @Test
    fun `sanitizeQuery removes YouTube suffix`() {
        val result = fetcher.sanitizeQuery("Artist - Song_YouTube")
        assertEquals("Song", result.title)
        assertEquals("Artist", result.artist)
    }

    @Test
    fun `sanitizeQuery removes bracketed prefixes`() {
        val result = fetcher.sanitizeQuery("【4K】周杰伦 - 青花瓷(Live)")
        assertNotNull(result.title)
        assert(result.title.isNotBlank())
    }

    @Test
    fun `sanitizeQuery extracts artist from dash pattern`() {
        val result = fetcher.sanitizeQuery("林俊杰 - 江南")
        assertEquals("江南", result.title)
        assertEquals("林俊杰", result.artist)
    }

    @Test
    fun `sanitizeQuery handles no artist pattern`() {
        val result = fetcher.sanitizeQuery("简单标题", null)
        assertEquals("简单标题", result.title)
    }

    @Test
    fun `sanitizeQuery removes quality suffixes`() {
        val result = fetcher.sanitizeQuery("歌曲 4K HD 超清")
        assert(!result.title.contains("4K"))
        assert(!result.title.contains("HD"))
    }

    @Test
    fun `sanitizeQuery uses rawArtist when no dash pattern`() {
        val result = fetcher.sanitizeQuery("一些歌词", "歌手名")
        assertEquals("一些歌词", result.title)
        assertEquals("歌手名", result.artist)
    }

    @Test
    fun `sanitizeQuery handles empty title`() {
        val result = fetcher.sanitizeQuery("", null)
        assertEquals("", result.title)
        assertNull(result.artist)
    }

    @Test
    fun `sanitizeQuery removes Official and Audio suffixes`() {
        val result = fetcher.sanitizeQuery("My Song (Official Audio)")
        assert(!result.title.contains("Official"))
        assert(!result.title.contains("Audio"))
    }
}
