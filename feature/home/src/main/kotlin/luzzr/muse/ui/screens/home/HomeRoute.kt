package luzzr.muse.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.feature.home.R
import luzzr.muse.ui.state.asString
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel(),
    innerPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
    hasPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, context) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is HomeUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message.asString(context))
                }
            }
        }
    }

    // 创建歌单对话框
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                viewModel.onEvent(HomeUiEvent.CreatePlaylist(name))
                showCreatePlaylistDialog = false
            }
        )
    }

    Box {
        HomeScreen(
            songs = uiState.songs,
            dailyRecommendation = uiState.dailyRecommendation,
            playlists = uiState.playlists,
            isScanning = uiState.isScanning,
            scanProgress = uiState.scanProgress,
            currentSong = uiState.currentSong,
            greeting = uiState.greeting,
            innerPadding = innerPadding,
            hasPermission = hasPermission,
            onRequestPermission = onRequestPermission,
            onScan = { viewModel.onEvent(HomeUiEvent.ScanAll) },
            onPlaySong = { song -> viewModel.onEvent(HomeUiEvent.PlaySong(song)) },
            onPlaylistClick = { playlistId ->
                onNavigateToPlaylist(playlistId)
            },
            onCreatePlaylist = { showCreatePlaylistDialog = true }
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_create_playlist)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(playlistName) },
                enabled = playlistName.isNotBlank()
            ) {
                Text(stringResource(R.string.dialog_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}