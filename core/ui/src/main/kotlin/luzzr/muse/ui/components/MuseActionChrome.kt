package luzzr.muse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * 底部多选操作坞：顶行次要操作 + 底行等宽主操作瓦片。
 * 用于曲库多选等场景，保证「抓歌词 / 加入歌单」同型展示。
 */
data class MusePrimaryAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

@Composable
fun MuseSelectionDock(
    selectedLabel: String,
    onCancel: () -> Unit,
    cancelLabel: String,
    onSelectAll: () -> Unit,
    selectAllLabel: String,
    selectAllEnabled: Boolean,
    primaryActions: List<MusePrimaryAction>,
    modifier: Modifier = Modifier,
    progress: Pair<Int, Int>? = null,
    progressLabel: String? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MuseShapeTokens.Sheet,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppSpacing.md,
                    end = AppSpacing.md,
                    top = AppSpacing.sm,
                    bottom = AppSpacing.md
                )
        ) {
            // 顶行：取消 | 计数 | 全选
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onCancel,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = AppSpacing.sm,
                        vertical = AppSpacing.xxs
                    )
                ) {
                    Text(
                        cancelLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onSelectAll,
                    enabled = selectAllEnabled,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = AppSpacing.sm,
                        vertical = AppSpacing.xxs
                    )
                ) {
                    Text(
                        selectAllLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (progress != null) {
                val (done, total) = progress
                val fraction = if (total > 0) done.toFloat() / total else 0f
                Spacer(Modifier.height(AppSpacing.xs))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                if (!progressLabel.isNullOrBlank()) {
                    Spacer(Modifier.height(AppSpacing.xxs))
                    Text(
                        progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.sm))

            // 底行：等宽主操作瓦片（同型）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                primaryActions.forEach { action ->
                    MusePrimaryActionTile(
                        label = action.label,
                        icon = action.icon,
                        onClick = action.onClick,
                        enabled = action.enabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 等宽主操作瓦片：图标 + 文案，用于双/多操作并列。
 */
@Composable
fun MusePrimaryActionTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(MuseDimens.ButtonHeightMedium),
        shape = MuseShapeTokens.Card,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = AppSpacing.sm,
            vertical = AppSpacing.xs
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(MuseDimens.IconSizeNormal)
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 通用等宽双操作行（歌单详情播放/随机、定时关闭等）。
 */
@Composable
fun MuseDualActionRow(primary: MusePrimaryAction, secondary: MusePrimaryAction, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        MusePrimaryActionTile(
            label = primary.label,
            icon = primary.icon,
            onClick = primary.onClick,
            enabled = primary.enabled,
            modifier = Modifier.weight(1f)
        )
        MusePrimaryActionTile(
            label = secondary.label,
            icon = secondary.icon,
            onClick = secondary.onClick,
            enabled = secondary.enabled,
            modifier = Modifier.weight(1f)
        )
    }
}
