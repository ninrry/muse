package luzzr.muse.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import luzzr.muse.data.repository.ArtworkRepositoryImpl
import luzzr.muse.data.repository.BookCollectionRepositoryImpl
import luzzr.muse.data.repository.LyricsRepository
import luzzr.muse.data.repository.SongRepositoryImpl
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.SongRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSongRepository(impl: SongRepositoryImpl): SongRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepository): luzzr.muse.domain.repository.LyricsRepository

    @Binds
    @Singleton
    abstract fun bindArtworkRepository(impl: ArtworkRepositoryImpl): ArtworkRepository

    @Binds
    @Singleton
    abstract fun bindBookCollectionRepository(impl: BookCollectionRepositoryImpl): BookCollectionRepository
}
