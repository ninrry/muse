package luzzr.muse.ui.screens.home

import luzzr.muse.data.model.Song

data class HomeUiState(
    val songs: List<Song> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val stats: HomeStats = HomeStats(0, 0, 0, 0, 0),
    val currentSong: Song? = null,
    val greeting: String = "",
    val hasPermission: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface HomeUiEvent {
    data object ScanAll : HomeUiEvent
    data object PlayAll : HomeUiEvent
    data object PlayShuffled : HomeUiEvent
    data class PlaySong(val index: Int) : HomeUiEvent
    data object RequestPermission : HomeUiEvent
}

sealed interface HomeUiEffect {
    data object NavigateToPlayer : HomeUiEffect
    data class ShowSnackbar(val message: String) : HomeUiEffect
}
