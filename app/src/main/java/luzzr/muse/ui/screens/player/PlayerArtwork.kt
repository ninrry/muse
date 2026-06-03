package luzzr.muse.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import luzzr.muse.data.model.Song
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.components.DefaultAlbumCover
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
fun AlbumArtSection(song: Song, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val coverSize = MuseDimens.adaptivePlayerArtworkSize()

    Crossfade(
        targetState = song,
        animationSpec = tween(MotionDuration.medium2),
        label = "album_art_crossfade"
    ) { targetSong ->
        Surface(
            modifier = modifier
                .size(coverSize)
                .then(if (!isPlaying) Modifier.alpha(0.7f) else Modifier),
            shape = RoundedCornerShape(AppSpacing.lg),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = AppSpacing.xxs
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                DefaultAlbumCover(
                    placeholder = targetSong.title.take(1).uppercase(),
                    modifier = Modifier.fillMaxSize()
                )
                if (targetSong.artworkUri != null) {
                    AsyncImage(
                        model = targetSong.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
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
                targetContentEnter = fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(200)),
                initialContentExit = fadeOut(animationSpec = tween(100)) +
                    scaleOut(targetScale = 0.95f, animationSpec = tween(100))
            )
        },
        label = "song_info"
    ) { targetSong ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = targetSong.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(AppSpacing.xxs))
            Text(
                text = targetSong.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
