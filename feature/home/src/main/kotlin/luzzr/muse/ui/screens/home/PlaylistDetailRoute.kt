package luzzr.muse.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PlaylistDetailRoute(
    playlistId: Long,
    innerPadding: PaddingValues = PaddingValues(),
    onBack: () -> Unit = {}
) {
    val viewModel: PlaylistDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is PlaylistDetailUiEffect.NavigateBack -> onBack()
                is PlaylistDetailUiEffect.ShowMessage -> {
                }
            }
        }
    }

    if (showEditDialog) {
        EditPlaylistDialog(
            currentName = uiState.playlist?.name ?: "",
            currentDescription = uiState.playlist?.description ?: "",
            onDismiss = { showEditDialog = false },
            onConfirm = { name, description ->
                viewModel.onEvent(PlaylistDetailUiEvent.EditPlaylist(name, description))
                showEditDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        DeletePlaylistDialog(
            playlistName = uiState.playlist?.name ?: "",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.onEvent(PlaylistDetailUiEvent.DeletePlaylist)
                showDeleteDialog = false
            }
        )
    }

    PlaylistDetailScreen(
        playlistName = uiState.playlist?.name ?: "",
        playlistArtworkUri = uiState.playlist?.artworkUri,
        songs = uiState.songs,
        isLoading = uiState.isLoading,
        currentSong = uiState.currentSong,
        innerPadding = innerPadding,
        onBack = onBack,
        onPlayAll = { viewModel.onEvent(PlaylistDetailUiEvent.PlayAll) },
        onShuffle = { viewModel.onEvent(PlaylistDetailUiEvent.Shuffle) },
        onPlaySong = { index -> viewModel.onEvent(PlaylistDetailUiEvent.PlaySong(index)) },
        onEdit = { showEditDialog = true },
        onDelete = { showDeleteDialog = true },
        onUpdateArtwork = { uri -> viewModel.onEvent(PlaylistDetailUiEvent.UpdateArtwork(uri)) },
        onRemoveSong = { songId -> viewModel.onEvent(PlaylistDetailUiEvent.RemoveSong(songId)) }
    )
}