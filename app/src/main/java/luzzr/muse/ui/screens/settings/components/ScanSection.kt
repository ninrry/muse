package luzzr.muse.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import luzzr.muse.data.repository.ScanStats
import luzzr.muse.ui.screens.settings.SettingsViewModel
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
@Suppress("UNUSED_PARAMETER")
fun ScanSection(
    viewModel: SettingsViewModel,
    isScanning: Boolean,
    scanProgress: Int,
    scanStats: ScanStats?,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        onClick = onPickFolder,
        enabled = !isScanning
    )

    if (isScanning) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
        ) {
            Column(Modifier.padding(AppSpacing.md)) {
                Text("正在扫描", style = MaterialTheme.typography.titleSmall)
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
            shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
        ) {
            Column(Modifier.padding(AppSpacing.md)) {
                Text("扫描完成", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(AppSpacing.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("${stats.totalSongs}", "歌曲")
                    StatItem("${stats.totalAlbums}", "专辑")
                    StatItem("${stats.totalArtists}", "艺术家")
                }
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    "耗时 ${stats.duration / 1000}秒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md))
}
