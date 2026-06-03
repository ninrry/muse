package luzzr.muse.ui.screens.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import luzzr.muse.BuildConfig
import luzzr.muse.R
import luzzr.muse.data.model.Song
import luzzr.muse.data.repository.CoverGenState
import luzzr.muse.data.repository.ScanStats
import luzzr.muse.ui.screens.settings.components.SectionHeader
import luzzr.muse.ui.screens.settings.components.SettingItem
import luzzr.muse.ui.screens.settings.components.StatItem
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.WindowSize
import luzzr.muse.ui.theme.currentWindowSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isScanning: Boolean,
    scanProgress: Int,
    scanStats: ScanStats?,
    songs: List<Song>,
    isDarkTheme: Boolean,
    isDarkThemeSupported: Boolean,
    coverGenState: CoverGenState,
    innerPadding: PaddingValues = PaddingValues(),
    onToggleTheme: () -> Unit = {},
    onScanAll: () -> Unit = {},
    onScanFolder: (String) -> Unit = {},
    onGenerateAllDefaultCovers: () -> Unit = {}
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val path = safTreeUriToPath(uri)
            if (path != null) {
                onScanFolder(path)
            }
        }
    }

    val windowSize = currentWindowSize()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when (windowSize) {
            WindowSize.Medium, WindowSize.Expanded -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        AppearanceSection(isDarkTheme, isDarkThemeSupported, onToggleTheme)
                        StatsSection(songs)
                        ScanSection(isScanning, scanProgress, scanStats, onScanAll, folderPicker)
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        CoverSection(coverGenState, onGenerateAllDefaultCovers)
                        FormatsSection()
                        Spacer(Modifier.height(AppSpacing.lg))
                    }
                }
            }
            WindowSize.Compact -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .verticalScroll(rememberScrollState())
                ) {
                    AppearanceSection(isDarkTheme, isDarkThemeSupported, onToggleTheme)
                    StatsSection(songs)
                    ScanSection(isScanning, scanProgress, scanStats, onScanAll, folderPicker)
                    CoverSection(coverGenState, onGenerateAllDefaultCovers)
                    FormatsSection()
                    Spacer(Modifier.height(AppSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    isDarkTheme: Boolean,
    isDarkThemeSupported: Boolean,
    onToggleTheme: () -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_appearance))
    if (isDarkThemeSupported) {
        SettingItem(
            icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
            title = stringResource(R.string.settings_dark_theme),
            subtitle = if (isDarkTheme) {
                stringResource(R.string.settings_dark_theme_on)
            } else {
                stringResource(R.string.settings_dark_theme_off)
            },
            onClick = onToggleTheme
        )
    }
}

@Composable
private fun StatsSection(songs: List<Song>) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_stats))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${songs.size}", stringResource(R.string.settings_songs))
                StatItem("${songs.distinctBy { it.album }.size}", stringResource(R.string.settings_albums))
            }
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${songs.distinctBy { it.artist }.size}", stringResource(R.string.settings_artists))
                val totalDuration = songs.sumOf { it.duration }
                val hours = totalDuration / 3_600_000
                val minutes = (totalDuration % 3_600_000) / 60_000
                StatItem(
                    if (hours > 0) "${hours}h${minutes}m" else "${minutes}分钟",
                    stringResource(R.string.settings_duration)
                )
            }
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val totalSize = songs.sumOf { it.size }
                StatItem(formatStorage(totalSize), stringResource(R.string.settings_storage))
            }
        }
    }
}

@Composable
private fun ScanSection(
    isScanning: Boolean,
    scanProgress: Int,
    scanStats: ScanStats?,
    onScanAll: () -> Unit,
    folderPicker: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_scan))
    SettingItem(
        icon = Icons.Default.Radar,
        title = stringResource(R.string.settings_scan_all),
        subtitle = stringResource(R.string.settings_scan_all_subtitle),
        onClick = onScanAll,
        enabled = !isScanning
    )
    SettingItem(
        icon = Icons.Default.FolderOpen,
        title = stringResource(R.string.settings_scan_folder),
        subtitle = stringResource(R.string.settings_scan_folder_subtitle),
        onClick = { folderPicker.launch(null) },
        enabled = !isScanning
    )
    if (isScanning) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
        ) {
            Column(Modifier.padding(AppSpacing.md)) {
                Text(stringResource(R.string.settings_scanning), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(AppSpacing.xs))
                LinearProgressIndicator(
                    progress = { scanProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(MuseDimens.ProgressBarHeight)
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text("$scanProgress%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    scanStats?.let { stats ->
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
        ) {
            Column(Modifier.padding(AppSpacing.md)) {
                Text(stringResource(R.string.settings_scan_complete), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(AppSpacing.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${stats.totalSongs}", stringResource(R.string.settings_songs))
                    StatItem("${stats.totalAlbums}", stringResource(R.string.settings_albums))
                    StatItem("${stats.totalArtists}", stringResource(R.string.settings_artists))
                }
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    stringResource(R.string.settings_scan_time, (stats.duration / 1000).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoverSection(
    coverGenState: CoverGenState,
    onGenerateAllDefaultCovers: () -> Unit
) {
    SectionHeader(stringResource(R.string.settings_cover_gen))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_cover_gen_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_cover_gen_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!coverGenState.isRunning) {
                    FilledTonalButton(
                        onClick = onGenerateAllDefaultCovers,
                        modifier = Modifier.height(MuseDimens.ButtonHeightSmall),
                        shape = RoundedCornerShape(MuseDimens.CornerRadiusMedium),
                        enabled = !coverGenState.isRunning
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
                        Spacer(Modifier.width(AppSpacing.xxs))
                        Text(stringResource(R.string.settings_cover_gen_start), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (coverGenState.isRunning) {
                Spacer(Modifier.height(AppSpacing.sm))
                LinearProgressIndicator(
                    progress = { coverGenState.processed.toFloat() / coverGenState.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(MuseDimens.ProgressBarHeight)
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    stringResource(R.string.settings_cover_gen_progress, coverGenState.processed, coverGenState.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!coverGenState.isRunning && coverGenState.total > 0) {
                Spacer(Modifier.height(AppSpacing.xs))
                val success = coverGenState.total - coverGenState.errors
                val color = if (coverGenState.errors == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Text(
                    if (coverGenState.errors == 0) {
                        stringResource(R.string.settings_cover_gen_done, success)
                    } else {
                        stringResource(R.string.settings_cover_gen_error, success, coverGenState.errors)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun FormatsSection() {
    SectionHeader(stringResource(R.string.settings_formats))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(
                stringResource(R.string.settings_formats_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatStorage(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
