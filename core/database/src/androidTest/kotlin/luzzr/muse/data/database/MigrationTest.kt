package luzzr.muse.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MuseDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        var db = helper.createDatabase(testDb, 1)

        // db has schema version 1. insert some data using SQL queries.
        // You cannot use DAO classes because they expect the latest schema.
        db.execSQL(
            "INSERT INTO songs (" +
                "id, title, artist, album, albumId, duration, uri, artworkUri, " +
                "trackNumber, year, genre, dateAdded, dateModified, albumArtist, " +
                "bitrate, sampleRate, channels, codec, size, filePath" +
                ") VALUES (" +
                "1, 'Title', 'Artist', 'Album', 1, 100, 'uri://1', NULL, " +
                "1, 2020, 'Pop', 100, 100, 'Artist', " +
                "320000, 44100, 2, 'mp3', 100, '/storage/emulated/0/Music/song.mp3'" +
                ")"
        )

        db.close()

        // Re-open the database with version 2.
        db = helper.runMigrationsAndValidate(testDb, 2, true, MuseDatabase.Companion.MIGRATION_1_2)

        // Validate that data was preserved through the migration.
        db.query("SELECT title, artist, album, filePath FROM songs WHERE id = 1").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("Title", cursor.getString(0))
            assertEquals("Artist", cursor.getString(1))
            assertEquals("Album", cursor.getString(2))
            assertEquals("/storage/emulated/0/Music/song.mp3", cursor.getString(3))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3() {
        var db = helper.createDatabase(testDb, 2)

        // v2 schema still has songs with filePath. Add a lyrics row tied to it.
        db.execSQL(
            "INSERT INTO songs (" +
                "id, title, artist, album, albumId, duration, uri, artworkUri, " +
                "trackNumber, year, genre, dateAdded, dateModified, albumArtist, " +
                "bitrate, sampleRate, channels, codec, size, filePath" +
                ") VALUES (" +
                "1, 'Title', 'Artist', 'Album', 1, 100, 'uri://1', NULL, " +
                "1, 2020, 'Pop', 100, 100, 'Artist', " +
                "320000, 44100, 2, 'mp3', 100, '/storage/emulated/0/Music/song.mp3'" +
                ")"
        )
        db.execSQL(
            "INSERT INTO lyrics (songId, syncedLyrics, plainText, fetchedAt) " +
                "VALUES (1, 'synced', 'plain', 1000)"
        )

        db.close()

        db = helper.runMigrationsAndValidate(testDb, 3, true, MuseDatabase.Companion.MIGRATION_2_3)

        // Lyrics row must survive 2 -> 3.
        db.query("SELECT syncedLyrics, plainText, fetchedAt FROM lyrics WHERE songId = 1").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("synced", cursor.getString(0))
            assertEquals("plain", cursor.getString(1))
            assertEquals(1000L, cursor.getLong(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database.
        helper.createDatabase(testDb, 1).apply { close() }

        // Open latest version. Room will validate the schema once all migrations execute.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MuseDatabase::class.java,
            testDb
        )
            .addMigrations(
                MuseDatabase.MIGRATION_1_2,
                MuseDatabase.MIGRATION_2_3,
                MuseDatabase.MIGRATION_3_4,
                MuseDatabase.MIGRATION_4_5,
                MuseDatabase.MIGRATION_5_6,
                MuseDatabase.MIGRATION_6_7,
                MuseDatabase.MIGRATION_7_8,
                MuseDatabase.MIGRATION_8_9
            )
            .build()
            .apply {
                openHelper.writableDatabase
                close()
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8CreatesReadAlongTables() {
        var db = helper.createDatabase(testDb, 7)
        db.close()

        db = helper.runMigrationsAndValidate(testDb, 8, true, MuseDatabase.MIGRATION_7_8)
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('readalong_books', 'readalong_progress')").use { cursor ->
            assertEquals(2, cursor.count)
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9AddsReadAlongMetadataColumns() {
        var db = helper.createDatabase(testDb, 8)
        db.execSQL(
            """
            INSERT INTO readalong_books (
                id, title, author, epubPath, packageRoot, coverPath, chaptersJson, synchronized, createdAt, updatedAt
            ) VALUES (
                'book-1', 'Title', 'Author', '/tmp/book.epub', '/tmp/root', NULL, '[]', 1, 1, 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO readalong_progress (
                bookId, chapterIndex, chapterId, audioPositionMs, textLocator, characterIndex,
                playbackSpeed, fontScale, lineHeightScale, totalListenedMs, lastReadAt, lastListenedAt, completed
            ) VALUES (
                'book-1', 0, 'ch001', 100, NULL, 0, 1.0, 1.0, 1.6, 0, 1, 1, 0
            )
            """.trimIndent()
        )
        db.close()

        db = helper.runMigrationsAndValidate(testDb, 9, true, MuseDatabase.MIGRATION_8_9)
        db.query("SELECT tocJson, sourceFingerprint FROM readalong_books WHERE id = 'book-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("[]", cursor.getString(0))
            assertEquals("", cursor.getString(1))
        }
        db.query("SELECT scrollProgress, theme, autoFollow FROM readalong_progress WHERE bookId = 'book-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0.0, cursor.getDouble(0), 0.0001)
            assertEquals("PAPER", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5() {
        var db = helper.createDatabase(testDb, 4)
        db.execSQL("INSERT INTO book_collections (id, name, createdAt) VALUES (1, 'Book', 100)")
        db.close()

        db = helper.runMigrationsAndValidate(testDb, 5, true, MuseDatabase.MIGRATION_4_5)
        db.query("SELECT name, author, artworkUri FROM book_collections WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Book", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals(null, cursor.getString(2))
        }
    }
}
