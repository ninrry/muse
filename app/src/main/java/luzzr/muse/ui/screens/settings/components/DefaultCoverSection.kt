package luzzr.muse.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import luzzr.muse.ui.screens.settings.SettingsViewModel
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
@Suppress("UNUSED_PARAMETER")
fun DefaultCoverSection(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val genState by viewModel.coverGenState.collectAsStateWithLifecycle()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
    ) {
        Column(Modifier.padding(AppSpacing.md)) {
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
                        "用渐变色+歌名替换所有歌曲的封面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!genState.isRunning) {
                    FilledTonalButton(
                        onClick = { viewModel.generateAllDefaultCovers() },
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(MuseDimens.ButtonCornerRadius),
                        enabled = !genState.isRunning
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(AppSpacing.md))
                        Spacer(Modifier.width(AppSpacing.xxs))
                        Text("开始", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (genState.isRunning) {
                Spacer(Modifier.height(AppSpacing.sm))
                LinearProgressIndicator(
                    progress = { genState.processed.toFloat() / genState.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(MuseDimens.ProgressBarHeight)
                )
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    "正在处理 ${genState.processed}/${genState.total} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!genState.isRunning && genState.total > 0) {
                Spacer(Modifier.height(AppSpacing.xs))
                val success = genState.total - genState.errors
                val color = if (genState.errors == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Text(
                    if (genState.errors == 0) {
                        "已为 $success 首歌曲生成默认封面"
                    } else {
                        "完成: $success 首成功，${genState.errors} 首失败"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}
