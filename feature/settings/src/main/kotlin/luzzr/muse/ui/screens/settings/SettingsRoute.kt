package luzzr.muse.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    innerPadding: PaddingValues = PaddingValues(),
    hasAudioPermission: Boolean = false,
    onRequestAudioPermission: () -> Unit = {}
) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanStats by viewModel.scanStats.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val hasFullFileAccess by viewModel.hasFullFileAccess.collectAsStateWithLifecycle()
    val audioHealthProgress by viewModel.audioHealthProgress.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreen(
        isScanning = isScanning,
        scanProgress = scanProgress,
        scanStats = scanStats,
        songs = songs,
        themeMode = themeMode,
        isDarkThemeSupported = viewModel.isDarkThemeSupported,
        hasAudioPermission = hasAudioPermission,
        hasFullFileAccess = hasFullFileAccess,
        innerPadding = innerPadding,
        onRequestAudioPermission = onRequestAudioPermission,
        onRequestFullFileAccess = viewModel::requestFullFileAccess,
        onRefreshPermissions = viewModel::refreshPermissionState,
        onToggleTheme = { viewModel.toggleTheme() },
        onScanAll = { viewModel.scanAll() },
        onScanFolder = { viewModel.scanFolder(it) },
        audioHealthProgress = audioHealthProgress,
        onCheckAudioHealth = viewModel::checkAudioFileHealth,
        onDismissAudioHealthResult = viewModel::dismissAudioHealthResult
    )
}
