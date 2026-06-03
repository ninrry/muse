package luzzr.muse.ui.screens.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    innerPadding: PaddingValues = PaddingValues()
) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanStats by viewModel.scanStats.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val coverGenState by viewModel.coverGenState.collectAsStateWithLifecycle()

    SettingsScreen(
        isScanning = isScanning,
        scanProgress = scanProgress,
        scanStats = scanStats,
        songs = songs,
        isDarkTheme = isDarkTheme,
        isDarkThemeSupported = viewModel.isDarkThemeSupported,
        coverGenState = coverGenState,
        innerPadding = innerPadding,
        onToggleTheme = { viewModel.toggleTheme() },
        onScanAll = { viewModel.scanAll() },
        onScanFolder = { viewModel.scanFolder(it) },
        onGenerateAllDefaultCovers = { viewModel.generateAllDefaultCovers() }
    )
}
