package luzzr.muse.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.ui.state.asString
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel(),
    innerPadding: PaddingValues = PaddingValues(),
    hasPermission: Boolean = true,
    onRequestPermission: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, context) {
        viewModel.uiEffect.collectLatest { effect ->
            when (effect) {
                is HomeUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message.asString(context))
                }
            }
        }
    }

    Box {
        HomeScreen(
            songs = uiState.songs,
            isScanning = uiState.isScanning,
            scanProgress = uiState.scanProgress,
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
