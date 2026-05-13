package luzzr.muse.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import luzzr.muse.data.network.LrcLine

private const val FADE_ALPHA_STRONG = 0.95f
private val fadeHeight = 120.dp
private val linePaddingV = 16.dp
private val linePaddingH = 28.dp
private const val ACCENT_BAR_WIDTH = 4f // dp

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
    currentLineIndex: Int,
    lineProgress: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
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

    LaunchedEffect(currentLineIndex, autoFollow) {
        if (!autoFollow) return@LaunchedEffect
        if (currentLineIndex !in lyrics.indices || lyrics.isEmpty()) return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.find { it.index == currentLineIndex }
        val viewportCenter = layoutInfo.viewportEndOffset / 2f
        val isCentered = currentItem?.let {
            val itemCenter = it.offset + it.size / 2f
            kotlin.math.abs(itemCenter - viewportCenter) < viewportCenter * 0.20f
        } ?: false

        if (!isCentered) {
            val estimatedItemHeight = with(density) { 56.dp.roundToPx() }
            val centerOffset = (layoutInfo.viewportEndOffset - estimatedItemHeight) / 2
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = centerOffset.coerceAtLeast(0)
            )
        }
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
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(
                top = boxHeight / 2 - 48.dp + 56.dp,
                bottom = (boxHeight / 2 - 48.dp - 56.dp).coerceAtLeast(0.dp)
            )
        ) {
            itemsIndexed(
                items = lyrics,
                key = { index, _ -> index }
            ) { index, line ->
                LyricsLine(
                    text = line.text,
                    isCurrent = index == currentLineIndex,
                    isPast = index < currentLineIndex,
                    lineProgress = if (index == currentLineIndex) lineProgress else 0f,
                    onClick = { onSeek(line.timestamp) }
                )
            }
        }

        EdgeFadeOverlay(
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface.copy(alpha = 1f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    Color.Transparent
                )
            ),
            height = fadeHeight,
            alignment = Alignment.TopCenter
        )
        EdgeFadeOverlay(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    MaterialTheme.colorScheme.surface.copy(alpha = 1f)
                )
            ),
            height = fadeHeight,
            alignment = Alignment.BottomCenter
        )
    }
}

@Composable
private fun BoxScope.EdgeFadeOverlay(brush: Brush, height: Dp, alignment: Alignment) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(brush = brush)
            .align(alignment),
    )
}

@Composable
private fun LyricsLine(
    text: String,
    isCurrent: Boolean,
    isPast: Boolean,
    lineProgress: Float,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    // === Animated visual properties ===
    val targetAlpha = when {
        isCurrent -> 1f
        isPast -> 0.80f
        else -> 0.90f
    }
    val targetFontSizeSp = if (isCurrent) 24f else 16f
    val targetWeight = if (isCurrent) 800f else 400f
    val targetScale = if (isCurrent) 1f else 0.92f
    val targetHighlightAlpha = if (isCurrent) 0.15f else 0f

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "line_alpha"
    )
    val animatedFontSize by animateFloatAsState(
        targetValue = targetFontSizeSp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "line_font_size"
    )
    val animatedWeight by animateFloatAsState(
        targetValue = targetWeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "line_weight"
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "line_scale"
    )
    val accentBarWidth by animateDpAsState(
        targetValue = if (isCurrent) ACCENT_BAR_WIDTH.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "accent_bar"
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = targetHighlightAlpha,
        animationSpec = tween(280),
        label = "highlight_alpha"
    )
    val animatedLetterSpacing by animateFloatAsState(
        targetValue = if (isCurrent) 2f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "letter_spacing"
    )

    // Tap bounce animation
    var isTapped by remember { mutableStateOf(false) }
    val tapBounce by animateFloatAsState(
        targetValue = if (isTapped) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tap_bounce"
    )

    // Infinite pulse for accent bar on current line
    val infiniteTransition = rememberInfiniteTransition(label = "accent_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "accent_pulse_alpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "accent_pulse_scale"
    )

    val textColor = if (isCurrent) primary else Color.Black
    val interactionSource = remember { MutableInteractionSource() }

    // Karaoke: blend lineProgress into text color for a fill-like effect
    val karaokeColor = if (isCurrent && lineProgress in 0.01f..0.99f) {
        lerp(textColor, Color.Black.copy(alpha = 0.60f), lineProgress * 0.6f)
    } else {
        textColor.copy(alpha = animatedAlpha)
    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .zIndex(if (isCurrent) 1f else 0f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(animatedScale * tapBounce)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, bottomStart = 20.dp,
                        topEnd = 0.dp, bottomEnd = 0.dp
                    )
                )
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            primary.copy(alpha = highlightAlpha),
                            primary.copy(alpha = highlightAlpha * 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) {
                    isTapped = true
                    onClick()
                }
        ) {
            // Accent bar with breathing pulse for current line
            if (isCurrent) {
                Canvas(
                    modifier = Modifier
                        .width(accentBarWidth * pulseScale)
                        .height(40.dp)
                        .align(Alignment.CenterStart)
                ) {
                    drawRoundRect(
                        color = primary.copy(alpha = pulseAlpha),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }

            // Karaoke fill overlay: a semi-transparent bar sweeps left-to-right
            if (isCurrent && lineProgress in 0.02f..0.98f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                ) {
                    val barWidth = size.width * lineProgress
                    val barHeight = size.height * 0.04f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.15f),
                        size = androidx.compose.ui.geometry.Size(barWidth.coerceAtLeast(4f), barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }
            }

            Text(
                text = text,
                color = karaokeColor,
                fontSize = animatedFontSize.sp,
                fontWeight = FontWeight(animatedWeight.toInt()),
                letterSpacing = animatedLetterSpacing.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = linePaddingV, horizontal = linePaddingH)
            )
        }
    }

    LaunchedEffect(isTapped) {
        if (isTapped) {
            delay(200)
            isTapped = false
        }
    }
}

/**
 * Empty state for synced lyrics — animated fade-in.
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
 * Loading state for lyrics fetching — animated fade-in.
 */
@Composable
fun LyricsLoadingState(modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(400))) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}
