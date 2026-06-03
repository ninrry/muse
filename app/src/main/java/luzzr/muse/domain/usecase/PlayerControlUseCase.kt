package luzzr.muse.domain.usecase

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import luzzr.muse.domain.model.Song
import luzzr.muse.player.MusicService
import luzzr.muse.player.PlayerState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerControlUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerState: PlayerState
) {
    private fun startService() {
        try {
            context.startForegroundService(Intent(context, MusicService::class.java))
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        startService()
        playerState.togglePlayPause()
    }

    fun skipToNext() {
        startService()
        playerState.skipToNext()
    }

    fun skipToPrevious() {
        startService()
        playerState.skipToPrevious()
    }

    fun seekTo(position: Long) {
        startService()
        playerState.seekTo(position)
    }

    fun playSongAtIndex(list: List<Song>, index: Int) {
        if (index in list.indices) {
            startService()
            playerState.playSongs(list, index)
        }
    }
}
