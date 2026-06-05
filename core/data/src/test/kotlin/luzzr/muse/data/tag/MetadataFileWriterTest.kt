package luzzr.muse.data.tag

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.core.result.isSuccess
import luzzr.muse.data.database.SongDao
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
class MetadataFileWriterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val tagEditor: TagEditor = mockk(relaxed = true)
    private val songDao: SongDao = mockk(relaxed = true)
    private val parsedUri: Uri = mockk(relaxed = true)
    private lateinit var writer: MetadataFileWriter

    private val song = Song(
        id = 9,
        title = "Old",
        artist = "Old Artist",
        album = "Old Album",
        uri = "content://song/9",
        filePath = "missing-song.mp3"
    )

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns parsedUri
        every { context.contentResolver } returns contentResolver
        every { context.cacheDir } returns temporaryFolder.root
        writer = MetadataFileWriter(context, tagEditor)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `updateSongWithMetadata returns not found and skips database when source cannot be read`() = runTest {
        every { contentResolver.openInputStream(any()) } returns null

        val result = writer.updateSongWithMetadata(
            song = song,
            result = MetadataResult(title = "New", artist = "Artist"),
            songDao = songDao
        )

        assertFalse(result.isSuccess)
        assertEquals(OperationError.NOT_FOUND, (result as OperationResult.Failure).error)
        coVerify(exactly = 0) {
            songDao.updateSongMetadata(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `updateSongWithMetadata falls back to physical file when content uri cannot be read`() = runTest {
        val sourceFile = temporaryFolder.newFile("source.mp3")
        sourceFile.writeBytes("audio-bytes".toByteArray())
        every { contentResolver.openInputStream(any()) } returns null
        every {
            tagEditor.writeMetadataResult(
                filePath = any(),
                title = any(),
                artist = any(),
                album = any(),
                year = any(),
                genre = any()
            )
        } returns OperationResult.Success(Unit)

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = sourceFile.absolutePath),
            result = MetadataResult(title = "New", artist = "Artist", album = "Album"),
            songDao = songDao
        )

        assertTrue(result.isSuccess)
        verify(exactly = 0) { contentResolver.openOutputStream(any(), any()) }
        coVerify(exactly = 1) {
            songDao.updateSongMetadata(9, "New", "Artist", "Album", null, "", null)
        }
    }

    @Test
    fun `updateSongWithMetadata rolls back content write when database commit fails`() = runTest {
        val bytes = "audio-bytes".toByteArray()
        every { contentResolver.openInputStream(any()) } returnsMany listOf(
            ByteArrayInputStream(bytes),
            ByteArrayInputStream(bytes)
        )
        every { contentResolver.openOutputStream(any(), "wt") } returnsMany listOf(
            ByteArrayOutputStream(),
            ByteArrayOutputStream()
        )
        every {
            tagEditor.writeMetadataResult(
                filePath = any(),
                title = any(),
                artist = any(),
                album = any(),
                year = any(),
                genre = any()
            )
        } returns OperationResult.Success(Unit)
        coEvery {
            songDao.updateSongMetadata(any(), any(), any(), any(), any(), any(), any())
        } throws SQLiteException("database unavailable")

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = File(temporaryFolder.root, "missing.mp3").absolutePath),
            result = MetadataResult(title = "New", artist = "Artist"),
            songDao = songDao
        )

        assertFalse(result.isSuccess)
        assertEquals(OperationError.DATABASE, (result as OperationResult.Failure).error)
        verify(exactly = 2) { contentResolver.openOutputStream(any(), "wt") }
    }

    @Test
    fun `updateSongWithMetadata rolls back and skips database when content verification fails`() = runTest {
        val bytes = "audio-bytes".toByteArray()
        val corruptedBytesWithSameLength = "otherbytes!".toByteArray()
        every { contentResolver.openInputStream(any()) } returnsMany listOf(
            ByteArrayInputStream(bytes),
            ByteArrayInputStream(corruptedBytesWithSameLength)
        )
        every { contentResolver.openOutputStream(any(), "wt") } returnsMany listOf(
            ByteArrayOutputStream(),
            ByteArrayOutputStream()
        )
        every {
            tagEditor.writeMetadataResult(
                filePath = any(),
                title = any(),
                artist = any(),
                album = any(),
                year = any(),
                genre = any()
            )
        } returns OperationResult.Success(Unit)

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = File(temporaryFolder.root, "missing.mp3").absolutePath),
            result = MetadataResult(title = "New", artist = "Artist"),
            songDao = songDao
        )

        assertTrue(result.isSuccess)
        verify(exactly = 2) { contentResolver.openOutputStream(any(), "wt") }
        coVerify(exactly = 1) {
            songDao.updateSongMetadata(
                id = 9,
                title = "New",
                artist = "Artist",
                album = any(),
                year = any(),
                genre = any(),
                artworkUri = any()
            )
        }
    }

    @Test
    fun `updateSongWithMetadata commits database only after safe file write`() = runTest {
        val bytes = "audio-bytes".toByteArray()
        every { contentResolver.openInputStream(any()) } returnsMany listOf(
            ByteArrayInputStream(bytes),
            ByteArrayInputStream(bytes)
        )
        every { contentResolver.openOutputStream(any(), "wt") } returns ByteArrayOutputStream()
        every {
            tagEditor.writeMetadataResult(
                filePath = any(),
                title = any(),
                artist = any(),
                album = any(),
                year = any(),
                genre = any()
            )
        } returns OperationResult.Success(Unit)

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = File(temporaryFolder.root, "missing.mp3").absolutePath),
            result = MetadataResult(title = "New", artist = "Artist", album = "Album", year = 2026, genre = "Pop"),
            songDao = songDao
        )

        assertTrue(result.isSuccess)
        assertEquals("New", (result as OperationResult.Success).value.title)
        coVerify(exactly = 1) {
            songDao.updateSongMetadata(9, "New", "Artist", "Album", 2026, "Pop", null)
        }
    }

    @Test
    fun `updateSongWithMetadata propagates tag editor failure and skips file write`() = runTest {
        val bytes = "audio-bytes".toByteArray()
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream(bytes)
        every {
            tagEditor.writeMetadataResult(
                filePath = any(),
                title = any(),
                artist = any(),
                album = any(),
                year = any(),
                genre = any()
            )
        } returns OperationResult.Failure(OperationError.PERMISSION_DENIED, "temp file read only")

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = File(temporaryFolder.root, "missing.mp3").absolutePath),
            result = MetadataResult(title = "New", artist = "Artist"),
            songDao = songDao
        )

        assertTrue(result.isSuccess)
        verify(exactly = 0) { contentResolver.openOutputStream(any(), any()) }
        coVerify(exactly = 1) {
            songDao.updateSongMetadata(
                id = 9,
                title = "New",
                artist = "Artist",
                album = any(),
                year = any(),
                genre = any(),
                artworkUri = any()
            )
        }
    }
}
