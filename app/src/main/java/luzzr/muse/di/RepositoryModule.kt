package luzzr.muse.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import luzzr.muse.data.repository.ArtworkRepository as ArtworkRepositoryImpl
import luzzr.muse.data.repository.BookCollectionRepositoryImpl
import luzzr.muse.data.repository.EbookMetadataRepositoryImpl
import luzzr.muse.data.repository.LyricsRepository
import luzzr.muse.data.repository.MediaUsageRepositoryImpl
import luzzr.muse.data.repository.PlaylistRepositoryImpl
import luzzr.muse.data.repository.ReadAlongRepositoryImpl
import luzzr.muse.data.repository.SongRepositoryImpl
import luzzr.muse.domain.repository.ArtworkRepository
import luzzr.muse.domain.repository.BookCollectionRepository
import luzzr.muse.domain.repository.EbookMetadataRepository
import luzzr.muse.domain.repository.MediaUsageRepository
import luzzr.muse.domain.repository.PlaylistRepository
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

    @Binds
    @Singleton
    abstract fun bindEbookMetadataRepository(impl: EbookMetadataRepositoryImpl): EbookMetadataRepository

    @Binds
    @Singleton
    abstract fun bindMediaUsageRepository(impl: MediaUsageRepositoryImpl): MediaUsageRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindReadAlongRepository(impl: ReadAlongRepositoryImpl): luzzr.muse.domain.repository.ReadAlongRepository

    @Binds
    @Singleton
    abstract fun bindAudioFileHealthCheckUseCase(
        impl: luzzr.muse.data.tag.AudioFileHealthCheckUseCaseImpl
    ): luzzr.muse.domain.healthcheck.AudioFileHealthCheckUseCase
}
