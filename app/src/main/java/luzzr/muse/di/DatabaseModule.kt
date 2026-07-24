package luzzr.muse.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import luzzr.muse.data.database.AlbumDao
import luzzr.muse.data.database.ArtistDao
import luzzr.muse.data.database.BookCollectionDao
import luzzr.muse.data.database.LyricsDao
import luzzr.muse.data.database.LyricsOffsetDao
import luzzr.muse.data.database.MuseDatabase
import luzzr.muse.data.database.PlaylistDao
import luzzr.muse.data.database.ReadAlongDao
import luzzr.muse.data.database.SongDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MuseDatabase = MuseDatabase.getInstance(context)

    @Provides
    fun provideSongDao(db: MuseDatabase): SongDao = db.songDao()

    @Provides
    fun provideAlbumDao(db: MuseDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideArtistDao(db: MuseDatabase): ArtistDao = db.artistDao()

    @Provides
    fun provideLyricsDao(db: MuseDatabase): LyricsDao = db.lyricsDao()

    @Provides
    fun provideLyricsOffsetDao(db: MuseDatabase): LyricsOffsetDao = db.lyricsOffsetDao()

    @Provides
    fun provideBookCollectionDao(db: MuseDatabase): BookCollectionDao = db.bookCollectionDao()

    @Provides
    fun providePlaylistDao(db: MuseDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideReadAlongDao(db: MuseDatabase): ReadAlongDao = db.readAlongDao()
}
