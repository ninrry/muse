package luzzr.muse.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel(),
    innerPadding: PaddingValues = PaddingValues(),
    hasPermission: Boolean = true,
    onRequestPermission: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is HomeUiEffect.ShowSnackbar -> {
                    // Handle snackbar display
                }
                HomeUiEffect.NavigateToPlayer -> {
                    // Handle navigation
                }
            }
        }
    }

    HomeScreen(
        songs = uiState.songs,
        isScanning = uiState.isScanning,
        scanProgress = uiState.scanProgress,
        stats = uiState.stats,
        currentSong = uiState.currentSong,
        greeting = uiState.greeting,
        innerPadding = innerPadding,
        hasPermission = hasPermission,
        onRequestPermission = onRequestPermission,
        onScan = { viewModel.onEvent(HomeUiEvent.ScanAll) },
        onPlayAll = { viewModel.onEvent(HomeUiEvent.PlayAll) },
        onShuffle = { viewModel.onEvent(HomeUiEvent.PlayShuffled) },
        onPlaySong = { index -> viewModel.onEvent(HomeUiEvent.PlaySong(index)) }
    )
}
