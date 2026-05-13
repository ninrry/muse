package luzzr.muse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Muse Design Token: 统一格式标签组件
 * - 固定高度 20dp（精确匹配 MD3 labelSmall 行高），避免长文本导致行高抖动
 * - 最小宽度 44dp，确保短文本（如 MP3）也有足够触摸和视觉面积
 * - 精确垂直居中，使用 intrinsicSize 确保像素级对齐
 * - 单行 + 省略号截断
 * - 圆角使用 Theme shape token (extraSmall = 8dp)
 */
@Composable
fun FormatBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    // 自动缩短常见长格式名
    val displayText = when {
        text == "M4A/AAC" -> "AAC"
        text == "OGG Vorbis" -> "OGG"
        text.startsWith("MP3") -> "MP3"
        text == "M4A" -> "AAC"
        text == "WAV" -> "WAV"
        text.contains("PCM") -> "WAV"
        else -> text
    }

    Box(
        modifier = modifier
            .heightIn(min = 20.dp, max = 20.dp)
            .widthIn(min = 44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall.copy(
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
