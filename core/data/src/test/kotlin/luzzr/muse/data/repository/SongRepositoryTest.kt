package luzzr.muse.data.repository

import android.content.Context
import io.mockk.mockk
import luzzr.muse.data.database.AlbumDao
import luzzr.muse.data.database.ArtistDao
import luzzr.muse.data.database.MuseDatabase
import luzzr.muse.data.database.ReadAlongDao
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.library.LibraryMediaInvalidation
import luzzr.muse.data.scanner.MediaStoreScanner
import luzzr.muse.data.tag.MetadataFileWriter
import luzzr.muse.domain.model.Song
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SongRepository] pure-logic helpers.
 *
 * detectCodec / buildAlbumEntities / buildArtistEntities are private,
 * so we invoke them via reflection. Context/DAOs/Database are mocked
 * with MockK (relaxed) — they are never accessed by the tested methods.
 */
class SongRepositoryTest {

    private lateinit var repository: SongRepositoryImpl

    @Before
    fun setUp() {
        val context: Context = mockk(relaxed = true)
        val songDao: SongDao = mockk(relaxed = true)
        val albumDao: AlbumDao = mockk(relaxed = true)
        val artistDao: ArtistDao = mockk(relaxed = true)
        val database: MuseDatabase = mockk(relaxed = true)
        val mediaStoreScanner: MediaStoreScanner = mockk(relaxed = true)
        val metadataFileWriter: MetadataFileWriter = mockk(relaxed = true)
        val tagEditor: luzzr.muse.data.tag.TagEditor = mockk(relaxed = true)
        val readAlongDao: ReadAlongDao = mockk(relaxed = true)
        repository = SongRepositoryImpl(
            context,
            songDao,
            albumDao,
            artistDao,
            database,
            mediaStoreScanner,
            metadataFileWriter,
            tagEditor,
            readAlongDao,
            LibraryMediaInvalidation()
        )
    }

    // -- buildAlbumEntities --------------------------------------

    @Test
    fun `buildAlbumEntities groups songs by album and albumId`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, album = "Album A", albumId = 100, artist = "Artist 1", uri = mockUri),
            Song(id = 2, album = "Album A", albumId = 100, artist = "Artist 1", uri = mockUri),
            Song(id = 3, album = "Album B", albumId = 200, artist = "Artist 2", uri = mockUri)
        )

        val albums = invokeBuildAlbumEntities(songs)
        assertEquals(2, albums.size)

        val albumA = albums.find { it.title == "Album A" }
        assertNotNull(albumA)
        assertEquals(2, albumA!!.songCount)
        assertEquals(100L, albumA.albumId)

        val albumB = albums.find { it.title == "Album B" }
        assertNotNull(albumB)
        assertEquals(1, albumB!!.songCount)
    }

    @Test
    fun `buildAlbumEntities uses albumArtist when available`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, album = "Collab Album", albumId = 1, artist = "Artist A", albumArtist = "Various Artists", uri = mockUri)
        )

        val albums = invokeBuildAlbumEntities(songs)
        assertEquals("Various Artists", albums[0].artist)
    }

    @Test
    fun `buildAlbumEntities falls back to artist when albumArtist is null`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, album = "Solo Album", albumId = 1, artist = "Solo Artist", albumArtist = null, uri = mockUri)
        )

        val albums = invokeBuildAlbumEntities(songs)
        assertEquals("Solo Artist", albums[0].artist)
    }

    @Test
    fun `buildAlbumEntities derives negative ID for albumId 0`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, album = "No Album Art", albumId = 0, artist = "Artist", uri = mockUri)
        )

        val albums = invokeBuildAlbumEntities(songs)
        assertTrue(albums[0].albumId < 0)
    }

    @Test
    fun `buildAlbumEntities derives negative ID for albumId -1`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, album = "Missing Album", albumId = -1, artist = "Artist", uri = mockUri)
        )

        val albums = invokeBuildAlbumEntities(songs)
        assertTrue(albums[0].albumId < 0)
    }

    @Test
    fun `buildAlbumEntities sets artworkUri from first song`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, album = "Art Album", albumId = 100, artist = "A", artworkUri = mockUri, uri = mockUri),
            Song(id = 2, album = "Art Album", albumId = 100, artist = "A", artworkUri = null, uri = mockUri)
        )

        val albums = invokeBuildAlbumEntities(songs)
        assertEquals(mockUri, albums[0].artworkUri)
    }

    @Test
    fun `buildAlbumEntities preserves year from first song`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, album = "Year Album", albumId = 1, artist = "A", year = 2024, uri = mockUri)
        )

        val albums = invokeBuildAlbumEntities(songs)
        assertEquals(2024, albums[0].year)
    }

    @Test
    fun `buildAlbumEntities handles empty song list`() {
        val albums = invokeBuildAlbumEntities(emptyList())
        assertTrue(albums.isEmpty())
    }

    // -- buildArtistEntities -------------------------------------

    @Test
    fun `buildArtistEntities groups by artist name`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, artist = "Artist A", album = "Album 1", uri = mockUri),
            Song(id = 2, artist = "Artist A", album = "Album 1", uri = mockUri),
            Song(id = 3, artist = "Artist A", album = "Album 2", uri = mockUri),
            Song(id = 4, artist = "Artist B", album = "Album 3", uri = mockUri)
        )

        val artists = invokeBuildArtistEntities(songs)
        assertEquals(2, artists.size)

        val artistA = artists.find { it.artistName == "Artist A" }
        assertNotNull(artistA)
        assertEquals(3, artistA!!.songCount)
        assertEquals(2, artistA.albumCount)

        val artistB = artists.find { it.artistName == "Artist B" }
        assertNotNull(artistB)
        assertEquals(1, artistB!!.songCount)
        assertEquals(1, artistB.albumCount)
    }

    @Test
    fun `buildArtistEntities counts distinct albums`() {
        val mockUri = "content://test/song"
        val songs = listOf(
            Song(id = 1, artist = "Prolific", album = "Album 1", uri = mockUri),
            Song(id = 2, artist = "Prolific", album = "Album 2", uri = mockUri),
            Song(id = 3, artist = "Prolific", album = "Album 3", uri = mockUri),
            Song(id = 4, artist = "Prolific", album = "Album 1", uri = mockUri) // duplicate album
        )

        val artists = invokeBuildArtistEntities(songs)
        assertEquals(4, artists[0].songCount)
        assertEquals(3, artists[0].albumCount)
    }

    @Test
    fun `buildArtistEntities handles empty song list`() {
        val artists = invokeBuildArtistEntities(emptyList())
        assertTrue(artists.isEmpty())
    }

    // -- Reflection helpers --------------------------------------

    private fun invokeBuildAlbumEntities(songs: List<Song>): List<luzzr.muse.data.database.AlbumEntity> {
        val method = SongRepositoryImpl::class.java.getDeclaredMethod(
            "buildAlbumEntities",
            List::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(repository, songs) as List<luzzr.muse.data.database.AlbumEntity>
    }

    private fun invokeBuildArtistEntities(songs: List<Song>): List<luzzr.muse.data.database.ArtistEntity> {
        val method = SongRepositoryImpl::class.java.getDeclaredMethod(
            "buildArtistEntities",
            List::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(repository, songs) as List<luzzr.muse.data.database.ArtistEntity>
    }
}
