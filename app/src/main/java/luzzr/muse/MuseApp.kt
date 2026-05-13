package luzzr.muse

import android.app.Application
import android.content.Intent
import android.os.Build
import luzzr.muse.data.database.MuseDatabase
import luzzr.muse.data.repository.MusicRepository
import luzzr.muse.player.MusicService
import luzzr.muse.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MuseApp : Application() {

    val playerState: PlayerState by lazy { PlayerState() }

    val database: MuseDatabase by lazy { MuseDatabase.getInstance(this) }

    val repository: MusicRepository by lazy { MusicRepository.getInstance(this) }

    // Theme state
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        // Trigger lazy initialization
        database
        repository

        // Start MusicService early so player is always ready when user taps play
        val serviceIntent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Auto-scan on first launch when database is empty
        appScope.launch(Dispatchers.IO) {
            val songs = repository.loadFromDatabase()
            if (songs.isEmpty()) {
                repository.scanAll()
            }
            // Set initial current song for MiniPlayer UI (no auto-play)
            val loadedSongs = repository.loadFromDatabase()
            if (loadedSongs.isNotEmpty()) {
                playerState.updateCurrentSong(loadedSongs[0])
            }
            // Generate missing default covers in background
            repository.generateMissingCovers()
        }
    }
}
