package luzzr.muse.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is PlaylistDetailUiEffect.NavigateBack -> {
                    isDeleting = false
                    showDeleteDialog = false
                    onBack()
                }
                is PlaylistDetailUiEffect.ShowMessage -> {
                    isDeleting = false
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    BackHandler(enabled = showDeleteDialog || showEditDialog) {
        if (isDeleting) return@BackHandler
        showDeleteDialog = false
        showEditDialog = false
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
            onDismiss = { if (!isDeleting) showDeleteDialog = false },
            onConfirm = {
                if (!isDeleting) {
                    isDeleting = true
                    viewModel.onEvent(PlaylistDetailUiEvent.DeletePlaylist)
                }
            }
        )
    }

    // PlaylistDetailScreen owns the only TopAppBar and therefore the only status-bar
    // inset. A nested Scaffold here used to consume system insets once, then the
    // inner TopAppBar consumed them again, leaving a large empty band above the title.
    Box(modifier = Modifier.fillMaxSize()) {
        PlaylistDetailScreen(
            playlistName = uiState.playlist?.name ?: "",
            playlistArtworkUri = uiState.playlist?.artworkUri,
            songs = uiState.songs,
            isLoading = uiState.isLoading || isDeleting,
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = innerPadding.calculateBottomPadding())
        )
    }
}
