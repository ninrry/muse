package luzzr.muse.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.MusicUsageStats
import luzzr.muse.domain.model.ReadAlongUsageStats
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.healthcheck.AudioHealthProgress
import luzzr.muse.feature.settings.R
import luzzr.muse.ui.screens.settings.components.SectionHeader
import luzzr.muse.ui.screens.settings.components.SettingItem
import luzzr.muse.ui.screens.settings.components.StatItem
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens
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
    musicStats: MusicUsageStats = MusicUsageStats(),
    readAlongStats: ReadAlongUsageStats = ReadAlongUsageStats(),
    themeMode: luzzr.muse.domain.preferences.ThemeMode,
    isAudiobookVisible: Boolean = true,
    isDarkThemeSupported: Boolean,
    permissionSnapshot: PermissionSnapshot,
    innerPadding: PaddingValues = PaddingValues(),
    onRequestAudioPermission: () -> Unit = {},
    onRequestFullFileAccess: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onToggleAudiobookVisibility: () -> Unit = {},
    onScanAll: () -> Unit = {},
    audioHealthProgress: AudioHealthProgress? = null,
    onCheckAudioHealth: () -> Unit = {},
    onDismissAudioHealthResult: () -> Unit = {}
) {
    val windowSize = currentWindowSize()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (windowSize) {
            WindowSize.Medium, WindowSize.Expanded -> {
                Box(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .align(Alignment.Center)
                            .padding(padding)
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        PermissionSection(
                            permissionSnapshot,
                            onRequestAudioPermission,
                            onRequestFullFileAccess,
                            onRequestNotificationPermission,
                            onRefreshPermissions
                        )
                        AppearanceSection(themeMode, isDarkThemeSupported, onToggleTheme)
                        ContentSection(isAudiobookVisible, onToggleAudiobookVisibility)
                        StatsSection(songs)
                        MusicUsageSection(musicStats)
                        ReadAlongUsageSection(readAlongStats)
                        ScanSection(isScanning, scanProgress, scanStats, onScanAll)
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        FormatsSection()
                        AudioHealthSection(audioHealthProgress, onCheckAudioHealth, onDismissAudioHealthResult)
                    }
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
                        permissionSnapshot,
                        onRequestAudioPermission,
                        onRequestFullFileAccess,
                        onRequestNotificationPermission,
                        onRefreshPermissions
                    )
                    AppearanceSection(themeMode, isDarkThemeSupported, onToggleTheme)
                    ContentSection(isAudiobookVisible, onToggleAudiobookVisibility)
                    StatsSection(songs)
                    MusicUsageSection(musicStats)
                    ReadAlongUsageSection(readAlongStats)
                    ScanSection(isScanning, scanProgress, scanStats, onScanAll)
                    FormatsSection()
                    AudioHealthSection(audioHealthProgress, onCheckAudioHealth, onDismissAudioHealthResult)
                    Spacer(Modifier.height(AppSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun PermissionSection(
    snapshot: PermissionSnapshot,
    onRequestAudioPermission: () -> Unit,
    onRequestFullFileAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_permissions))
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = MuseShapeTokens.Card,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
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
                status = snapshot.audio,
                onRequest = onRequestAudioPermission
            )
            HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
            PermissionItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_file_permission),
                subtitle = stringResource(R.string.settings_file_permission_description),
                status = snapshot.fullFileAccess,
                onRequest = onRequestFullFileAccess
            )
            HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
            PermissionItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notification_permission),
                subtitle = stringResource(R.string.settings_notification_permission_description),
                status = snapshot.notifications,
                onRequest = onRequestNotificationPermission
            )
            Spacer(Modifier.height(AppSpacing.xs))
            FilledTonalButton(
                onClick = onRefreshPermissions,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = MuseDimens.TouchTarget),
                shape = MuseShapeTokens.Pill
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
    status: PermissionStatus,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MuseDimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when (status) {
                PermissionStatus.GRANTED, PermissionStatus.NOT_REQUIRED -> MaterialTheme.colorScheme.primary
                PermissionStatus.MISSING -> MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(AppSpacing.lg)
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when (status) {
            PermissionStatus.GRANTED -> PermissionStatusLabel(stringResource(R.string.settings_permission_granted))
            PermissionStatus.NOT_REQUIRED -> PermissionStatusLabel(stringResource(R.string.settings_permission_not_required))
            PermissionStatus.MISSING -> FilledTonalButton(
                onClick = onRequest,
                modifier = Modifier.heightIn(min = MuseDimens.TouchTarget),
                shape = MuseShapeTokens.Pill
            ) {
                Text(stringResource(R.string.settings_permission_grant))
            }
        }
    }
}

