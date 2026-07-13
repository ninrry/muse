package luzzr.muse.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import luzzr.muse.domain.model.Song
import luzzr.muse.feature.player.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTopBar(
    showLyrics: Boolean,
    currentSong: Song?,
    floatingLyricsEnabled: Boolean = false,
    onBack: () -> Unit,
    onToggleLyrics: () -> Unit,
    onRefreshLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onToggleFloatingLyrics: () -> Unit = {}
) {
    TopAppBar(
        title = {
            if (showLyrics && currentSong != null) {
                Column {
                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(stringResource(R.string.player_now_playing))
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.player_back))
            }
        },
        actions = {
            val toggleDesc = if (showLyrics) {
                stringResource(R.string.player_toggle_artwork)
            } else {
                stringResource(R.string.player_toggle_lyrics)
            }
            IconButton(
                onClick = onToggleLyrics,
                modifier = Modifier.semantics { contentDescription = toggleDesc }
            ) {
                Icon(
                    imageVector = if (showLyrics) Icons.Default.Image else Icons.AutoMirrored.Filled.Article,
                    contentDescription = null,
                    tint = if (showLyrics) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (showLyrics) {
                IconButton(onClick = onRefreshLyrics) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.player_refresh_lyrics),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val floatingDesc = if (floatingLyricsEnabled) {
                stringResource(R.string.player_floating_lyrics_on)
            } else {
                stringResource(R.string.player_floating_lyrics_off)
            }
            IconButton(
                onClick = onToggleFloatingLyrics,
                modifier = Modifier.semantics { contentDescription = floatingDesc }
            ) {
                Icon(
                    imageVector = if (floatingLyricsEnabled) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                    contentDescription = null,
                    tint = if (floatingLyricsEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = onShowQueue) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.player_queue)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
