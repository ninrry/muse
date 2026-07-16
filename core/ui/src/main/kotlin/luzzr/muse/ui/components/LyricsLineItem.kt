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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import luzzr.muse.domain.model.WordSegment
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionLyrics
import luzzr.muse.ui.theme.AppSpacing
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lyrics line row. When [isCurrent] is true, the active karaoke fill
 * recomputes per-frame from [positionProvider] + [lyricsOffsetMs] +
 * [lineStartMs]/[lineEndMs] at the display refresh rate, independent of
 * the upstream 20Hz position polling.
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
    reduceMotion: Boolean = false
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
        animationSpec = if (reduceMotion) tween(0) else tween(220, easing = FastOutSlowInEasing),
        label = "vp"
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
                scaleX = scaleState?.value ?: targetScale
                scaleY = scaleState?.value ?: targetScale
                alpha = alphaState?.value ?: targetAlpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = AppSpacing.lg, vertical = animatedVerticalPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isCurrent) {
            ActiveLyricText(
                text = text,
                positionProvider = positionProvider,
                lyricsOffsetMs = lyricsOffsetMs,
                baseColor = onSurface.copy(alpha = 0.20f),
                fillColor = primary,
                style = style,
                wordSegments = wordSegments,
                lineStartMs = lineStartMs,
                lineEndMs = lineEndMs,
                reduceMotion = reduceMotion
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
    reduceMotion: Boolean = false
) {
    val span = (lineEndMs - lineStartMs).coerceAtLeast(1L)
    var progress by remember(lineStartMs, lineEndMs) { mutableFloatStateOf(0f) }

    // Wall-clock-driven karaoke fill. We anchor a `(pos, wall)` reference
    // each time the upstream positionProvider delivers a new sample and
    // project forward every vsync using SystemClock.elapsedRealtime.
    // This decouples the fill rate from the upstream 20Hz polling
    // cadence: progress advances continuously and smoothly at the panel
    // refresh rate (60-120Hz) instead of stepping once per 50ms upstream
    // sample. The previous design extrapolated only ~24ms and gated on a
    // 0.0008 delta, which produced visible "one frame at a time" jumps.
    LaunchedEffect(lineStartMs, lineEndMs, reduceMotion) {
        var refPos = 0L
        var refWall = 0L
        var anchored = false
        while (true) {
            withFrameNanos {
                val now = SystemClock.elapsedRealtime()
                val upstream = positionProvider()
                if (!anchored || upstream != refPos) {
                    refPos = upstream
                    refWall = now
                    anchored = true
                }
                val projected = refPos + (now - refWall).coerceAtLeast(0L)
                val pos = (projected + lyricsOffsetMs).coerceAtLeast(0L)
                progress = ((pos - lineStartMs).toFloat() / span).coerceIn(0f, 1f)
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
            val reveal = computeRevealChars(progress, wordSegments, lineStartMs, lineEndMs, total)
            val xOffset = ((size.width - measuredLayout.size.width) / 2f).coerceAtLeast(0f)

            drawText(measuredLayout, topLeft = Offset(xOffset, 0f), color = baseColor)

            if (reveal > 0) {
                val lineCount = measuredLayout.lineCount
                for (line in 0 until lineCount) {
                    val ls = measuredLayout.getLineStart(line)
                    val le = measuredLayout.getLineEnd(line)
                    val lineProg = if (le <= reveal) 1f
                    else if (ls >= reveal) 0f
                    else ((reveal - ls).toFloat() / (le - ls).coerceAtLeast(1)).coerceIn(0f, 1f)
                    if (lineProg <= 0f) continue
                    val left = measuredLayout.getLineLeft(line) + xOffset
                    val right = measuredLayout.getLineRight(line) + xOffset
                    val top = measuredLayout.getLineTop(line)
                    val bottom = measuredLayout.getLineBottom(line)
                    clipRect(
                        left = left, top = top,
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

/**
 * Returns a subpixel "reveal" measured in character units (e.g. 2.37).
 * The caller combines this with the line's measured [getLineStart] /
 * [getLineEnd] offsets to produce a per-line fill ratio in [clipRect],
 * yielding a smooth, continuous (not stepped) karaoke reveal at the
 * display refresh rate.
 */
private fun computeRevealChars(
    progress: Float,
    wordSegments: List<WordSegment>?,
    lineStartMs: Long,
    lineEndMs: Long,
    total: Int
): Float {
    if (total <= 0) return 0f
    val clamped = progress.coerceIn(0f, 1f)
    return if (!wordSegments.isNullOrEmpty()) {
        val span = (lineEndMs - lineStartMs).coerceAtLeast(1L)
        val currentMs = lineStartMs + span * clamped
        var revealChars = 0f
        for (i in wordSegments.indices) {
            val w = wordSegments[i]
            if (w.timeMs > currentMs) break
            val next = wordSegments.getOrNull(i + 1)
            val within = if (next != null) {
                ((currentMs - w.timeMs).toFloat() / (next.timeMs - w.timeMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
            } else 1f
            revealChars = w.charStart + w.text.length * within
        }
        revealChars.coerceAtMost(total.toFloat())
    } else {
        clamped * total
    }
}
