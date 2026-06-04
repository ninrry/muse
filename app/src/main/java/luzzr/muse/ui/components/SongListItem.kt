package luzzr.muse.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import luzzr.muse.data.model.Song
import luzzr.muse.ui.screens.home.NowPlayingBars
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseShapeTokens

/**
 * Menu action descriptor for [SongListItem].
 * Pass a list of these to enable the overflow context menu.
 */
data class SongMenuItem(
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Unified song list row used across Home and Library screens.
 *
 * - Shows album art, title, artist, optional codec badge, duration, and
 *   a pulsing equalizer indicator when the song is currently playing.
 * - When [menuItems] is non-empty, an overflow "⋮" button appears with
 *   a [DropdownMenu].
 *
 * @param song             The song data to display.
 * @param isPlaying        Whether this song is the currently playing track.
 * @param onClick          Called when the row is tapped.
 * @param menuItems        Optional overflow-menu actions; empty = no menu shown.
 * @param artworkSize      Album-art thumbnail size (Home uses 36 dp, Library 40 dp).
 */
@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    menuItems: List<SongMenuItem> = emptyList(),
    artworkSize: Dp = 40.dp
) {
    var showMenu by remember { mutableStateOf(false) }
    val bgColor = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.background
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xs),
        shape = MuseShapeTokens.Item,
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumbnail(
                artworkUri = song.artworkUri,
                placeholder = song.title.take(1).uppercase(),
                modifier = Modifier.size(artworkSize)
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            Spacer(Modifier.width(AppSpacing.xs))

            // Now-playing indicator: pulsing equalizer bars
            if (isPlaying) {
                NowPlayingBars(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(AppSpacing.xxs))
            }
            Text(
                song.formattedDuration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Overflow menu (only when menu items provided)
            if (menuItems.isNotEmpty()) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        menuItems.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        item.label,
                                        color = if (item.isDestructive) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    item.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
