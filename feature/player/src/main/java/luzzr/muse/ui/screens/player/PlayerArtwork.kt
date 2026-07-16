package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.animation.MotionPlayer
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

@Composable
fun AlbumArtSection(song: Song, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val coverSize = MuseDimens.adaptivePlayerArtworkSize()
    val glowSize = coverSize + 48.dp

    val targetAlpha = if (isPlaying) 1f else 0.78f
    val targetScale = if (isPlaying) 1f else 0.97f
    val glowAlphaTarget = if (isPlaying) 0.45f else 0.22f

    val alphaAnim by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = MotionPlayer.artworkSettle,
        label = "artwork_alpha"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = MotionPlayer.artworkSettle,
        label = "artwork_scale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = glowAlphaTarget,
        animationSpec = MotionPlayer.ambient,
        label = "artwork_glow"
    )

    val ambient = MaterialTheme.colorScheme.primary
    val ambientSoft = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier.size(glowSize),
        contentAlignment = Alignment.Center
    ) {
        // Soft ambient bloom behind cover (immersive "vinyl room" feel)
        Box(
            modifier = Modifier
                .size(glowSize)
                .graphicsLayer { alpha = glowAlpha }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ambient.copy(alpha = 0.55f),
                            ambientSoft.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        Crossfade(
            targetState = song,
            animationSpec = tween(MotionDuration.medium2),
            label = "album_art_crossfade"
        ) { targetSong ->
            Surface(
                modifier = Modifier
                    .size(coverSize)
                    .graphicsLayer {
                        alpha = alphaAnim
                        scaleX = scaleAnim
                        scaleY = scaleAnim
                        shadowElevation = if (isPlaying) 18f else 10f
                        shape = MuseShapeTokens.Album
                        clip = true
                    },
                shape = MuseShapeTokens.Album,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DefaultAlbumCover(
                        placeholder = targetSong.title.take(1).uppercase(),
                        modifier = Modifier.fillMaxSize()
                    )
                    if (targetSong.artworkUri != null) {
                        AsyncImage(
                            model = targetSong.artworkUri,
                            contentDescription = "Album artwork for ${targetSong.title} by ${targetSong.artist}",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun SongInfoSection(song: Song, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = song,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(animationSpec = tween(220)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(220)),
                initialContentExit = fadeOut(animationSpec = tween(120)) +
                    scaleOut(targetScale = 0.96f, animationSpec = tween(120))
            )
        },
        label = "song_info"
    ) { targetSong ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = targetSong.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                text = targetSong.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (targetSong.album.isNotBlank()) {
                Spacer(Modifier.height(AppSpacing.xxxs))
                Text(
                    text = targetSong.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
