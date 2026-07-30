package luzzr.muse.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import luzzr.muse.domain.lyrics.LocalLyricsSource
import luzzr.muse.domain.lyrics.LyricsSearchClient
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.LyricsResult
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.repository.LyricsRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.test.runTest

class FetchLyricsUseCaseTest {

    private val searchClient: LyricsSearchClient = mockk(relaxed = true)
    private val lyricsRepository: LyricsRepository = mockk(relaxed = true)
    private val localLyricsSource: LocalLyricsSource = mockk(relaxed = true)
    private val useCase = FetchLyricsUseCase(
        lyricsSearchClient = searchClient,
        lyricsRepository = lyricsRepository,
        localLyricsSource = localLyricsSource
    )

    @Test
    fun `local lyrics take precedence over memory cache and network`() = runTest {
        val song = Song(id = 7, title = "Song", artist = "Artist", filePath = "/music/song.mp3")
        val cached = lyrics("network", "cached")
        val local = lyrics("local", "local")
        useCase.restore(song.id, cached)
        coEvery { localLyricsSource.find(song) } returns local

        val result = useCase(song)

        assertEquals("local", result?.source)
        assertEquals("local", result?.syncedLines?.single()?.text)
        coVerify(exactly = 0) {
            searchClient.fetchSync(any(), any(), any(), any())
        }
    }

    private fun lyrics(source: String, text: String) = LyricsResult(
        id = null,
        trackName = "Song",
        artistName = "Artist",
        albumName = null,
        duration = 180.0,
        syncedLines = listOf(LrcLine(1_000, text)),
        plainText = null,
        rawSyncedLyrics = "[00:01.00]$text",
        source = source
    )
}
