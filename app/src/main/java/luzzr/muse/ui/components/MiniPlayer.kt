package luzzr.muse.ui.components

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
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import luzzr.muse.data.model.Song
import luzzr.muse.ui.animation.MotionDuration

/**
 * Playback progress bar — integrated into MiniPlayer's top edge.
 */
@Composable
fun MiniPlayerProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0.05f, 1f) // minimum 5% for visibility

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(horizontal = 0.dp)
    ) {
        // Track background — use theme surface variant with distinct warmth
        val trackColor = MaterialTheme.colorScheme.outlineVariant
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(trackColor)
        )
        // Fill — use primary for playback progress
        val fillColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = clampedProgress)
                .padding(start = 16.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .background(fillColor)
        )
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float = 0f,
    shuffleMode: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    onQueueClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column {
            // Progress bar as top edge of MiniPlayer
            MiniPlayerProgressBar(progress = progress)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumbnail(
                    artworkUri = song.artworkUri,
                    placeholder = song.title.take(1).uppercase(),
                    modifier = Modifier.size(48.dp)
                )

                Spacer(Modifier.width(16.dp))

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

                IconButton(onClick = onTogglePlayPause) {
                    Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(MotionDuration.medium1),
                        label = "mini_play_pause"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "暂停" else "播放",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Shuffle indicator — visible when shuffle mode is active
                if (shuffleMode) {
                    Box(
                        modifier = Modifier
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = "随机播放",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onQueueClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumArtThumbnail(
    artworkUri: android.net.Uri?,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    // Always show default cover as fallback; try loading MediaStore artwork on top
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        DefaultAlbumCover(placeholder = placeholder, modifier = Modifier.fillMaxSize())
        if (artworkUri != null) {
            SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri)
                    .crossfade(true)
                    .size(200)
                    .build(),
                contentDescription = null,
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
fun DefaultAlbumCover(
    placeholder: String,
    modifier: Modifier = Modifier
) {
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
        val hue = (hash % 50).toFloat() // 0..49 → warm hues (reds, oranges, yellows)
        // Add subtle saturation/value variation from hash bits
        val sat = 0.55f + (hash % 10) * 0.03f   // 0.55–0.82
        val value = 0.70f + (hash / 10 % 8) * 0.025f // 0.70–0.875
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        Color(colorInt)
    }

    val textStyle = TextStyle(
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold
    )

    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        tonalElevation = 2.dp
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Solid colored background
            drawRect(color = bgColor, size = size)

            // White bold text, centered
            val textLayoutResult = textMeasurer.measure(
                text = AnnotatedString(displayText),
                style = textStyle
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