@Composable
private fun PermissionStatusLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AppSpacing.md)
        )
        Spacer(Modifier.width(AppSpacing.xxs))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
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
private fun ContentSection(
    isAudiobookVisible: Boolean,
    onToggleAudiobookVisibility: () -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_content))
    val interaction = remember { MutableInteractionSource() }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggleAudiobookVisibility
            ),
        shape = MuseShapeTokens.Card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MuseDimens.TouchTarget)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(AppSpacing.lg)
            )
            Spacer(Modifier.width(AppSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_show_audiobook), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        if (isAudiobookVisible) R.string.settings_show_audiobook_on
                        else R.string.settings_show_audiobook_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isAudiobookVisible,
                onCheckedChange = { onToggleAudiobookVisibility() }
            )
        }
    }
}

@Composable
private fun StatsSection(songs: List<Song>) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_stats))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = MuseShapeTokens.Card
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
private fun MusicUsageSection(stats: MusicUsageStats) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_music_usage))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = MuseShapeTokens.Card
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(formatUsageDuration(stats.totalListenedMs), stringResource(R.string.settings_music_total_listened))
                StatItem(formatUsageDuration(stats.todayListenedMs), stringResource(R.string.settings_music_today_listened))
            }
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${stats.playCount}", stringResource(R.string.settings_music_play_count))
                StatItem("${stats.listenedSongCount}", stringResource(R.string.settings_music_listened_songs))
            }
        }
    }
}

private fun formatUsageDuration(durationMs: Long): String {
    val minutes = durationMs / 60_000L
    val hours = minutes / 60L
    return if (hours > 0L) {
        "${hours}小时${minutes % 60L}分钟"
    } else {
        "${minutes}分钟"
    }
}

@Composable
private fun ReadAlongUsageSection(stats: ReadAlongUsageStats) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_readalong_usage))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = MuseShapeTokens.Card
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(formatUsageDuration(stats.totalReadMs), stringResource(R.string.settings_readalong_total_read))
                StatItem(formatUsageDuration(stats.todayReadMs), stringResource(R.string.settings_readalong_today_read))
            }
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(formatUsageDuration(stats.weekReadMs), stringResource(R.string.settings_readalong_week_read))
                StatItem(formatUsageDuration(stats.totalListenedMs), stringResource(R.string.settings_readalong_total_listened))
            }
        }
    }
}

@Composable
private fun ScanSection(
    isScanning: Boolean,
    scanProgress: Int,
    scanStats: ScanStats?,
    onScanAll: () -> Unit
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
    if (isScanning) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            shape = MuseShapeTokens.Card
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
            shape = MuseShapeTokens.Card
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
private fun FormatsSection() {
    SectionHeader(stringResource(R.string.settings_formats))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        shape = MuseShapeTokens.Card
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
private fun AudioHealthSection(
    progress: AudioHealthProgress?,
    onCheck: () -> Unit,
    onDismissResult: () -> Unit
) {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
    SectionHeader(stringResource(R.string.settings_audio_health_title))
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = MuseShapeTokens.Card
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
                        shape = MuseShapeTokens.Pill
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
                        progress = { progress.current.toFloat() / progress.total.coerceAtLeast(1) },
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
