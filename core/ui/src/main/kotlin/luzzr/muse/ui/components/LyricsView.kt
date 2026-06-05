package luzzr.muse.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.ui.theme.AppSpacing
import kotlinx.coroutines.delay

private val fadeHeight = 120.dp

/**
 * Premium synchronized lyrics view with deep animation polish:
 * - **Karaoke fill**: Current line text smoothly blends from primary→surface as playback
 *   progresses through the line, simulating a left-to-right reveal
 * - **Breathing pulse**: Accent bar glows with a subtle infinite pulse
 * - **Spring-animated auto-scroll** that centers the current line
 * - **Tap bounce**: Brief elastic scale-down on tap
 * - **Letter-spacing animation**: Current line gets wider spacing
 * - **Strong emphasis**: Larger font, bolder weight, accent bar, background glow
 * - **Past/future** opacity differentiation with smooth transitions
 * - **Edge gradient fade** top and bottom
 * - **Auto-follow** pauses during manual scroll, resumes after 1.5s idle
 */
@Composable
fun LyricsView(
    lyrics: List<LrcLine>,
    currentLineIndexProvider: () -> Int,
    lineProgressProvider: () -> Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isCalibrationMode: Boolean = false,
    onCalibrate: (Long) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    var viewportHeightPx by remember { mutableIntStateOf(0) }

    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var autoFollow by rememberSaveable { mutableStateOf(true) }
    val lastUserInteractionAt = rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            autoFollow = false
            lastUserInteractionAt.longValue = SystemClock.elapsedRealtime()
        } else {
            val snapshot = lastUserInteractionAt.longValue
            delay(1500)
            if (lastUserInteractionAt.longValue == snapshot) {
                autoFollow = true
            }
        }
    }

    LaunchedEffect(currentLineIndexProvider(), autoFollow, lyrics, viewportHeightPx) {
        if (!autoFollow) return@LaunchedEffect
        val index = currentLineIndexProvider()
        if (index !in lyrics.indices || lyrics.isEmpty()) return@LaunchedEffect
        if (viewportHeightPx <= 0) return@LaunchedEffect

        // Add a short delay to ensure LazyColumn layout measurement stabilizes
        delay(80)

        val layoutInfo = listState.layoutInfo
        val viewportHeight = if (layoutInfo.viewportEndOffset > 0) {
            layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        } else {
            viewportHeightPx
        }
        val targetLazyIndex = index + 1 // Offset by 1 because index 0 is the top Spacer
        val visibleItems = layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.find { it.index == targetLazyIndex }
        val itemHeight = currentItem?.size ?: with(density) { 56.dp.roundToPx() }
        val targetOffset = -(viewportHeight / 2 - itemHeight / 2)

        listState.animateScrollToItem(
            index = targetLazyIndex,
            scrollOffset = targetOffset
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val boxHeight = maxHeight

        LaunchedEffect(boxHeight) {
            viewportHeightPx = with(density) { boxHeight.toPx().toInt() }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.xs)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // Elegant alpha fading edge on top
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = fadeHeight.toPx()
                        ),
                        blendMode = BlendMode.DstIn
                    )
                    // Elegant alpha fading edge on bottom
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height - fadeHeight.toPx(),
                            endY = size.height
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Top Spacer for precise center alignment of the first lyrics item
            item {
                Spacer(modifier = Modifier.height(boxHeight / 2))
            }

            itemsIndexed(
                items = lyrics,
                key = { index, _ -> index }
            ) { index, line ->
                val stableOnClick = remember(line.timestamp, isCalibrationMode, onSeek, onCalibrate) {
                    {
                        if (isCalibrationMode) {
                            onCalibrate(line.timestamp)
                        } else {
                            onSeek(line.timestamp)
                        }
                    }
                }
                LyricsLineItem(
                    text = line.text,
                    isCurrent = index == currentLineIndexProvider(),
                    isPast = index < currentLineIndexProvider(),
                    lineProgress = if (index == currentLineIndexProvider()) lineProgressProvider() else 0f,
                    onClick = stableOnClick,
                    isCalibrationMode = isCalibrationMode
                )
            }

            // Bottom Spacer for precise center alignment of the last lyrics item
            item {
                Spacer(modifier = Modifier.height(boxHeight / 2))
            }
        }
    }
}

/**
 * Empty state for synced lyrics ->animated fade-in.
 */
@Composable
fun LyricsEmptyState(message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(400))) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Loading state for lyrics fetching ->animated fade-in.
 */
@Composable
fun LyricsLoadingState(modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(400))) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.padding(AppSpacing.xlg),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}
