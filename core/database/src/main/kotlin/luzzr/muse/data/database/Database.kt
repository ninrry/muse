package luzzr.muse.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        LyricsEntity::class,
        LyricsOffsetEntity::class,
        BookCollectionEntity::class,
        BookCollectionItemEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class MuseDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun lyricsOffsetDao(): LyricsOffsetDao
    abstract fun bookCollectionDao(): BookCollectionDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: MuseDatabase? = null

        fun getInstance(context: Context): MuseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuseDatabase::class.java,
                    "muse_player.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build().also { INSTANCE = it }
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lyrics (
                        songId INTEGER NOT NULL PRIMARY KEY,
                        syncedLyrics TEXT,
                        plainText TEXT,
                        fetchedAt INTEGER NOT NULL DEFAULT 0
                    )
                """
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lyrics_offset (
                        songId INTEGER NOT NULL PRIMARY KEY,
                        offsetMs INTEGER NOT NULL DEFAULT 0
                    )
                """
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS book_collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS book_collection_items (
                        collectionId INTEGER NOT NULL,
                        songId INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        PRIMARY KEY(collectionId, songId)
                    )
                """
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE book_collections ADD COLUMN author TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE book_collections ADD COLUMN artworkUri TEXT")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新建独立的歌单表，与有声书集合分离
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        artworkUri TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_items (
                        playlistId INTEGER NOT NULL,
                        songId INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        PRIMARY KEY(playlistId, songId)
                    )
                """
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_items_songId ON playlist_items(songId)")
            }
        }
    }
}
