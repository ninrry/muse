package luzzr.muse.data.network

import luzzr.muse.domain.model.MetadataResult
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

    @Test
    fun `sanitizeQuery keeps supplied artist and removes matching filename prefix`() {
        val result = fetcher.sanitizeQuery("周杰伦 - 青花瓷.flac", "周杰伦")

        assertEquals("青花瓷", result.title)
        assertEquals("周杰伦", result.artist)
    }

    @Test
    fun `ranking rejects exact title from a different known artist`() {
        val results = fetcher.mergeAndRankResults(
            results = listOf(
                MetadataResult(
                    title = "同名歌曲",
                    artist = "正确歌手",
                    album = "正确专辑",
                    coverUrl = "https://example.com/right.jpg",
                    source = "Netease",
                    score = 80
                ),
                MetadataResult(
                    title = "同名歌曲",
                    artist = "其他歌手",
                    album = "错误专辑",
                    coverUrl = "https://example.com/wrong.jpg",
                    source = "iTunes",
                    score = 100
                )
            ),
            queryTitle = "同名歌曲",
            queryArtist = "正确歌手",
            queryAlbum = "正确专辑",
            maxResults = 10
        )

        assertEquals(1, results.size)
        assertEquals("正确歌手", results.single().artist)
        assertEquals("https://example.com/right.jpg", results.single().coverUrl)
    }

    @Test
    fun `ranking keeps album editions separate and prefers the local album`() {
        val results = fetcher.mergeAndRankResults(
            results = listOf(
                MetadataResult(
                    title = "歌曲",
                    artist = "歌手",
                    album = "原版专辑",
                    coverUrl = "https://example.com/original.jpg",
                    source = "Netease",
                    score = 70
                ),
                MetadataResult(
                    title = "歌曲",
                    artist = "歌手",
                    album = "现场合集",
                    coverUrl = "https://example.com/live.jpg",
                    source = "iTunes",
                    score = 90
                )
            ),
            queryTitle = "歌曲",
            queryArtist = "歌手",
            queryAlbum = "原版专辑",
            maxResults = 10
        )

        assertEquals(2, results.size)
        assertEquals("原版专辑", results.first().album)
        assertEquals("https://example.com/original.jpg", results.first().coverUrl)
        assertEquals("https://example.com/live.jpg", results.last().coverUrl)
    }

    @Test
    fun `ranking keeps a safe cover when local album metadata is stale`() {
        val results = fetcher.mergeAndRankResults(
            results = listOf(
                MetadataResult(
                    title = "歌曲",
                    artist = "歌手",
                    album = "正确专辑",
                    coverUrl = "https://example.com/correct.jpg",
                    source = "Netease",
                    score = 80
                )
            ),
            queryTitle = "歌曲",
            queryArtist = "歌手",
            queryAlbum = "错误的本地专辑",
            maxResults = 10
        )

        assertEquals("https://example.com/correct.jpg", results.single().coverUrl)
    }

    @Test
    fun `netease cover size parameter preserves an existing query string`() {
        assertEquals(
            "https://example.com/cover.jpg?token=abc&param=800y800",
            fetcher.neteaseCoverUrl("http://example.com/cover.jpg?token=abc")
        )
    }
}
