package luzzr.muse.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlaylistDetailRoute(
    playlistId: Long,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {}
) {
    val viewModel: PlaylistDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Load playlist when composable enters composition
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    PlaylistDetailScreen(
        playlistName = uiState.playlist?.name ?: "",
        songs = uiState.songs,
        isLoading = uiState.isLoading,
        currentSong = uiState.currentSong,
        innerPadding = innerPadding,
        onBack = onBack,
        onPlayAll = { viewModel.onEvent(PlaylistDetailUiEvent.PlayAll) },
        onShuffle = { viewModel.onEvent(PlaylistDetailUiEvent.Shuffle) },
        onPlaySong = { index -> viewModel.onEvent(PlaylistDetailUiEvent.PlaySong(index)) }
    )
}