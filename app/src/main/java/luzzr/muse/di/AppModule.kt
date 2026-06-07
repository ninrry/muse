package luzzr.muse.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import luzzr.muse.data.scanner.SharedPreferencesScanHistoryStore
import luzzr.muse.data.search.SharedPreferencesMetadataSearchHistoryStore
import luzzr.muse.domain.artwork.DefaultCoverGenerationController
import luzzr.muse.domain.metadata.MetadataSearchHistoryStore
import luzzr.muse.domain.preferences.ThemePreferenceController
import luzzr.muse.domain.scanner.LibraryScanController
import luzzr.muse.domain.scanner.RepositoryLibraryScanController
import luzzr.muse.domain.scanner.ScanHistoryStore
import luzzr.muse.domain.usecase.GenerateAllDefaultCoversUseCase
import luzzr.muse.domain.usecase.PlayerControlUseCase
import luzzr.muse.media.PlaybackActionController
import luzzr.muse.media.PlaybackController
import luzzr.muse.player.PlayerState
import luzzr.muse.ui.state.AndroidStoragePermissionController
import luzzr.muse.ui.state.LyricsStateHolder
import luzzr.muse.ui.state.PlayerLyricsController
import luzzr.muse.ui.state.SessionRestoreController
import luzzr.muse.ui.state.SessionRestoreManager
import luzzr.muse.ui.state.StoragePermissionController
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module that provides application-scoped singletons.
 *
 * Repositories use constructor injection directly (marked @Singleton on the class);
 * this module supplies the external / third-party singletons that cannot be
 * annotated (Room database, DAOs, PlayerState, network clients).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // -- Player State -----------------------------------------------

    @Provides
    @Singleton
    fun providePlayerState(): PlayerState = PlayerState()

    @Provides
    fun providePlaybackController(playerState: PlayerState): PlaybackController = playerState

    @Provides
    @Singleton
    fun provideThemePreferenceController(themeManager: ThemeManager): ThemePreferenceController = themeManager

    @Provides
    @Singleton
    fun provideScanHistoryStore(store: SharedPreferencesScanHistoryStore): ScanHistoryStore = store

    @Provides
    @Singleton
    fun provideMetadataSearchHistoryStore(store: SharedPreferencesMetadataSearchHistoryStore): MetadataSearchHistoryStore = store

    @Provides
    @Singleton
    fun provideLibraryScanController(controller: RepositoryLibraryScanController): LibraryScanController = controller

    @Provides
    @Singleton
    fun provideDefaultCoverGenerationController(useCase: GenerateAllDefaultCoversUseCase): DefaultCoverGenerationController = useCase

    @Provides
    @Singleton
    fun providePlaybackActionController(useCase: PlayerControlUseCase): PlaybackActionController = useCase

    @Provides
    @Singleton
    fun providePlayerLyricsController(holder: LyricsStateHolder): PlayerLyricsController = holder

    @Provides
    @Singleton
    fun provideSessionRestoreController(manager: SessionRestoreManager): SessionRestoreController = manager

    @Provides
    @Singleton
    fun provideStoragePermissionController(controller: AndroidStoragePermissionController): StoragePermissionController = controller

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

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
