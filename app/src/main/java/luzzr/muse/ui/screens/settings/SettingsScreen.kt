package luzzr.muse.ui.screens.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import luzzr.muse.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    innerPadding: PaddingValues = PaddingValues()
) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val scanStats by viewModel.scanStats.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()

    // Folder picker — use system SAF dialog instead of manual path input
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val path = safTreeUriToPath(uri)
            if (path != null) {
                viewModel.scanFolder(path)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = innerPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
        ) {
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Appearance section per MD3 Settings spec (prominently placed)
            SectionHeader("外观")

            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            if (viewModel.isDarkThemeSupported) {
                SettingItem(
                    icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    title = "深色主题",
                    subtitle = if (isDarkTheme) "当前为深色模式" else "当前为浅色模式",
                    onClick = { viewModel.toggleTheme() }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Statistics section per spec (prominently placed)
            SectionHeader("统计")

            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("${songs.size}", "歌曲")
                        StatItem("${songs.distinctBy { it.album }.size}", "专辑")
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("${songs.distinctBy { it.artist }.size}", "艺术家")
                        val totalDuration = songs.sumOf { it.duration }
                        val hours = totalDuration / 3_600_000
                        val minutes = (totalDuration % 3_600_000) / 60_000
                        StatItem(
                            if (hours > 0) "${hours}h${minutes}m" else "${minutes}分钟",
                            "总时长"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        val totalSize = songs.sumOf { it.size }
                        StatItem(formatStorage(totalSize), "存储占用")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Scan management section per spec
            SectionHeader("扫描管理")

            SettingItem(
                icon = Icons.Default.Radar,
                title = "全盘扫描",
                subtitle = "扫描设备中所有音乐文件",
                onClick = { viewModel.scanAll() },
                enabled = !isScanning
            )

            SettingItem(
                icon = Icons.Default.FolderOpen,
                title = "扫描指定文件夹",
                subtitle = "选择设备中的文件夹进行扫描",
                onClick = { folderPicker.launch(null) },
                enabled = !isScanning
            )

            if (isScanning) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("正在扫描…", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$scanProgress%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            scanStats?.let { stats ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("扫描完成", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatItem("${stats.totalSongs}", "歌曲")
                            StatItem("${stats.totalAlbums}", "专辑")
                            StatItem("${stats.totalArtists}", "艺术家")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "耗时 ${stats.duration / 1000}秒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Default cover generation
            SectionHeader("封面管理")

            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                val genState by viewModel.coverGenState.collectAsStateWithLifecycle()
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "全部生成默认封面",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "用渐变色+歌名替换所有歌曲的封面图",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!genState.isRunning) {
                            FilledTonalButton(
                                onClick = { viewModel.generateAllDefaultCovers() },
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(18.dp),
                                enabled = !genState.isRunning
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("开始", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (genState.isRunning) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { genState.processed.toFloat() / genState.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "正在处理 ${genState.processed}/${genState.total} …",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!genState.isRunning && genState.total > 0) {
                        Spacer(Modifier.height(8.dp))
                        val success = genState.total - genState.errors
                        val color = if (genState.errors == 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                        Text(
                            if (genState.errors == 0)
                                "已为 $success 首歌曲生成默认封面 ✓"
                            else
                                "完成: $success 首成功, ${genState.errors} 首失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    }
                }
            }

            // Supported formats per spec
            SectionHeader("支持格式")

            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "MP3, AAC, M4A, Opus, FLAC, WAV, ALAC, OGG",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // About section per spec
            SectionHeader("关于")

            SettingItem(
                icon = Icons.Default.Info,
                title = "Muse",
                subtitle = "版本 ${BuildConfig.VERSION_NAME}",
                onClick = {}
            )

            SettingItem(
                icon = Icons.Default.PhoneAndroid,
                title = "设备",
                subtitle = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                onClick = {}
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Convert SAF tree URI to a file system path.
 * URI format: content://com.android.externalstorage.documents/tree/primary%3AMusic
 * Returns:    /storage/emulated/0/Music
 */
private fun safTreeUriToPath(uri: android.net.Uri): String? {
    return try {
        val docId = java.net.URLDecoder.decode(uri.lastPathSegment ?: return null, "UTF-8")
        // docId: "primary:Music" or "XXXX-XXXX:Music"
        val parts = docId.split(":")
        val storage = if (parts[0] == "primary") "emulated/0" else parts[0]
        val folderPath = if (parts.size > 1) "/${parts[1]}" else ""
        "/storage/$storage$folderPath"
    } catch (_: Exception) { null }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatStorage(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
