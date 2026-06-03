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
        MuseDatabase::class.java.canonicalName,
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
            .addMigrations(MuseDatabase.Companion.MIGRATION_1_2, MuseDatabase.Companion.MIGRATION_2_3)
            .build()
            .apply {
                openHelper.writableDatabase
                close()
            }
    }
}
