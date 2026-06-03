package luzzr.muse.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.MusicRepositoryFacade
import luzzr.muse.domain.usecase.ScanAllSongsUseCase
import luzzr.muse.player.PlayerState
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repository: MusicRepositoryFacade,
    private val playerState: PlayerState,
    private val playerControlUseCase: luzzr.muse.domain.usecase.PlayerControlUseCase,
    private val scanAllSongsUseCase: ScanAllSongsUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<HomeUiEffect>()
    val uiEffect: SharedFlow<HomeUiEffect> = _uiEffect.asSharedFlow()

    init {
        // Initialize greeting
        _uiState.update { it.copy(greeting = getGreeting()) }

        // Observe songs from repository
        viewModelScope.launch {
            repository.songs.collect { songs ->
                _uiState.update { currentState ->
                    currentState.copy(
                        songs = songs,
                        stats = calculateStats(songs)
                    )
                }
            }
        }

        // Observe scanning state
        viewModelScope.launch {
            repository.isScanning.collect { isScanning ->
                _uiState.update { it.copy(isScanning = isScanning) }
            }
        }

        // Observe scan progress
        viewModelScope.launch {
            repository.scanProgress.collect { progress ->
                _uiState.update { it.copy(scanProgress = progress) }
            }
        }

        // Observe current song
        viewModelScope.launch {
            playerState.currentSong.collect { song ->
                _uiState.update { it.copy(currentSong = song) }
            }
        }
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.ScanAll -> scanAll()
            HomeUiEvent.PlayAll -> playAll()
            HomeUiEvent.PlayShuffled -> playShuffled()
            is HomeUiEvent.PlaySong -> playSong(event.index)
            HomeUiEvent.RequestPermission -> requestPermission()
        }
    }

    private fun scanAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                scanAllSongsUseCase()
            } catch (e: Exception) {
                _uiEffect.emit(HomeUiEffect.ShowSnackbar("扫描失败: ${e.message}"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun playAll(startIndex: Int = 0) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        startServiceAndPlay(songs, startIndex)
    }

    private fun playSong(index: Int) {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        startServiceAndPlay(songs, index)
    }

    private fun playShuffled() {
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return
        playerControlUseCase.playSongAtIndex(songs, 0)
        if (!playerState.shuffleMode.value) {
            playerState.toggleShuffle()
        }
    }

    private fun requestPermission() {
        // This would be handled by the UI layer
    }

    private fun startServiceAndPlay(songs: List<Song>, startIndex: Int = 0) {
        playerControlUseCase.playSongAtIndex(songs, startIndex)
    }

    private fun calculateStats(songs: List<Song>): HomeStats {
        return HomeStats(
            songCount = songs.size,
            albumCount = songs.distinctBy { it.album }.size,
            artistCount = songs.distinctBy { it.artist }.size,
            totalDurationMs = songs.sumOf { it.duration },
            totalStorageBytes = songs.sumOf { it.size }
        )
    }

    private fun getGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
    }
}
