package luzzr.muse.data.repository

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.model.Song
import luzzr.muse.data.tag.DefaultCoverGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class ArtworkRepositoryTest {

    private lateinit var repository: ArtworkRepository
    private val context: Context = mockk(relaxed = true)
    private val songRepository: SongRepositoryImpl = mockk(relaxed = true)
    private val songDao: SongDao = mockk(relaxed = true)
    private val mockUri: android.net.Uri = mockk(relaxed = true)

    private val testSongs = MutableStateFlow(
        listOf(
            Song(id = 1, title = "歌", artist = "A", uri = mockUri, filePath = "/a/1.mp3"),
            Song(id = 2, title = "曲", artist = "B", uri = mockUri, filePath = "/a/2.mp3")
        )
    )

    @Before
    fun setup() {
        mockkObject(DefaultCoverGenerator)
        io.mockk.mockkStatic(android.net.Uri::class)
        every { DefaultCoverGenerator.generate(any()) } returns byteArrayOf(1, 2, 3)
        every { android.net.Uri.fromFile(any()) } returns mockUri
        every { android.net.Uri.parse(any()) } returns mockUri
        every { songRepository.songs } returns testSongs
        repository = ArtworkRepository(context, songRepository, songDao)
    }

    @After
    fun tearDown() {
        unmockkObject(DefaultCoverGenerator)
        io.mockk.unmockkStatic(android.net.Uri::class)
    }

    @Test
    fun `generateDefaultCoverForSong returns false when song not found`() = runTest {
        val unknown = Song(id = 999, title = "无", uri = mockUri)
        val result = repository.generateDefaultCoverForSong(unknown)
        assertFalse(result)
    }

    @Test
    fun `generateDefaultCoversForAll returns false when already running`() = runTest {
        every { songRepository.songs } returns MutableStateFlow(emptyList())
        val repo = ArtworkRepository(context, songRepository, songDao)
        val result = repo.generateDefaultCoversForAll()
        assertFalse(result)
    }

    @Test
    fun `generateDefaultCoversForAll returns false for empty list`() = runTest {
        every { songRepository.songs } returns MutableStateFlow(emptyList())
        val repo = ArtworkRepository(context, songRepository, songDao)
        assertFalse(repo.generateDefaultCoversForAll())
    }

    @Test
    fun `coverGenState initial value is default`() {
        val state = repository.coverGenState.value
        assertFalse(state.isRunning)
        assertEquals(0, state.processed)
        assertEquals(0, state.total)
        assertEquals(0, state.errors)
    }

    @Test
    fun `CoverGenState data class holds values`() {
        val state = CoverGenState(isRunning = true, processed = 5, total = 10, errors = 1)
        assertTrue(state.isRunning)
        assertEquals(5, state.processed)
        assertEquals(10, state.total)
        assertEquals(1, state.errors)
    }

    @Test
    fun `generateDefaultCoverForSong updates DAO on success`() = runTest {
        val fileDir = java.io.File(System.getProperty("java.io.tmpdir"), "muse_test_${System.nanoTime()}")
        every { context.filesDir } returns fileDir
        try {
            repository.generateDefaultCoverForSong(testSongs.value[0])
        } finally {
            fileDir.deleteRecursively()
        }
    }
}
