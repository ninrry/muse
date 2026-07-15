package luzzr.muse.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens
import kotlin.math.abs

private const val AUTO_FOLLOW_RESUME_DELAY_MS = 1600L

/**
 * 同步歌词列表：
 * - 帧轮询行号（StateFlow 非 Snapshot，不能用 snapshotFlow 订阅 .value）
 * - 切行时居中滚动
 * - progress 仅当前行跟帧
 */
@Composable
fun LyricsView(
    lyrics: List<LrcLine>,
    currentLineIndexProvider: () -> Int,
    lineProgressProvider: () -> Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isCalibrationMode: Boolean = false,
    onCalibrate: (Long) -> Unit = {},
    reduceMotion: Boolean = false,
    isPlaying: Boolean = false,
    isPanelVisible: Boolean = true
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val bg = MaterialTheme.colorScheme.background

    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var autoFollow by rememberSaveable { mutableStateOf(true) }
    val lastUserInteractionAt = rememberSaveable { mutableLongStateOf(0L) }

    var currentIndex by remember { mutableIntStateOf(currentLineIndexProvider()) }

    LaunchedEffect(isPlaying, isPanelVisible) {
        while (true) {
            if (isPlaying) {
                var frameCounter = 0
                withFrameNanos {
                    frameCounter++
                    if (frameCounter % 2 == 0) {
                        val idx = currentLineIndexProvider()
                        if (idx != currentIndex) currentIndex = idx
                    }
                }
            } else {
                kotlinx.coroutines.delay(200)
                val idx = currentLineIndexProvider()
                if (idx != currentIndex) currentIndex = idx
            }
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            autoFollow = false
            lastUserInteractionAt.longValue = SystemClock.elapsedRealtime()
        } else if (!autoFollow) {
            val snapshot = lastUserInteractionAt.longValue
            kotlinx.coroutines.delay(AUTO_FOLLOW_RESUME_DELAY_MS)
            if (lastUserInteractionAt.longValue == snapshot) {
                autoFollow = true
            }
        }
    }

    // 切行或恢复跟随时滚动居中
    LaunchedEffect(currentIndex, autoFollow, viewportHeightPx, lyrics.size) {
        if (!autoFollow) return@LaunchedEffect
        if (viewportHeightPx <= 0) return@LaunchedEffect
        if (lyrics.isEmpty() || currentIndex !in lyrics.indices) return@LaunchedEffect

        // 等一帧让 LazyColumn 量完布局
        withFrameNanos { }

        val targetLazyIndex = currentIndex + 1 // +1 top spacer
        val layoutInfo = listState.layoutInfo
        val viewportHeight = (
            layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            ).takeIf { it > 0 } ?: viewportHeightPx
        val currentItem = layoutInfo.visibleItemsInfo.find { it.index == targetLazyIndex }
        val itemHeight = currentItem?.size ?: with(density) { 56.dp.roundToPx() }
        // scrollOffset：item 顶部相对 viewport 顶部的偏移；负值把 item 往下推到中线
        val scrollOffset = -(viewportHeight / 2 - itemHeight / 2)

        try {
            listState.animateScrollToItem(
                index = targetLazyIndex.coerceIn(0, lyrics.size), // spacer + items; max is lyrics.size (bottom spacer)
                scrollOffset = scrollOffset
            )
        } catch (_: Exception) {
            try {
                listState.scrollToItem(
                    index = targetLazyIndex.coerceAtLeast(0),
                    scrollOffset = scrollOffset
                )
            } catch (_: Exception) {
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxHeight = maxHeight
        LaunchedEffect(boxHeight) {
            viewportHeightPx = with(density) { boxHeight.toPx().toInt() }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item(key = "top_sp", contentType = "sp") {
                Spacer(Modifier.height(boxHeight * 0.36f))
            }

            itemsIndexed(
                items = lyrics,
                key = { index, line -> "${line.timestamp}_$index" },
                contentType = { _, _ -> "ly" }
            ) { index, line ->
                val distance = if (currentIndex < 0) 3 else index - currentIndex
                val isCurrent = index == currentIndex

                val onClick = remember(line.timestamp, isCalibrationMode, onSeek, onCalibrate) {
                    {
                        if (isCalibrationMode) onCalibrate(line.timestamp)
                        else onSeek(line.timestamp)
                    }
                }

                LyricsLineItem(
                    text = line.text,
                    isCurrent = isCurrent,
                    isPast = index < currentIndex,
                    lineProgressProvider = lineProgressProvider,
                    onClick = onClick,
                    isCalibrationMode = isCalibrationMode,
                    distanceFromCurrent = distance,
                    wordSegments = line.words,
                    lineStartMs = line.timestamp,
                    lineEndMs = lyrics.getOrNull(index + 1)?.timestamp ?: (line.timestamp + 4000L),
                    reduceMotion = reduceMotion
                )
            }

            item(key = "bot_sp", contentType = "sp") {
                Spacer(Modifier.height(boxHeight * 0.40f))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f))))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg)))
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = !autoFollow,
            enter = fadeIn(tween(MotionDuration.medium1)),
            exit = fadeOut(tween(MotionDuration.short)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.md)
        ) {
            Surface(
                shape = MuseShapeTokens.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 1.dp
            ) {
                TextButton(
                    onClick = {
                        autoFollow = true
                        lastUserInteractionAt.longValue = SystemClock.elapsedRealtime()
                    },
                    contentPadding = PaddingValues(
                        horizontal = AppSpacing.md,
                        vertical = AppSpacing.xxs
                    )
                ) {
                    Text(
                        text = stringResource(R.string.ui_lyrics_follow_current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun LyricsEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(AppSpacing.xlg)
        )
    }
}

@Composable
fun LyricsLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.padding(AppSpacing.xlg),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        )
    }
}
