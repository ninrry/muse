package luzzr.muse.ui.screens.home

import luzzr.muse.domain.model.GreetingPeriod
import luzzr.muse.domain.model.Playlist
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.state.UiText

data class HomeUiState(
    val songs: List<Song> = emptyList(),
    val dailyRecommendation: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val currentSong: Song? = null,
    val greeting: GreetingPeriod = GreetingPeriod.MORNING,
    val hasPermission: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface HomeUiEvent {
    data object ScanAll : HomeUiEvent
    data object PlayAll : HomeUiEvent
    data object PlayShuffled : HomeUiEvent
    data class PlaySong(val song: Song) : HomeUiEvent
    data object RequestPermission : HomeUiEvent
    data class CreatePlaylist(val name: String) : HomeUiEvent
    data class PlayPlaylist(val playlistId: Long) : HomeUiEvent
}

sealed interface HomeUiEffect {
    data class ShowSnackbar(val message: UiText) : HomeUiEffect
}
