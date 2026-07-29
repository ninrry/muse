package luzzr.muse.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import luzzr.muse.data.network.AndroidTextNormalizer
import luzzr.muse.data.network.LyricsFetcher
import luzzr.muse.data.network.MetadataFetcher
import luzzr.muse.data.network.defaultMuseConfig
import luzzr.muse.domain.lyrics.LyricsSearchClient
import luzzr.muse.domain.metadata.MetadataSearchClient
import luzzr.muse.domain.text.TextNormalizer
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().defaultMuseConfig().build()

    @Provides
    @Singleton
    fun provideLyricsSearchClient(okHttpClient: OkHttpClient): LyricsSearchClient =
        LyricsFetcher(okHttpClient)

    @Provides
    @Singleton
    fun provideMetadataSearchClient(okHttpClient: OkHttpClient): MetadataSearchClient =
        MetadataFetcher(okHttpClient)

    @Provides
    @Singleton
    fun provideTextNormalizer(): TextNormalizer = AndroidTextNormalizer()
}
