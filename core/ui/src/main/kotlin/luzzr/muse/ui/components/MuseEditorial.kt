package luzzr.muse.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.LocalMuseVisualStyle
import luzzr.muse.ui.theme.MuseShapeTokens

@Composable
fun MusePage(modifier: Modifier = Modifier, ambient: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val visuals = LocalMuseVisualStyle.current
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .background(background)
    ) {
        if (ambient) {
            Canvas(Modifier.fillMaxSize()) {
                drawEditorialGlow(
                    primary = visuals.pageGlow,
                    secondary = visuals.pageGlowSecondary
                )
            }
        }
        content()
    }
}

private fun DrawScope.drawEditorialGlow(primary: Color, secondary: Color) {
    val radius = size.maxDimension * 0.72f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary, Color.Transparent),
            center = Offset(size.width * 0.12f, size.height * 0.02f),
            radius = radius
        ),
        radius = radius,
        center = Offset(size.width * 0.12f, size.height * 0.02f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(secondary, Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.68f),
            radius = radius * 0.82f
        ),
        radius = radius * 0.82f,
        center = Offset(size.width * 0.92f, size.height * 0.68f)
    )
}

@Composable
fun MuseEditorialCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
    content: @Composable ColumnScope.() -> Unit
) {
    val visuals = LocalMuseVisualStyle.current
    Surface(
        modifier = modifier,
        shape = MuseShapeTokens.SectionCard,
        color = color,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, visuals.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = visuals.cardElevation
    ) {
        Column(content = content)
    }
}

@Composable
fun MuseSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    supportingText: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(AppSpacing.xxxs))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(Modifier.height(AppSpacing.xxs))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(AppSpacing.sm))
            action()
        }
    }
}

@Composable
fun MuseStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier,
        shape = MuseShapeTokens.Pill,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

@Composable
fun MuseSkeleton(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    highlightColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    val reduceMotion = LocalReduceMotion.current
    val travel = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "muse_skeleton")
        val animatedTravel by transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "muse_skeleton_travel"
        )
        animatedTravel
    }
    val brush = if (reduceMotion) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(travel * 420f, 0f),
            end = Offset((travel + 1f) * 420f, 220f)
        )
    }
    Box(
        modifier = modifier.background(brush, MuseShapeTokens.Item)
    )
}
