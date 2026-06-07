package luzzr.muse.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import luzzr.muse.data.network.AndroidTextNormalizer
import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.data.network.MetadataFetcher
import luzzr.muse.domain.lyrics.LyricsSearchClient
import luzzr.muse.domain.metadata.MetadataSearchClient
import luzzr.muse.domain.text.TextNormalizer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLyricsSearchClient(): LyricsSearchClient = LyricsFetcher.getInstance()

    @Provides
    @Singleton
    fun provideMetadataSearchClient(): MetadataSearchClient = MetadataFetcher.getInstance()

    @Provides
    @Singleton
    fun provideTextNormalizer(): TextNormalizer = AndroidTextNormalizer()
}
