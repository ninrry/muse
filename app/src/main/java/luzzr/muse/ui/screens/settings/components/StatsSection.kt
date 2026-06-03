package luzzr.muse.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import luzzr.muse.data.model.Song
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
@Suppress("UNUSED_PARAMETER")
fun StatsSection(songs: List<Song>, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${songs.size}", "歌曲")
                StatItem("${songs.distinctBy { it.album }.size}", "专辑")
            }
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
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
            Spacer(Modifier.height(AppSpacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(AppSpacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val totalSize = songs.sumOf { it.size }
                StatItem(formatStorage(totalSize), "存储占用")
            }
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
