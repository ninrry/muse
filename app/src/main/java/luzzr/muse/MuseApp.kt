package luzzr.muse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import luzzr.muse.core.log.MuseDebugTree
import luzzr.muse.core.log.MuseReleaseTree
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.player.PlayerState
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MuseApp : Application() {

    @Inject lateinit var playerState: PlayerState

    @Inject lateinit var repository: MusicRepositoryFacade

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(MuseDebugTree())
        } else {
            Timber.plant(MuseReleaseTree())
        }

        // Hilt member injection is complete after super.onCreate().
        playerState.initSessionPrefs(getSharedPreferences("player_session", MODE_PRIVATE))

        // Load cached library metadata on startup. Runtime scanning is
        // triggered from MainActivity after the user grants audio permission.
        appScope.launch(Dispatchers.IO) {
            val loadedSongs = repository.loadFromDatabase()
            if (loadedSongs.isNotEmpty()) {
                repository.generateMissingCovers()
            }
        }
    }
}
