package luzzr.muse.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * Playback progress bar — leaf-only; reads [progressProvider] per frame (120 Hz ready).
 */
@Composable
fun MiniPlayerProgressBar(
    progressProvider: () -> Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // 直接采样，不做 120ms 二次平滑（避免 120Hz 拖尾）
    var progress by remember { mutableFloatStateOf(progressProvider().coerceIn(0f, 1f)) }
    LaunchedEffect(isPlaying) {
        while (true) {
            withFrameNanos {
                val next = progressProvider().coerceIn(0f, 1f)
                if (next != progress) progress = next
            }
            if (!isPlaying) kotlinx.coroutines.delay(200)
        }
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val fillColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress)
                .clip(RoundedCornerShape(999.dp))
                .background(fillColor)
        )
    }
}

/** Compatibility overload for static previews and older callers. */
@Composable
fun MiniPlayerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    MiniPlayerProgressBar(
        progressProvider = { progress },
        isPlaying = false,
        modifier = modifier
    )
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressProvider: () -> Float = { 0f },
    shuffleMode: Boolean = false,
    onQueueClick: () -> Unit = {}
) {
    val miniPlayerHeight = MuseDimens.adaptiveMiniPlayerHeight()
    val cardInteraction = remember { MutableInteractionSource() }
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(miniPlayerHeight)
            .pressScale(cardInteraction, 0.985f),
        shape = MuseShapeTokens.Card,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        interactionSource = cardInteraction
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MiniPlayerProgressBar(
                progressProvider = progressProvider,
                isPlaying = isPlaying,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md)
                    .padding(top = 5.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = AppSpacing.md,
                        end = AppSpacing.xs,
                        top = 8.dp,
                        bottom = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumbnail(
                    artworkUri = song.artworkUri,
                    placeholder = song.title.take(1).uppercase(),
                    modifier = Modifier.size(44.dp)
                )

                Spacer(Modifier.width(AppSpacing.sm))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (shuffleMode) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.ui_player_shuffle_active),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(AppSpacing.xxs))
                }

                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Crossfade(
                            targetState = isPlaying,
                            animationSpec = tween(MotionDuration.medium1),
                            label = "mini_play_pause"
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    if (playing) R.string.ui_player_pause else R.string.ui_player_play
                                ),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onQueueClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.ui_player_queue),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumArtThumbnail(artworkUri: String?, placeholder: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(MuseShapeTokens.Item)) {
        DefaultAlbumCover(placeholder = placeholder, modifier = Modifier.fillMaxSize())
        if (artworkUri != null) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .crossfade(true)
                    .size(128)
                    .build(),
                contentDescription = stringResource(R.string.ui_album_artwork),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Generated default album cover: warm hash hue + soft vertical gradient,
 * letter centered with high-contrast cream ink (theme-safe, no pure white).
 */
@Composable
fun DefaultAlbumCover(placeholder: String, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val displayText = placeholder.take(8)
    val inkColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)

    val fontSize = when {
        displayText.length >= 6 -> 14.sp
        displayText.length >= 4 -> 18.sp
        displayText.length >= 2 -> 22.sp
        else -> 28.sp
    }

    val baseColor = remember(placeholder) {
        val hash = kotlin.math.abs(placeholder.hashCode())
        val hue = 18f + (hash % 42).toFloat()
        val sat = 0.42f + (hash % 10) * 0.025f
        val value = 0.62f + (hash / 10 % 8) * 0.03f
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        Color(colorInt)
    }
    val topColor = remember(baseColor) {
        baseColor.copy(
            red = (baseColor.red * 1.08f).coerceAtMost(1f),
            green = (baseColor.green * 1.06f).coerceAtMost(1f),
            blue = (baseColor.blue * 1.04f).coerceAtMost(1f)
        )
    }

    val textStyle = TextStyle(
        color = inkColor,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold
    )

    val textLayoutResult = remember(displayText, textStyle) {
        textMeasurer.measure(
            text = AnnotatedString(displayText),
            style = textStyle
        )
    }

    Surface(
        modifier = modifier.clip(MuseShapeTokens.Item),
        tonalElevation = 0.dp
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(topColor, baseColor),
                    startY = 0f,
                    endY = size.height
                ),
                size = size
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x = (size.width - textLayoutResult.size.width) / 2f,
                    y = (size.height - textLayoutResult.size.height) / 2f
                )
            )
        }
    }
}
