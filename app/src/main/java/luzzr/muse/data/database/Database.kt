package luzzr.muse.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SongEntity::class, AlbumEntity::class, ArtistEntity::class, LyricsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MuseDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun lyricsDao(): LyricsDao

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
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS lyrics (
                        songId INTEGER NOT NULL PRIMARY KEY,
                        syncedLyrics TEXT,
                        plainText TEXT,
                        fetchedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }
    }
}
