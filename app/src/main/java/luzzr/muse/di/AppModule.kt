package luzzr.muse.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import luzzr.muse.data.database.AlbumDao
import luzzr.muse.data.database.ArtistDao
import luzzr.muse.data.database.LyricsDao
import luzzr.muse.data.database.LyricsOffsetDao
import luzzr.muse.data.database.MuseDatabase
import luzzr.muse.data.database.SongDao
import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.data.network.MetadataFetcher
import luzzr.muse.data.repository.SongRepositoryImpl
import luzzr.muse.player.PlayerState
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module that provides application-scoped singletons.
 *
 * Repositories use constructor injection directly (marked @Singleton on the class);
 * this module supplies the external / third-party singletons that cannot be
 * annotated (Room database, DAOs, PlayerState, network fetchers).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // -- Database & DAOs --------------------------------------------

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

    // -- Player State -----------------------------------------------

    @Provides
    @Singleton
    fun providePlayerState(): PlayerState = PlayerState()

    // -- Network Fetchers -------------------------------------------

    @Provides
    @Singleton
    fun provideLyricsFetcher(): LyricsFetcher = LyricsFetcher.getInstance()

    @Provides
    @Singleton
    fun provideMetadataFetcher(): MetadataFetcher = MetadataFetcher.getInstance()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideSongRepository(impl: SongRepositoryImpl): luzzr.muse.domain.repository.SongRepository = impl

    @Provides
    @Singleton
    fun provideLyricsRepository(impl: luzzr.muse.data.repository.LyricsRepository): luzzr.muse.domain.repository.LyricsRepository = impl

    @Provides
    @Singleton
    fun provideArtworkRepository(impl: luzzr.muse.data.repository.ArtworkRepository): luzzr.muse.domain.repository.ArtworkRepository = impl

    @Provides
    @Singleton
    @Named("scan_prefs")
    fun provideScanPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("muse_scan_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @Named("search_prefs")
    fun provideSearchPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("muse_search_prefs", Context.MODE_PRIVATE)
}
