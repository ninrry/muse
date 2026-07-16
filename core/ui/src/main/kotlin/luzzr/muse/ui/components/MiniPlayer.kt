package luzzr.muse.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import coil.compose.SubcomposeAsyncImage
import kotlin.math.abs
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

/**
 * Playback progress bar integrated into MiniPlayer's top edge.
 */
@Composable
fun MiniPlayerProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val lastProgress = remember { mutableFloatStateOf(clampedProgress) }
    val isSeekJump = abs(clampedProgress - lastProgress.floatValue) > 0.1f
    lastProgress.floatValue = clampedProgress
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = if (isSeekJump) {
            tween(durationMillis = 100, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 350, easing = FastOutSlowInEasing)
        },
        label = "mini_progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppSpacing.xs)
            .padding(horizontal = AppSpacing.md)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppSpacing.xxs))
            .background(MaterialTheme.colorScheme.outlineVariant)
    ) {
        // Fill — use primary for playback progress
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppSpacing.xxs))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    shuffleMode: Boolean = false,
    onQueueClick: () -> Unit = {}
) {
    val miniPlayerHeight = MuseDimens.adaptiveMiniPlayerHeight()
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(miniPlayerHeight),
        shape = RoundedCornerShape(topStart = MuseDimens.SmallCardCornerRadius, topEnd = MuseDimens.SmallCardCornerRadius)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Progress bar overlay on top edge of MiniPlayer
            MiniPlayerProgressBar(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = AppSpacing.md,
                        end = AppSpacing.md,
                        top = 6.dp, // margin for progress bar
                        bottom = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumbnail(
                    artworkUri = song.artworkUri,
                    placeholder = song.title.take(1).uppercase(),
                    modifier = Modifier.size(40.dp) // Adjusted from xxlg (48.dp) to 40.dp to fit vertical constraints
                )

                Spacer(Modifier.width(AppSpacing.md))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (shuffleMode) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.ui_player_shuffle_active),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = onQueueClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.ui_player_queue),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumArtThumbnail(artworkUri: String?, placeholder: String, modifier: Modifier = Modifier) {
    // Always show default cover as fallback; try loading MediaStore artwork on top
    Box(modifier = modifier.clip(RoundedCornerShape(AppSpacing.sm))) {
        DefaultAlbumCover(placeholder = placeholder, modifier = Modifier.fillMaxSize())
        if (artworkUri != null) {
            SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .crossfade(true)
                    .size(200)
                    .build(),
                contentDescription = stringResource(R.string.ui_album_artwork),
                modifier = Modifier.fillMaxSize(),
                loading = { },
                error = { }
            )
        }
    }
}

/**
 * Draws a generated default album cover on a Canvas with a warm colored background
 * and bold, centered text. Color is derived from a hash of the placeholder string.
 */
@Composable
fun DefaultAlbumCover(placeholder: String, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val displayText = placeholder.take(8)

    // Scale font size so shorter strings appear larger, longer strings shrink
    val fontSize = when {
        displayText.length >= 6 -> 14.sp
        displayText.length >= 4 -> 18.sp
        displayText.length >= 2 -> 22.sp
        else -> 28.sp
    }

    // Generate a warm color from the hash of the placeholder
    val bgColor = remember(placeholder) {
        val hash = kotlin.math.abs(placeholder.hashCode())
        val hue = (hash % 50).toFloat() // 0..49: warm hues (reds, oranges, yellows)
        // Add subtle saturation/value variation from hash bits
        val sat = 0.55f + (hash % 10) * 0.03f // 0.55..0.82
        val value = 0.70f + (hash / 10 % 8) * 0.025f // 0.70..0.875
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        Color(colorInt)
    }

    val textStyle = TextStyle(
        color = Color.White,
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
        modifier = modifier.clip(RoundedCornerShape(AppSpacing.sm)),
        tonalElevation = AppSpacing.xxxs
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = bgColor, size = size)
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
