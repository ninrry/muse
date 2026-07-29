package luzzr.muse.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import luzzr.muse.domain.lyrics.LyricsTimeline
import luzzr.muse.domain.lyrics.LyricsFillMode
import luzzr.muse.domain.model.LrcLine
import luzzr.muse.domain.model.WordSegment
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.animation.MotionLyrics
import luzzr.muse.ui.theme.AppSpacing
import kotlin.math.abs

/**
 * 歌词行。当前行逐字填色与 [positionProvider] 同时钟，避免提前切行导致上色未完。
 */
@Composable
fun LyricsLineItem(
    text: String,
    isCurrent: Boolean,
    isPast: Boolean,
    positionProvider: () -> Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCalibrationMode: Boolean = false,
    distanceFromCurrent: Int = if (isCurrent) 0 else 1,
    wordSegments: List<WordSegment>? = null,
    lineStartMs: Long = 0L,
    lineEndMs: Long = 0L,
    lyricsOffsetMs: Long = 0L,
    reduceMotion: Boolean = false,
    isPlaying: Boolean = true
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val depth = abs(distanceFromCurrent).coerceAtMost(4)

    val targetAlpha = when {
        isCurrent -> 1f
        depth == 1 -> 0.72f
        depth == 2 -> 0.52f
        else -> 0.36f
    }
    val targetScale = when {
        isCurrent -> 1f
        depth == 1 -> 0.97f
        depth == 2 -> 0.94f
        else -> 0.92f
    }

    val alphaState = if (reduceMotion) null else animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = MotionLyrics.depthAlpha,
        label = "a"
    )
    val scaleState = if (reduceMotion) null else animateFloatAsState(
        targetValue = targetScale,
        animationSpec = MotionLyrics.focusScale,
        label = "s"
    )

    val targetVerticalPadding = if (isCurrent) 12.dp else 8.dp
    val animatedVerticalPadding by animateDpAsState(
        targetValue = targetVerticalPadding,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            tween(MotionDuration.medium1, easing = FastOutSlowInEasing)
        },
        label = "vp"
    )

    val interactionSource = remember { MutableInteractionSource() }

    val style = if (isCurrent) {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.15).sp,
            fontSize = 20.sp,
            lineHeight = 28.sp,
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
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scaleState?.value ?: targetScale
                scaleY = scaleState?.value ?: targetScale
                alpha = alphaState?.value ?: targetAlpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = text
                this.text = AnnotatedString(text)
                role = Role.Button
            }
            .padding(horizontal = AppSpacing.lg, vertical = animatedVerticalPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isCurrent) {
            ActiveLyricText(
                text = text,
                positionProvider = positionProvider,
                lyricsOffsetMs = lyricsOffsetMs,
                baseColor = onSurface.copy(alpha = 0.45f),
                fillColor = primary,
                style = style,
                wordSegments = wordSegments,
                lineStartMs = lineStartMs,
                lineEndMs = lineEndMs,
                reduceMotion = reduceMotion,
                isPlaying = isPlaying
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

@Composable
private fun ActiveLyricText(
    text: String,
    positionProvider: () -> Long,
    baseColor: Color,
    fillColor: Color,
    style: TextStyle,
    wordSegments: List<WordSegment>? = null,
    lineStartMs: Long = 0L,
    lineEndMs: Long = 0L,
    lyricsOffsetMs: Long = 0L,
    reduceMotion: Boolean = false,
    isPlaying: Boolean = true
) {
    val effectiveEnd = resolveLineEndMs(lineStartMs, lineEndMs, wordSegments)
    var progress by remember(lineStartMs, effectiveEnd) { mutableFloatStateOf(0f) }
    var revealCharacters by remember(text, lineStartMs, effectiveEnd) { mutableFloatStateOf(0f) }
    var fillMode by remember(text, lineStartMs, effectiveEnd, wordSegments) {
        mutableStateOf(LyricsFillMode.STATIC)
    }
    val timeline = remember(text, lineStartMs, effectiveEnd, wordSegments) {
        LyricsTimeline(listOf(LrcLine(lineStartMs, text, wordSegments)))
    }

    LaunchedEffect(lineStartMs, effectiveEnd, reduceMotion, isPlaying, lyricsOffsetMs, timeline) {
        if (reduceMotion) {
            progress = 1f
            revealCharacters = text.length.toFloat()
            return@LaunchedEffect
        }
        var refPos = 0L
        var refWall = 0L
        var anchored = false
        // 进度只增不减（同句内），避免上游回跳导致填色回退
        var peak = 0f
        while (true) {
            withFrameNanos {
                val now = SystemClock.elapsedRealtime()
                val upstream = positionProvider()
                val movedBackwards = anchored && upstream + 80L < refPos
                if (!anchored || upstream != refPos) {
                    refPos = upstream
                    refWall = now
                    anchored = true
                }
                val projected = if (isPlaying) {
                    refPos + (now - refWall).coerceAtLeast(0L)
                } else {
                    upstream
                }
                val frame = timeline.frameAt(
                    positionMs = projected,
                    durationMs = effectiveEnd,
                    offsetMs = lyricsOffsetMs
                )
                fillMode = frame.fillMode
                val raw = frame.lineProgress
                if (movedBackwards) {
                    peak = raw
                    progress = raw
                    revealCharacters = frame.revealCharacters
                } else if (raw >= peak) {
                    peak = raw
                    progress = peak
                    revealCharacters = maxOf(revealCharacters, frame.revealCharacters)
                }
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx().toInt().coerceAtLeast(1) }
        val measuredLayout = remember(text, style, maxWidth, density) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                constraints = Constraints(maxWidth = maxWidthPx),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { measuredLayout.size.height.toDp() })
        ) {
            val total = text.length
            val reveal = if (reduceMotion || progress >= 0.999f) {
                total.toFloat()
            } else if (fillMode == LyricsFillMode.STATIC) {
                0f
            } else {
                revealCharacters.coerceIn(0f, total.toFloat())
            }
            val xOffset = ((size.width - measuredLayout.size.width) / 2f).coerceAtLeast(0f)

            drawText(measuredLayout, topLeft = Offset(xOffset, 0f), color = baseColor)

            if (reveal > 0f) {
                val lineCount = measuredLayout.lineCount
                for (line in 0 until lineCount) {
                    val ls = measuredLayout.getLineStart(line)
                    val le = measuredLayout.getLineEnd(line)
                    val lineProg = if (le <= reveal) {
                        1f
                    } else if (ls >= reveal) {
                        0f
                    } else {
                        ((reveal - ls) / (le - ls).coerceAtLeast(1)).coerceIn(0f, 1f)
                    }
                    if (lineProg <= 0f) continue
                    val left = measuredLayout.getLineLeft(line) + xOffset
                    val right = measuredLayout.getLineRight(line) + xOffset
                    val top = measuredLayout.getLineTop(line)
                    val bottom = measuredLayout.getLineBottom(line)
                    clipRect(
                        left = left,
                        top = top,
                        right = left + (right - left) * lineProg,
                        bottom = bottom
                    ) {
                        drawText(measuredLayout, topLeft = Offset(xOffset, 0f), color = fillColor)
                    }
                }
            }
        }
    }
}

/** 行结束时间：至少覆盖到下一句，并给末词留出填色窗口 */
internal fun resolveLineEndMs(
    lineStartMs: Long,
    lineEndMs: Long,
    wordSegments: List<WordSegment>?
): Long {
    var end = lineEndMs.takeIf { it > lineStartMs } ?: (lineStartMs + 4000L)
    if (!wordSegments.isNullOrEmpty()) {
        val last = wordSegments.last()
        // 末词至少 280ms 填色窗口，避免「刚碰到词头就切句」
        val lastWordEnd = last.timeMs + 280L
        if (lastWordEnd > end) end = lastWordEnd
    }
    // 最短行长，防止极短行瞬间切走
    if (end - lineStartMs < 400L) {
        end = lineStartMs + 400L
    }
    return end
}
