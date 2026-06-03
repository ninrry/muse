package luzzr.muse.ui.state

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.core.log.MuseLog
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.player.MusicService
import luzzr.muse.player.PlayerState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class SessionRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepo: MusicRepositoryFacade,
    private val playerState: PlayerState
) {

    suspend fun restoreIfNeeded() {
        if (playerState.currentPlaylist.value.isNotEmpty()) return
        if (!playerState.hasSavedSession()) return

        MuseLog.w("SessionRestoreManager", "restoreIfNeeded: playlist empty, restoring saved session")
        val ids = playerState.getSavedPlaylistIds()
        if (ids.isEmpty()) return
        val (savedIndex, savedPos) = playerState.getSavedPlaybackInfo()
        MuseLog.w("SessionRestoreManager", "restoreSessionFromPrefs: ids=${ids.size} idx=$savedIndex pos=$savedPos")
        try {
            val allSongs = musicRepo.songs.value.let { songs ->
                if (songs.isEmpty()) musicRepo.loadFromDatabase() else songs
            }
            val savedSongs = ids.mapNotNull { id -> allSongs.find { it.id == id } }
            if (savedSongs.isEmpty()) {
                MuseLog.w("SessionRestoreManager", "restoreSessionFromPrefs: no matching songs, clearing")
                playerState.clearSavedSession()
                return
            }
            try {
                context.startForegroundService(Intent(context, MusicService::class.java))
            } catch (e: IllegalStateException) {
                MuseLog.e("SessionRestoreManager", "Failed to start service", e)
            }
            delay(200)
            playerState.playSongs(savedSongs, savedIndex.coerceIn(0, savedSongs.size - 1))
            if (savedPos > 0) {
                delay(100)
                playerState.seekTo(savedPos)
            }
            if (playerState.getSavedShuffleMode()) {
                playerState.toggleShuffle()
            }
            delay(200)
            if (playerState.isPlaying.value) {
                playerState.togglePlayPause()
            }
            MuseLog.w("SessionRestoreManager", "restoreSessionFromPrefs: done, ${savedSongs.size} songs restored")
        } catch (e: Exception) {
            MuseLog.e("SessionRestoreManager", "restoreSessionFromPrefs failed", e)
        }
    }
}
