package luzzr.muse.ui.screens.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import luzzr.muse.MuseApp
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepository
import luzzr.muse.player.MusicService
import luzzr.muse.player.PlayerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeStats(
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val totalDurationMs: Long,
    val totalStorageBytes: Long
) {
    val totalDurationFormatted: String
        get() {
            val hours = totalDurationMs / 3_600_000
            val minutes = (totalDurationMs % 3_600_000) / 60_000
            return if (hours > 0) "${hours}小时${minutes}分钟" else "${minutes}分钟"
        }

    val totalStorageFormatted: String
        get() = when {
            totalStorageBytes >= 1_073_741_824 -> "%.1f GB".format(totalStorageBytes / 1_073_741_824.0)
            totalStorageBytes >= 1_048_576 -> "%.1f MB".format(totalStorageBytes / 1_048_576.0)
            else -> "${totalStorageBytes / 1_024} KB"
        }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository = (application as MuseApp).repository
    private val playerState: PlayerState = (application as MuseApp).playerState

    val songs: StateFlow<List<Song>> = repository.songs
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Int> = repository.scanProgress

    val currentSong: StateFlow<Song?> = playerState.currentSong
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPlaying: StateFlow<Boolean> = playerState.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val stats: StateFlow<HomeStats> = songs.map { list ->
        HomeStats(
            songCount = list.size,
            albumCount = list.distinctBy { it.album }.size,
            artistCount = list.distinctBy { it.artist }.size,
            totalDurationMs = list.sumOf { it.duration },
            totalStorageBytes = list.sumOf { it.size }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats(0, 0, 0, 0, 0))

    val greeting: String
        get() = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }

    fun scanAll() {
        viewModelScope.launch { repository.scanAll() }
    }

    fun playAll(startIndex: Int = 0) {
        val list = songs.value
        if (list.isEmpty()) return
        startServiceAndPlay(list, startIndex)
    }

    fun playSongs(songList: List<Song>, startIndex: Int = 0) {
        if (songList.isEmpty()) return
        startServiceAndPlay(songList, startIndex)
    }

    /** Play all songs shuffled (random start + shuffle mode) */
    fun playShuffled() {
        val list = songs.value
        if (list.isEmpty()) return
        val ctx = getApplication<Application>()
        ctx.startForegroundService(Intent(ctx, MusicService::class.java))
        playerState.playShuffled(list)
    }

    private fun startServiceAndPlay(songs: List<Song>, startIndex: Int = 0) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MusicService::class.java)
        ctx.startForegroundService(intent)
        playerState.playSongs(songs, startIndex)
    }
}
