package luzzr.muse.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, AlbumEntity::class, ArtistEntity::class, LyricsEntity::class, LyricsOffsetEntity::class, BookCollectionEntity::class, BookCollectionItemEntity::class],
    version = 4,
    exportSchema = true
)
abstract class MuseDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun lyricsOffsetDao(): LyricsOffsetDao
    abstract fun bookCollectionDao(): BookCollectionDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
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
    }
}
