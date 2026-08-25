package luzzr.muse.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.healthcheck.AudioHealthProgress
import luzzr.muse.domain.model.MusicUsageStats
import luzzr.muse.domain.model.ReadAlongUsageStats
import luzzr.muse.domain.model.ScanStats
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.preferences.ThemeMode
import luzzr.muse.feature.settings.R
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.screens.settings.components.FormatTagChip
import luzzr.muse.ui.screens.settings.components.SectionHeader
import luzzr.muse.ui.screens.settings.components.SettingItem
import luzzr.muse.ui.screens.settings.components.SettingsGroupCard
import luzzr.muse.ui.screens.settings.components.StatBadge
import luzzr.muse.ui.screens.settings.components.StatItem
import luzzr.muse.ui.screens.settings.components.ThemeSelectionDialog
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseIcons
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
    themeMode: ThemeMode,
    isAudiobookVisible: Boolean = true,
    isDarkThemeSupported: Boolean,
    permissionSnapshot: PermissionSnapshot,
    innerPadding: PaddingValues = PaddingValues(),
    onRequestAudioPermission: () -> Unit = {},
    onRequestFullFileAccess: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onRefreshPermissions: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onSetThemeMode: (ThemeMode) -> Unit = {},
    onToggleAudiobookVisibility: () -> Unit = {},
    onScanAll: () -> Unit = {},
    audioHealthProgress: AudioHealthProgress? = null,
    onCheckAudioHealth: () -> Unit = {},
    onDismissAudioHealthResult: () -> Unit = {}
) {
    val windowSize = currentWindowSize()
    var showThemeDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = themeMode,
            onSelectMode = { onSetThemeMode(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when (windowSize) {
            WindowSize.Medium, WindowSize.Expanded -> {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 1080.dp)
                            .align(Alignment.TopCenter)
                            .padding(padding)
                    ) {
                        SettingsTopHeader()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.5f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                PreferencesSection(
                                    themeMode = themeMode,
                                    isAudiobookVisible = isAudiobookVisible,
                                    onOpenThemeDialog = { showThemeDialog = true },
                                    onToggleAudiobookVisibility = onToggleAudiobookVisibility
                                )
                                Spacer(Modifier.height(AppSpacing.sm))
                                LibrarySection(
                                    isScanning = isScanning,
                                    scanProgress = scanProgress,
                                    scanStats = scanStats,
                                    onScanAll = onScanAll,
                                    snapshot = permissionSnapshot,
                                    onRequestAudioPermission = onRequestAudioPermission,
                                    onRequestFullFileAccess = onRequestFullFileAccess,
                                    onRequestNotificationPermission = onRequestNotificationPermission,
                                    onRefreshPermissions = onRefreshPermissions
                                )
                                Spacer(Modifier.height(160.dp))
                            }
                            Column(
                                modifier = Modifier
                                    .weight(0.5f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                DashboardSection(
                                    songs = songs,
                                    musicStats = musicStats,
                                    readAlongStats = readAlongStats,
                                    audioHealthProgress = audioHealthProgress,
                                    onCheckAudioHealth = onCheckAudioHealth,
                                    onDismissAudioHealthResult = onDismissAudioHealthResult
                                )
                                Spacer(Modifier.height(AppSpacing.sm))
                                AboutSection()
                                Spacer(Modifier.height(160.dp))
                            }
                        }
                    }
                }
            }
            WindowSize.Compact -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    SettingsTopHeader()
                    PreferencesSection(
                        themeMode = themeMode,
                        isAudiobookVisible = isAudiobookVisible,
                        onOpenThemeDialog = { showThemeDialog = true },
                        onToggleAudiobookVisibility = onToggleAudiobookVisibility
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    LibrarySection(
                        isScanning = isScanning,
                        scanProgress = scanProgress,
                        scanStats = scanStats,
                        onScanAll = onScanAll,
                        snapshot = permissionSnapshot,
                        onRequestAudioPermission = onRequestAudioPermission,
                        onRequestFullFileAccess = onRequestFullFileAccess,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onRefreshPermissions = onRefreshPermissions
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    DashboardSection(
                        songs = songs,
                        musicStats = musicStats,
                        readAlongStats = readAlongStats,
                        audioHealthProgress = audioHealthProgress,
                        onCheckAudioHealth = onCheckAudioHealth,
                        onDismissAudioHealthResult = onDismissAudioHealthResult
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    AboutSection()
                    Spacer(Modifier.height(160.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsTopHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// -------------------------------------------------------------
// Group 1: 偏好与外观
// -------------------------------------------------------------
@Composable
private fun PreferencesSection(
    themeMode: ThemeMode,
    isAudiobookVisible: Boolean,
    onOpenThemeDialog: () -> Unit,
    onToggleAudiobookVisibility: () -> Unit
) {
    SectionHeader(
        title = stringResource(R.string.settings_group_preferences),
        icon = MuseIcons.Tune
    )
    val themeSubtitle = when (themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.settings_dark_theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.settings_dark_theme_off_mode)
        ThemeMode.DARK -> stringResource(R.string.settings_dark_theme_on_mode)
    }

    SettingItem(
        icon = when (themeMode) {
            ThemeMode.DARK -> MuseIcons.Moon
            ThemeMode.LIGHT -> MuseIcons.Sun
            ThemeMode.SYSTEM -> MuseIcons.Settings
        },
        title = stringResource(R.string.settings_appearance),
        subtitle = themeSubtitle,
        onClick = onOpenThemeDialog
    )

    Spacer(Modifier.height(AppSpacing.xxs))

    val interaction = remember { MutableInteractionSource() }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = MuseShapeTokens.Card
            )
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggleAudiobookVisibility
            ),
        shape = MuseShapeTokens.Card,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MuseDimens.TouchTarget)
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = MuseShapeTokens.Item
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    MuseIcons.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_show_audiobook),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    stringResource(
                        if (isAudiobookVisible) R.string.settings_show_audiobook_on else R.string.settings_show_audiobook_off
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

// -------------------------------------------------------------
// Group 2: 媒体库与扫描
// -------------------------------------------------------------
@Composable
private fun LibrarySection(
    isScanning: Boolean,
    scanProgress: Int,
    scanStats: ScanStats?,
    onScanAll: () -> Unit,
    snapshot: PermissionSnapshot,
    onRequestAudioPermission: () -> Unit,
    onRequestFullFileAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    SectionHeader(
        title = stringResource(R.string.settings_group_library),
        icon = MuseIcons.Folder
    )

    SettingItem(
        icon = MuseIcons.Radar,
        title = stringResource(R.string.settings_scan_all),
        subtitle = stringResource(R.string.settings_scan_all_subtitle),
        onClick = onScanAll,
        enabled = !isScanning,
        trailingContent = {
            if (isScanning) {
                Text(
                    "$scanProgress%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    MuseIcons.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    )

    if (isScanning) {
        SettingsGroupCard {
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

    scanStats?.let { stats ->
        SettingsGroupCard {
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

    Spacer(Modifier.height(AppSpacing.xxs))

    SettingsGroupCard {
        Text(
            stringResource(R.string.settings_permissions_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.sm))
        PermissionItem(
            icon = MuseIcons.Library,
            title = stringResource(R.string.settings_audio_permission),
            subtitle = stringResource(R.string.settings_audio_permission_description),
            status = snapshot.audio,
            onRequest = onRequestAudioPermission
        )
        HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
        PermissionItem(
            icon = MuseIcons.CheckCircle,
            title = stringResource(R.string.settings_file_permission),
            subtitle = stringResource(R.string.settings_file_permission_description),
            status = snapshot.fullFileAccess,
            onRequest = onRequestFullFileAccess
        )
        HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
        PermissionItem(
            icon = MuseIcons.Tune,
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
            Icon(MuseIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
            Spacer(Modifier.width(AppSpacing.xxs))
            Text(stringResource(R.string.settings_permission_recheck))
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
            MuseIcons.CheckCircle,
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

// -------------------------------------------------------------
// Group 3: 数据仪表盘与体检
// -------------------------------------------------------------
@Composable
private fun DashboardSection(
    songs: List<Song>,
    musicStats: MusicUsageStats,
    readAlongStats: ReadAlongUsageStats,
    audioHealthProgress: AudioHealthProgress?,
    onCheckAudioHealth: () -> Unit,
    onDismissAudioHealthResult: () -> Unit
) {
    SectionHeader(
        title = stringResource(R.string.settings_group_dashboard),
        icon = MuseIcons.GraphicEq
    )

    // 1. 曲库总览徽章卡
    SettingsGroupCard {
        Text(
            stringResource(R.string.settings_stats),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            StatBadge("${songs.size}", stringResource(R.string.settings_songs), Modifier.weight(1f))
            StatBadge("${songs.distinctBy { it.album }.size}", stringResource(R.string.settings_albums), Modifier.weight(1f))
            StatBadge("${songs.distinctBy { it.artist }.size}", stringResource(R.string.settings_artists), Modifier.weight(1f))
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            val totalDuration = songs.sumOf { it.duration }
            val hours = totalDuration / 3_600_000
            val minutes = (totalDuration % 3_600_000) / 60_000
            val durationText = if (hours > 0) {
                stringResource(R.string.settings_hours_minutes, hours.toInt(), minutes.toInt())
            } else {
                stringResource(R.string.settings_minutes, minutes.toInt())
            }
            StatBadge(durationText, stringResource(R.string.settings_duration), Modifier.weight(1f))
            StatBadge(formatStorage(songs.sumOf { it.size }), stringResource(R.string.settings_storage), Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(AppSpacing.xxs))

    // 2. 收听与阅读时长 Tab 卡片
    var selectedTab by remember { mutableIntStateOf(0) }
    SettingsGroupCard {
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.settings_tab_music)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.settings_tab_readalong)) }
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        if (selectedTab == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(formatUsageDuration(musicStats.totalListenedMs), stringResource(R.string.settings_music_total_listened))
                StatItem(formatUsageDuration(musicStats.todayListenedMs), stringResource(R.string.settings_music_today_listened))
            }
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${musicStats.playCount}", stringResource(R.string.settings_music_play_count))
                StatItem("${musicStats.listenedSongCount}", stringResource(R.string.settings_music_listened_songs))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(formatUsageDuration(readAlongStats.totalReadMs), stringResource(R.string.settings_readalong_total_read))
                StatItem(formatUsageDuration(readAlongStats.todayReadMs), stringResource(R.string.settings_readalong_today_read))
            }
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(formatUsageDuration(readAlongStats.weekReadMs), stringResource(R.string.settings_readalong_week_read))
                StatItem(formatUsageDuration(readAlongStats.totalListenedMs), stringResource(R.string.settings_readalong_total_listened))
            }
        }
    }

    Spacer(Modifier.height(AppSpacing.xxs))

    // 3. 音频健康体检卡
    AudioHealthSection(audioHealthProgress, onCheckAudioHealth, onDismissAudioHealthResult)
}

@Composable
private fun AudioHealthSection(progress: AudioHealthProgress?, onCheck: () -> Unit, onDismissResult: () -> Unit) {
    SettingsGroupCard {
        Text(
            stringResource(R.string.settings_audio_health_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(4.dp))
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = MuseShapeTokens.Pill
                ) {
                    Icon(MuseIcons.Radar, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
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
                    ),
                    style = MaterialTheme.typography.bodyMedium
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
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
                FilledTonalButton(
                    onClick = onDismissResult,
                    modifier = Modifier.align(Alignment.End),
                    shape = MuseShapeTokens.Pill
                ) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Group 4: 关于与支持
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutSection() {
    SectionHeader(
        title = stringResource(R.string.settings_group_about),
        icon = MuseIcons.CheckCircle
    )

    // 支持音频格式 Chip 流
    SettingsGroupCard {
        Text(
            stringResource(R.string.settings_formats),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(AppSpacing.xs))
        val formats = listOf("MP3", "FLAC", "M4A", "AAC", "Opus", "WAV", "ALAC", "OGG", "APE", "AIFF", "DSD", "WMA")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            formats.forEach { format ->
                FormatTagChip(label = format)
            }
        }
    }

    Spacer(Modifier.height(AppSpacing.xxs))

    // 应用信息卡
    SettingsGroupCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    MuseIcons.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_app_name),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    stringResource(R.string.settings_app_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            stringResource(R.string.settings_app_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatUsageDuration(durationMs: Long): String {
    val minutes = durationMs / 60_000L
    val hours = minutes / 60L
    return when {
        hours > 0L -> "${hours}小时${minutes % 60L}分钟"
        durationMs > 0L && minutes == 0L -> "不足1分钟"
        else -> "${minutes}分钟"
    }
}

private fun formatStorage(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1_073_741_824 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1_024.0)
    else -> "$bytes B"
}

