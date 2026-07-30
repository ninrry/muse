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
import luzzr.muse.data.scanner.MediaStoreFileRefresher
import luzzr.muse.domain.model.MetadataResult
import luzzr.muse.domain.model.Song
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
    private val mediaStoreFileRefresher: MediaStoreFileRefresher = mockk(relaxed = true)
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
        every { tagEditor.canReadAudioFile(any()) } returns true
        writer = MetadataFileWriter(context, tagEditor, mediaStoreFileRefresher)
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
    fun `updateSongWithMetadata rolls back physical write when database commit fails`() = runTest {
        val sourceFile = temporaryFolder.newFile("source.mp3")
        val originalBytes = "original-bytes".toByteArray()
        sourceFile.writeBytes(originalBytes)

        every {
            tagEditor.writeMetadataResult(
                filePath = any(),
                title = any(),
                artist = any(),
                album = any(),
                year = any(),
                genre = any()
            )
        } answers {
            File(firstArg<String>()).writeBytes("edited-bytes".toByteArray())
            OperationResult.Success(Unit)
        }
        coEvery {
            songDao.updateSongMetadata(any(), any(), any(), any(), any(), any(), any())
        } throws SQLiteException("database unavailable")

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = sourceFile.absolutePath),
            result = MetadataResult(title = "New", artist = "Artist"),
            songDao = songDao
        )

        assertFalse(result.isSuccess)
        assertEquals(OperationError.DATABASE, (result as OperationResult.Failure).error)
        assertArrayEquals(originalBytes, sourceFile.readBytes())
    }

    @Test
    fun `updateSongWithMetadata fails and skips database when content verification fails`() = runTest {
        val sourceFile = temporaryFolder.newFile("source.mp3")
        sourceFile.writeBytes("audio-bytes".toByteArray())

        every { tagEditor.canReadAudioFile(any()) } returns false
        every { tagEditor.hasRecognizedAudioHeader(any()) } returns false
        every {
            tagEditor.writeMetadataResult(any(), any(), any(), any(), any(), any())
        } returns OperationResult.Success(Unit)

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = sourceFile.absolutePath),
            result = MetadataResult(title = "New", artist = "Artist"),
            songDao = songDao
        )

        assertFalse(result.isSuccess)
        assertEquals(OperationError.IO, (result as OperationResult.Failure).error)
        coVerify(exactly = 0) { songDao.updateSongMetadata(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `updateSongWithMetadata commits database only after safe file write`() = runTest {
        val sourceFile = temporaryFolder.newFile("source.mp3")
        val originalBytes = "original-bytes".toByteArray()
        sourceFile.writeBytes(originalBytes)

        every {
            tagEditor.writeMetadataResult(
                filePath = any(),
                title = any(),
                artist = any(),
                album = any(),
                year = any(),
                genre = any()
            )
        } answers {
            File(firstArg<String>()).writeBytes("edited-bytes".toByteArray())
            OperationResult.Success(Unit)
        }

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = sourceFile.absolutePath),
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
    fun `updateSongWithMetadata updates uri after automatic rename`() = runTest {
        val sourceFile = temporaryFolder.newFile("source.mp3")
        sourceFile.writeBytes("original-bytes".toByteArray())
        val targetFile = File(sourceFile.parentFile, "Renamed.mp3")
        val scannedUri = "content://media/external/audio/media/99"
        every { contentResolver.openInputStream(any()) } returns null
        every {
            tagEditor.writeMetadataResult(any(), any(), any(), any(), any(), any())
        } answers {
            File(firstArg<String>()).writeBytes("edited-bytes".toByteArray())
            OperationResult.Success(Unit)
        }
        coEvery {
            mediaStoreFileRefresher.refresh(sourceFile.absolutePath, targetFile.absolutePath)
        } returns mapOf(targetFile.absolutePath to scannedUri)

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = sourceFile.absolutePath),
            result = MetadataResult(title = "Renamed", artist = "Artist"),
            songDao = songDao
        )

        assertTrue(result.isSuccess)
        val renamed = (result as OperationResult.Success).value
        assertEquals(targetFile.absolutePath, renamed.filePath)
        assertEquals(scannedUri, renamed.uri)
        assertTrue(targetFile.exists())
        assertFalse(sourceFile.exists())
        coVerify(exactly = 1) {
            songDao.updateSongMeta(9, "Renamed", scannedUri, targetFile.absolutePath)
        }
    }

    @Test
    fun `updateSongWithMetadata preserves fields missing from network result`() = runTest {
        val sourceFile = temporaryFolder.newFile("preserve.mp3")
        sourceFile.writeBytes("audio-bytes".toByteArray())
        every { contentResolver.openInputStream(any()) } returns null
        every { tagEditor.writeMetadataResult(any(), any(), any(), any(), any(), any()) } returns OperationResult.Success(Unit)
        val original = song.copy(
            filePath = sourceFile.absolutePath,
            artworkUri = "content://art/9",
            year = 2020,
            genre = "Rock"
        )

        val result = writer.updateSongWithMetadata(original, MetadataResult(title = "New", artist = ""), songDao)

        assertTrue(result.isSuccess)
        val updated = (result as OperationResult.Success).value
        assertEquals("Old Artist", updated.artist)
        assertEquals("Old Album", updated.album)
        assertEquals(2020, updated.year)
        assertEquals("Rock", updated.genre)
        assertEquals("content://art/9", updated.artworkUri)
        coVerify(exactly = 1) {
            songDao.updateSongMetadata(9, "New", "Old Artist", "Old Album", 2020, "Rock", "content://art/9")
        }
    }

    @Test
    fun `updateSongWithMetadata propagates tag editor failure and skips database`() = runTest {
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

        assertFalse(result.isSuccess)
        assertEquals(OperationError.PERMISSION_DENIED, (result as OperationResult.Failure).error)
        verify(exactly = 0) { contentResolver.openOutputStream(any(), any()) }
        coVerify(exactly = 0) { songDao.updateSongMetadata(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `updateSongWithMetadata fails before replacing source when edited audio validation fails`() = runTest {
        val sourceFile = temporaryFolder.newFile("validation-source.mp3")
        sourceFile.writeBytes("audio-bytes".toByteArray())
        every { contentResolver.openInputStream(any()) } returns null
        every { tagEditor.writeMetadataResult(any(), any(), any(), any(), any(), any()) } returns OperationResult.Success(Unit)
        every { tagEditor.canReadAudioFile(any()) } returns false

        val result = writer.updateSongWithMetadata(
            song = song.copy(filePath = sourceFile.absolutePath),
            result = MetadataResult(title = "New", artist = "Artist"),
            songDao = songDao
        )

        assertFalse(result.isSuccess)
        assertEquals(OperationError.IO, (result as OperationResult.Failure).error)
        assertArrayEquals("audio-bytes".toByteArray(), sourceFile.readBytes())
        coVerify(exactly = 0) { songDao.updateSongMetadata(any(), any(), any(), any(), any(), any(), any()) }
    }
}
