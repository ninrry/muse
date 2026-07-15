package luzzr.muse

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import luzzr.muse.core.log.MuseDebugTree
import luzzr.muse.core.log.MuseLog
import luzzr.muse.core.log.MuseReleaseTree
import luzzr.muse.core.log.TimberMuseLogSink
import luzzr.muse.player.PlayerState
import luzzr.muse.work.PeriodicWorkScheduler
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MuseApp : Application(), Configuration.Provider, coil.ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject lateinit var playerState: PlayerState

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(MuseDebugTree())
        } else {
            Timber.plant(MuseReleaseTree())
        }
        MuseLog.install(TimberMuseLogSink)

        // Hilt member injection is complete after super.onCreate().
        playerState.initSession(getSharedPreferences("player_session", MODE_PRIVATE))
        applicationScope.launch {
            PeriodicWorkScheduler.schedule(this@MuseApp)
        }
    }

    override fun newImageLoader(): coil.ImageLoader {
        return coil.ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024L)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
