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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.lyrics.LyricsSyncEngine
import luzzr.muse.domain.lyrics.LyricsTimeline
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens
import kotlin.math.abs

private enum class LyricsFollowState { Following, UserScrolling, WaitingToResume, Seeking }
private const val LYRICS_FOCUS_FRACTION = 0.42f

/**
 * 同步歌词列表：
 * - 行号与填色同一时钟（无提前预测），避免「上色未完就切句」
 * - 切行滚动：小步平滑、大步直达；用户拖动后暂停跟随
 */
@Composable
fun LyricsView(
    lyrics: List<LrcLine>,
    positionProvider: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lyricsOffsetMs: Long = 0L,
    durationMs: Long = 0L,
    isCalibrationMode: Boolean = false,
    onCalibrate: (Long) -> Unit = {},
    reduceMotion: Boolean = false,
    isPlaying: Boolean = true,
    isPanelVisible: Boolean = true
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val bg = MaterialTheme.colorScheme.background.copy(alpha = 0.82f)

    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var followState by remember { mutableStateOf(LyricsFollowState.Following) }
    var showReturnButton by remember { mutableStateOf(false) }
    val timeline = remember(lyrics) { LyricsTimeline(lyrics) }
    val syncEngine = remember(timeline) { LyricsSyncEngine(timeline) }

    var currentIndex by remember { mutableIntStateOf(-1) }
    var previousIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(timeline) {
        currentIndex = -1
        previousIndex = -1
        followState = LyricsFollowState.Following
        showReturnButton = false
        try {
            listState.scrollToItem(0)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && previousIndex >= 0 && abs(currentIndex - previousIndex) > 3) {
            if (followState != LyricsFollowState.UserScrolling &&
                followState != LyricsFollowState.WaitingToResume
            ) {
                followState = LyricsFollowState.Seeking
            }
        }
        if (currentIndex >= 0) previousIndex = currentIndex
    }

    // 主列表与填色共用同一套投影，不额外 +16ms 抢跑
    LaunchedEffect(isPlaying, isPanelVisible, timeline, lyricsOffsetMs) {
        if (timeline.lines.isEmpty()) {
            currentIndex = -1
            return@LaunchedEffect
        }
        if (isPlaying && isPanelVisible) {
            while (true) {
                withFrameNanos {
                    val now = SystemClock.elapsedRealtime()
                    val idx = syncEngine.frameAt(
                        positionMs = positionProvider(),
                        durationMs = durationMs,
                        offsetMs = lyricsOffsetMs,
                        isPlaying = true,
                        wallClockMs = now
                    ).currentIndex
                    if (idx != currentIndex) currentIndex = idx
                }
            }
        } else {
            while (true) {
                kotlinx.coroutines.delay(if (isPlaying) 100 else 250)
                val idx = syncEngine.frameAt(
                    positionMs = positionProvider(),
                    durationMs = durationMs,
                    offsetMs = lyricsOffsetMs,
                    isPlaying = false,
                    wallClockMs = SystemClock.elapsedRealtime()
                ).currentIndex
                if (idx != currentIndex) currentIndex = idx
            }
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            followState = LyricsFollowState.UserScrolling
            showReturnButton = false
        } else if (followState == LyricsFollowState.UserScrolling) {
            followState = LyricsFollowState.WaitingToResume
            showReturnButton = true
        }
    }

    // 可取消的居中滚动：大跨度直达，小跨度动画
    LaunchedEffect(currentIndex, followState, viewportHeightPx, reduceMotion) {
        if (currentIndex < 0 || currentIndex >= timeline.lines.size || viewportHeightPx <= 0) {
            return@LaunchedEffect
        }
        if (followState == LyricsFollowState.UserScrolling || followState == LyricsFollowState.WaitingToResume) return@LaunchedEffect
        val lazyIndex = currentIndex + 1
        val layoutInfo = listState.layoutInfo
        val vh = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
            .takeIf { it > 0 } ?: viewportHeightPx
        if (vh <= 0) return@LaunchedEffect

        val visible = layoutInfo.visibleItemsInfo.find { it.index == lazyIndex }
        val itemH = visible?.size ?: with(density) { 56.dp.roundToPx() }
        val offset = lyricsFocusScrollOffset(vh, itemH)
        val target = lazyIndex.coerceIn(0, timeline.lines.size)
        val firstVisible = listState.firstVisibleItemIndex
        val jump = abs(target - firstVisible)

        try {
            if (reduceMotion || jump > 3 || followState == LyricsFollowState.Seeking) {
                listState.scrollToItem(target, scrollOffset = offset)
            } else {
                listState.animateScrollToItem(target, scrollOffset = offset)
            }
            if (followState == LyricsFollowState.Seeking) followState = LyricsFollowState.Following
        } catch (_: Exception) {
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
                Spacer(Modifier.height(boxHeight * 0.34f))
            }

            itemsIndexed(
                items = timeline.lines,
                key = { index, line -> "${line.timestamp}_$index" },
                contentType = { _, _ -> "ly" }
            ) { index, line ->
                val distance = if (currentIndex < 0) 3 else index - currentIndex
                val isCurrent = index == currentIndex
                val lineEnd = timeline.lineEndMs(index, durationMs)

                val onClick = {
                    if (isCalibrationMode) {
                        onCalibrate(line.timestamp)
                    } else {
                        onSeek(line.timestamp)
                        followState = LyricsFollowState.Following
                        showReturnButton = false
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
                    lineEndMs = lineEnd,
                    reduceMotion = reduceMotion,
                    isPlaying = isPlaying
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
            visible = showReturnButton && followState != LyricsFollowState.Following,
            enter = fadeIn(tween(if (reduceMotion) 0 else MotionDuration.medium1)),
            exit = fadeOut(tween(if (reduceMotion) 0 else MotionDuration.short)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.md)
        ) {
            Surface(
                shape = MuseShapeTokens.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = 0.dp
            ) {
                TextButton(
                    onClick = {
                        followState = LyricsFollowState.Following
                        showReturnButton = false
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

internal fun lyricsFocusScrollOffset(viewportHeightPx: Int, itemHeightPx: Int): Int =
    -((viewportHeightPx * LYRICS_FOCUS_FRACTION) - (itemHeightPx / 2f)).toInt()

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
