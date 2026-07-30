package luzzr.muse.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        PlaylistItemEntity::class,
        ReadAlongBookEntity::class,
        ReadAlongProgressEntity::class,
        ReadAlongAnnotationEntity::class,
        ReadAlongBookmarkEntity::class,
        ReadAlongMarkerEntity::class,
        MediaUsageEntity::class
    ],
    version = 12,
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
    abstract fun readAlongDao(): ReadAlongDao
    abstract fun mediaUsageDao(): MediaUsageDao

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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12
                    )
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

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS readalong_books (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        epubPath TEXT NOT NULL,
                        packageRoot TEXT NOT NULL,
                        coverPath TEXT,
                        chaptersJson TEXT NOT NULL,
                        synchronized INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS readalong_progress (
                        bookId TEXT NOT NULL PRIMARY KEY,
                        chapterIndex INTEGER NOT NULL,
                        chapterId TEXT,
                        audioPositionMs INTEGER NOT NULL,
                        textLocator TEXT,
                        characterIndex INTEGER NOT NULL,
                        playbackSpeed REAL NOT NULL,
                        fontScale REAL NOT NULL,
                        lineHeightScale REAL NOT NULL,
                        totalListenedMs INTEGER NOT NULL,
                        lastReadAt INTEGER NOT NULL,
                        lastListenedAt INTEGER NOT NULL,
                        completed INTEGER NOT NULL
                    )
                """
                )
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN tocJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN sourceFingerprint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN scrollProgress REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN theme TEXT NOT NULL DEFAULT 'PAPER'")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN autoFollow INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 补全书籍元数据
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN language TEXT NOT NULL DEFAULT 'zh'")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN publisher TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN isbn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN pubDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE readalong_books ADD COLUMN totalChapters INTEGER NOT NULL DEFAULT 0")
                // 阅读偏好扩展
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN fontFamily TEXT NOT NULL DEFAULT 'SERIF'")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN fontWeight TEXT NOT NULL DEFAULT 'REGULAR'")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN paragraphSpacing REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN pagerMode TEXT NOT NULL DEFAULT 'SCROLL'")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN sessionStartMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE readalong_progress ADD COLUMN consecutiveDays INTEGER NOT NULL DEFAULT 0")
                // 注释/书签/光标
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS readalong_annotations (
                        id TEXT NOT NULL PRIMARY KEY,
                        bookId TEXT NOT NULL,
                        chapterId TEXT NOT NULL,
                        chapterHref TEXT NOT NULL,
                        elementId TEXT,
                        charStart INTEGER NOT NULL,
                        charEnd INTEGER NOT NULL,
                        color TEXT NOT NULL,
                        note TEXT NOT NULL,
                        quote TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_readalong_annotations_book ON readalong_annotations(bookId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS readalong_bookmarks (
                        id TEXT NOT NULL PRIMARY KEY,
                        bookId TEXT NOT NULL,
                        chapterId TEXT NOT NULL,
                        chapterHref TEXT NOT NULL,
                        label TEXT NOT NULL,
                        charOffset INTEGER NOT NULL,
                        audioPositionMs INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_readalong_bookmarks_book ON readalong_bookmarks(bookId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS readalong_markers (
                        bookId TEXT NOT NULL,
                        chapterId TEXT NOT NULL,
                        charOffset INTEGER NOT NULL,
                        unitIndex INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(bookId, chapterId)
                    )
                    """
                )
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE readalong_progress ADD COLUMN pageProgress REAL NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_usage_daily (
                        mediaType TEXT NOT NULL,
                        mediaId TEXT NOT NULL,
                        dayStart INTEGER NOT NULL,
                        listenedMs INTEGER NOT NULL DEFAULT 0,
                        readMs INTEGER NOT NULL DEFAULT 0,
                        playCount INTEGER NOT NULL DEFAULT 0,
                        completionCount INTEGER NOT NULL DEFAULT 0,
                        lastPlayedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(mediaType, mediaId, dayStart)
                    )
                    """
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO media_usage_daily(
                        mediaType, mediaId, dayStart, listenedMs, lastPlayedAt
                    )
                    SELECT 'AUDIOBOOK', bookId, 0, totalListenedMs, lastListenedAt
                    FROM readalong_progress
                    WHERE totalListenedMs > 0
                    """
                )
            }
        }
    }
}
