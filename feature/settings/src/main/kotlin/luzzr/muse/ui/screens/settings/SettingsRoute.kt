package luzzr.muse.ui.screens.settings

import android.os.Build
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
    hasNotificationPermission: Boolean = true,
    onRequestAudioPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanStats by viewModel.scanStats.collectAsStateWithLifecycle()
    val musicStats by viewModel.musicStats.collectAsStateWithLifecycle()
    val readAlongStats by viewModel.readAlongStats.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isAudiobookVisible by viewModel.isAudiobookVisible.collectAsStateWithLifecycle()
    val hasFullFileAccess by viewModel.hasFullFileAccess.collectAsStateWithLifecycle()
    val audioHealthProgress by viewModel.audioHealthProgress.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionSnapshot = PermissionSnapshot(
        audio = if (hasAudioPermission) PermissionStatus.GRANTED else PermissionStatus.MISSING,
        fullFileAccess = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> PermissionStatus.NOT_REQUIRED
            hasFullFileAccess -> PermissionStatus.GRANTED
            else -> PermissionStatus.MISSING
        },
        notifications = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PermissionStatus.NOT_REQUIRED
        } else if (hasNotificationPermission) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.MISSING
        }
    )

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
        musicStats = musicStats,
        readAlongStats = readAlongStats,
        themeMode = themeMode,
        isAudiobookVisible = isAudiobookVisible,
        isDarkThemeSupported = viewModel.isDarkThemeSupported,
        permissionSnapshot = permissionSnapshot,
        innerPadding = innerPadding,
        onRequestAudioPermission = onRequestAudioPermission,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onRequestFullFileAccess = viewModel::requestFullFileAccess,
        onRefreshPermissions = viewModel::refreshPermissionState,
        onToggleTheme = { viewModel.toggleTheme() },
        onToggleAudiobookVisibility = viewModel::toggleAudiobookVisibility,
        onScanAll = { viewModel.scanAll() },
        audioHealthProgress = audioHealthProgress,
        onCheckAudioHealth = viewModel::checkAudioFileHealth,
        onDismissAudioHealthResult = viewModel::dismissAudioHealthResult
    )
}
