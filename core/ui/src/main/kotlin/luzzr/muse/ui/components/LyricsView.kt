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
import luzzr.muse.domain.lyrics.LrcParser
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens

private const val AUTO_FOLLOW_RESUME_DELAY_MS = 1600L

/**
 * 同步歌词列表：
 * - 帧轮询行号：positionMs 20Hz 推入，UI 端 withFrameNanos 每帧二分查找
 *   → 行号变化延迟从 50-80ms 降到 ~16ms（仅 1 帧）
 * - 切行时居中平滑滚动
 * - progress 在 ActiveLyricText 内部由 positionMs + lineStartMs/lineEndMs
 *   实时计算，与显示刷新率（60-120Hz）一致
 */
@Composable
fun LyricsView(
    lyrics: List<LrcLine>,
    positionProvider: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lyricsOffsetMs: Long = 0L,
    isCalibrationMode: Boolean = false,
    onCalibrate: (Long) -> Unit = {},
    reduceMotion: Boolean = false,
    isPlaying: Boolean = true,
    isPanelVisible: Boolean = true
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val bg = MaterialTheme.colorScheme.background

    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var autoFollow by rememberSaveable { mutableStateOf(true) }
    val lastUserInteractionAt = rememberSaveable { mutableLongStateOf(0L) }

    var currentIndex by remember { mutableIntStateOf(-1) }
    // 离线上次 position 基准，用于在两次 20Hz tick 之间用 vsync 间隔 + 速度预测
    // 提升行号变化的感知帧率
    var lastSamplePos by remember { mutableLongStateOf(0L) }
    var lastSampleWall by remember { mutableLongStateOf(0L) }

    // Per-frame recompute of currentIndex from positionProvider + offset.
    // Bypasses the 50ms upstream polling delay of positionMs StateFlow for
    // line-change detection. Trade-off: one binary search per frame
    // (O(log n) on the lyrics list, typically a few dozen entries).
    LaunchedEffect(isPlaying, isPanelVisible, lyrics) {
        if (lyrics.isEmpty()) {
            currentIndex = -1
            return@LaunchedEffect
        }
        if (isPlaying) {
            while (true) {
                withFrameNanos {
                    val rawPos = positionProvider().coerceAtLeast(0L)
                    val nowWall = SystemClock.elapsedRealtime()
                    val speed = if (lastSampleWall == 0L) {
                        1f
                    } else {
                        val dt = (nowWall - lastSampleWall).coerceAtLeast(1L)
                        ((rawPos - lastSamplePos).toFloat() / dt).coerceIn(0f, 2f)
                    }
                    // 预测：positionProvider 落后 ~16ms 时用 1.0x 平推一帧
                    val predicted = (rawPos + 16L * speed).toLong()
                    lastSamplePos = rawPos
                    lastSampleWall = nowWall
                    val adjusted = (predicted + lyricsOffsetMs).coerceAtLeast(0L)
                    val idx = LrcParser.getLineIndex(lyrics, adjusted)
                    if (idx != currentIndex) currentIndex = idx
                }
            }
        } else {
            while (true) {
                kotlinx.coroutines.delay(200)
                val rawPos = positionProvider().coerceAtLeast(0L)
                val adjusted = (rawPos + lyricsOffsetMs).coerceAtLeast(0L)
                val idx = LrcParser.getLineIndex(lyrics, adjusted)
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

    LaunchedEffect(currentIndex, autoFollow, viewportHeightPx) {
        if (!autoFollow || currentIndex < 0 || currentIndex >= lyrics.size || viewportHeightPx <= 0) return@LaunchedEffect
        val lazyIndex = currentIndex + 1
        val layoutInfo = listState.layoutInfo
        val vh = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
            .takeIf { it > 0 } ?: viewportHeightPx
        if (vh <= 0) return@LaunchedEffect
        val visible = layoutInfo.visibleItemsInfo.find { it.index == lazyIndex }
        val itemH = visible?.size ?: with(density) { 56.dp.roundToPx() }
        val offset = -(vh / 2 - itemH / 2)
        try {
            listState.animateScrollToItem(
                index = lazyIndex.coerceIn(0, lyrics.size),
                scrollOffset = offset
            )
        } catch (_: Exception) { }
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
                    positionProvider = positionProvider,
                    lyricsOffsetMs = lyricsOffsetMs,
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
