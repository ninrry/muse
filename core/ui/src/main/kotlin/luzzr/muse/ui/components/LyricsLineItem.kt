package luzzr.muse.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import luzzr.muse.ui.R
import luzzr.muse.ui.theme.AppSpacing
import kotlin.math.abs

/**
 * 高性能歌词行：
 * - 非当前行：静态样式 + 轻量 alpha/scale 动画（仅切行时）
 * - 当前行：帧级 progressive fill（withFrameNanos + draw 失效，不牵连列表）
 */
@Composable
fun LyricsLineItem(
    text: String,
    isCurrent: Boolean,
    isPast: Boolean,
    lineProgressProvider: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCalibrationMode: Boolean = false,
    distanceFromCurrent: Int = if (isCurrent) 0 else 1
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val depth = abs(distanceFromCurrent).coerceAtMost(4)

    val targetAlpha = when {
        isCurrent -> 1f
        depth == 1 -> 0.40f
        depth == 2 -> 0.26f
        else -> 0.14f
    }
    val targetScale = when {
        isCurrent -> 1f
        depth == 1 -> 0.94f
        depth == 2 -> 0.90f
        else -> 0.88f
    }

    // 仅在目标变化时插值；远行用短 tween，避免 spring 拖帧
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "a"
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "s"
    )

    val interactionSource = remember { MutableInteractionSource() }

    val style = if (isCurrent) {
        MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            lineHeight = 34.sp,
            textAlign = TextAlign.Center
        )
    } else {
        MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center
        )
    }

    val textColor = when {
        isCurrent -> primary
        isPast -> onSurface
        else -> onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = AppSpacing.lg, vertical = if (isCurrent) 12.dp else 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isCurrent) {
            ActiveLyricText(
                text = text,
                progressProvider = lineProgressProvider,
                baseColor = onSurface.copy(alpha = 0.20f),
                fillColor = primary,
                style = style
            )
        } else {
            Text(
                text = text,
                color = textColor,
                style = style,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isCalibrationMode && !isCurrent) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(R.string.ui_player_align_lyric_line),
                tint = primary.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
            )
        }
    }
}

/**
 * 当前行专用：每帧拉取 progress，仅触发 draw 层，不重组 LazyColumn 其他行。
 */
@Composable
private fun ActiveLyricText(
    text: String,
    progressProvider: () -> Float,
    baseColor: Color,
    fillColor: Color,
    style: TextStyle
) {
    var progress by remember { mutableFloatStateOf(progressProvider()) }

    // VSYNC 对齐 + 采样点间线性外推，在 8ms 采样下仍可达 120fps 视觉
    LaunchedEffect(Unit) {
        var sample = progressProvider().coerceIn(0f, 1f)
        var sampleMs = android.os.SystemClock.elapsedRealtime()
        var velocityPerMs = 0f
        while (true) {
            withFrameNanos {
                val now = android.os.SystemClock.elapsedRealtime()
                val s = progressProvider().coerceIn(0f, 1f)
                if (abs(s - sample) >= 0.0005f) {
                    val dt = (now - sampleMs).coerceAtLeast(1L)
                    velocityPerMs = (s - sample) / dt
                    // 回跳/切行时清零速度
                    if (s + 0.05f < sample) velocityPerMs = 0f
                    sample = s
                    sampleMs = now
                }
                val predicted = (sample + velocityPerMs * (now - sampleMs)).coerceIn(0f, 1f)
                if (abs(predicted - progress) >= 0.0008f) {
                    progress = predicted
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = baseColor,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = text,
            color = fillColor,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val p = progress.coerceIn(0f, 1f)
                    clipRect(left = 0f, top = 0f, right = size.width * p, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        )
    }
}
