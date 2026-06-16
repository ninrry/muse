package luzzr.muse.ui.screens.settings

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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.AdminPanelSettings
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
import luzzr.muse.domain.model.CoverGenState
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import luzzr.muse.data.tag.AudioHealthProgress
import luzzr.muse.data.tag.RenameProgress
import luzzr.muse.feature.settings.R
import luzzr.muse.ui.screens.settings.components.SectionHeader
import luzzr.muse.ui.screens.settings.components.SettingItem
import luzzr.muse.ui.screens.settings.components.StatItem
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.WindowSize
import luzzr.muse.ui.theme.currentWindowSize
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isScanning: Boolean,
    scanProgress: Int,
    scanStats: ScanStats?,
    songs: List<Song>,
    themeMode: luzzr.muse.domain.preferences.ThemeMode,
    isDarkThemeSupported: Boolean,
    coverGenState: CoverGenState,
    hasAudioPermission: Boolean,
    hasFullFileAccess: Boolean,
    shizukuAvailable: Boolean = false,
    shizukuGranted: Boolean = false,
    innerPadding: PaddingValues = PaddingValues(),
    onRequestAudioPermission: () -> Unit = {},
    onRequestFullFileAccess: () -> Unit = {},
    onRequestShizukuPermission: () -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onScanAll: () -> Unit = {},
    onScanFolder: (String) -> Unit = {},
    onGenerateAllDefaultCovers: () -> Unit = {},
    renameProgress: RenameProgress? = null,
    onRenameAllToTags: () -> Unit = {},
    onDismissRenameResult: () -> Unit = {},
    audioHealthProgress: AudioHealthProgress? = null,
    onCheckAudioHealth: () -> Unit = {},
    onDismissAudioHealthResult: () -> Unit = {}
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
                        PermissionSection(
                            hasAudioPermission,
                            hasFullFileAccess,
                            shizukuAvailable,
                            shizukuGranted,
                            onRequestAudioPermission,
                            onRequestFullFileAccess,
                            onRequestShizukuPermission,
                            onRefreshPermissions
                        )
                        AppearanceSection(themeMode, isDarkThemeSupported, onToggleTheme)
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
                        RenameSection(renameProgress, onRenameAllToTags, onDismissRenameResult)
                        AudioHealthSection(audioHealthProgress, onCheckAudioHealth, onDismissAudioHealthResult)
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
                    PermissionSection(
                        hasAudioPermission,
                        hasFullFileAccess,
                        shizukuAvailable,
                        shizukuGranted,
                        onRequestAudioPermission,
                        onRequestFullFileAccess,
                        onRequestShizukuPermission,
                        onRefreshPermissions
                    )
                    AppearanceSection(themeMode, isDarkThemeSupported, onToggleTheme)
                    StatsSection(songs)
                    ScanSection(isScanning, scanProgress, scanStats, onScanAll, folderPicker)
                    CoverSection(coverGenState, onGenerateAllDefaultCovers)
                    FormatsSection()
                    RenameSection(renameProgress, onRenameAllToTags, onDismissRenameResult)
                    AudioHealthSection(audioHealthProgress, onCheckAudioHealth, onDismissAudioHealthResult)
                    Spacer(Modifier.height(AppSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun PermissionSection(
    hasAudioPermission: Boolean,
    hasFullFileAccess: Boolean,
    shizukuAvailable: Boolean,
    shizukuGranted: Boolean,
    onRequestAudioPermission: () -> Unit,
    onRequestFullFileAccess: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_permissions))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(
                stringResource(R.string.settings_permissions_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppSpacing.sm))
            PermissionItem(
                icon = Icons.Default.LibraryMusic,
                title = stringResource(R.string.settings_audio_permission),
                subtitle = stringResource(R.string.settings_audio_permission_description),
                granted = hasAudioPermission,
                onRequest = onRequestAudioPermission
            )
            HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
            PermissionItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_file_permission),
                subtitle = stringResource(R.string.settings_file_permission_description),
                granted = hasFullFileAccess,
                onRequest = onRequestFullFileAccess
            )
            HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
            PermissionItem(
                icon = Icons.Default.AdminPanelSettings,
                title = stringResource(R.string.settings_shizuku_permission),
                subtitle = if (shizukuAvailable) {
                    if (shizukuGranted) stringResource(R.string.settings_shizuku_granted)
                    else stringResource(R.string.settings_shizuku_not_granted)
                } else {
                    stringResource(R.string.settings_shizuku_unavailable)
                },
                granted = shizukuGranted,
                onRequest = onRequestShizukuPermission
            )
            Spacer(Modifier.height(AppSpacing.sm))
            FilledTonalButton(
                onClick = onRefreshPermissions,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(MuseDimens.CornerRadiusMedium)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
                Spacer(Modifier.width(AppSpacing.xxs))
                Text(stringResource(R.string.settings_permission_recheck))
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(AppSpacing.lg)
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (granted) {
                    stringResource(R.string.settings_permission_granted)
                } else {
                    stringResource(R.string.settings_permission_missing)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        FilledTonalButton(
            onClick = onRequest,
            enabled = !granted,
            shape = RoundedCornerShape(MuseDimens.CornerRadiusMedium)
        ) {
            Text(
                if (granted) {
                    stringResource(R.string.settings_permission_done)
                } else {
                    stringResource(R.string.settings_permission_grant)
                }
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: luzzr.muse.domain.preferences.ThemeMode,
    isDarkThemeSupported: Boolean,
    onToggleTheme: () -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_appearance))
    if (isDarkThemeSupported) {
        val icon = when (themeMode) {
            luzzr.muse.domain.preferences.ThemeMode.DARK -> Icons.Default.DarkMode
            luzzr.muse.domain.preferences.ThemeMode.LIGHT -> Icons.Default.LightMode
            luzzr.muse.domain.preferences.ThemeMode.SYSTEM -> Icons.Default.DarkMode
        }
        val subtitle = when (themeMode) {
            luzzr.muse.domain.preferences.ThemeMode.SYSTEM -> stringResource(R.string.settings_dark_theme_system)
            luzzr.muse.domain.preferences.ThemeMode.LIGHT -> stringResource(R.string.settings_dark_theme_off_mode)
            luzzr.muse.domain.preferences.ThemeMode.DARK -> stringResource(R.string.settings_dark_theme_on_mode)
        }
        SettingItem(
            icon = icon,
            title = stringResource(R.string.settings_dark_theme),
            subtitle = subtitle,
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
                    if (hours > 0) {
                        stringResource(R.string.settings_hours_minutes, hours.toInt(), minutes.toInt())
                    } else {
                        stringResource(R.string.settings_minutes, minutes.toInt())
                    },
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
private fun CoverSection(coverGenState: CoverGenState, onGenerateAllDefaultCovers: () -> Unit) {
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

@Composable
private fun RenameSection(progress: RenameProgress?, onRename: () -> Unit, onDismissResult: () -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_rename_title))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(stringResource(R.string.settings_rename_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(AppSpacing.sm))
            if (progress == null) {
                FilledTonalButton(onClick = onRename, Modifier.fillMaxWidth(), shape = RoundedCornerShape(MuseDimens.CornerRadiusMedium)) {
                    Text(stringResource(R.string.settings_rename_button))
                }
            } else if (progress.current < progress.total) {
                Text(stringResource(R.string.settings_rename_progress, progress.current, progress.total, progress.success, progress.failed))
                LinearProgressIndicator(progress = progress.current.toFloat() / progress.total, modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs))
            } else {
                Text(stringResource(R.string.settings_rename_done, progress.success, progress.failed))
                FilledTonalButton(onClick = onDismissResult) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun AudioHealthSection(
    progress: AudioHealthProgress?,
    onCheck: () -> Unit,
    onDismissResult: () -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_audio_health_title))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = RoundedCornerShape(MuseDimens.CornerRadiusPill)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Text(
                stringResource(R.string.settings_audio_health_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppSpacing.sm))
            when {
                progress == null -> {
                    FilledTonalButton(
                        onClick = onCheck,
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(MuseDimens.CornerRadiusMedium)
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
                        Spacer(Modifier.width(AppSpacing.xxs))
                        Text(stringResource(R.string.settings_audio_health_button))
                    }
                }
                !progress.isFinished -> {
                    Text(
                        stringResource(
                            R.string.settings_audio_health_progress,
                            progress.current,
                            progress.total
                        )
                    )
                    LinearProgressIndicator(
                        progress = progress.current.toFloat() / progress.total.coerceAtLeast(1),
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs)
                    )
                }
                else -> {
                    Text(
                        stringResource(
                            R.string.settings_audio_health_done,
                            progress.healthy,
                            progress.damaged,
                            progress.missing,
                            progress.unsupported
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val problemCount = progress.damaged + progress.missing + progress.unsupported
                    if (problemCount > 0) {
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            stringResource(R.string.settings_audio_health_advice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        progress.examples.forEach { example ->
                            Text(
                                "• $example",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.sm))
                    FilledTonalButton(onClick = onDismissResult) { Text(stringResource(R.string.settings_close)) }
                }
            }
        }
    }
}

private fun formatStorage(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
