package luzzr.muse.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.Song
import luzzr.muse.ui.R
import luzzr.muse.ui.haptic.pressScale
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
 * - Shows selection checkbox when in selection mode.
 * - When [menuItems] is non-empty, an overflow "⋮" button appears with
 *   a [DropdownMenu].
 * - When [onLongClick] is provided, long-pressing the row triggers it
 *   (used to enter selection mode in Library).
 *
 * @param song             The song data to display.
 * @param isPlaying        Whether this song is the currently playing track.
 * @param onClick          Called when the row is tapped.
 * @param onLongClick      Optional callback for long-press (e.g. enter selection mode).
 * @param menuItems        Optional overflow-menu actions; empty = no menu shown.
 * @param artworkSize      Album-art thumbnail size (Home uses 36 dp, Library 40 dp).
 * @param isSelected       Whether this song is selected in multi-select mode.
 * @param onSelectionChanged Callback when selection state changes.
 * @param selectionMode    When true, a selection checkbox (check / ring) is shown
 *                         in front of the row. When false, no leading icon is drawn
 *                         (fixes the always-on ring on every song in normal mode).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    menuItems: List<SongMenuItem> = emptyList(),
    artworkSize: Dp = 40.dp,
    isSelected: Boolean = false,
    onSelectionChanged: ((Boolean) -> Unit)? = null,
    selectionMode: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.background
    }

    if (onLongClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        // Use combinedClickable via Box to support long press
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MuseShapeTokens.Item)
                .background(bgColor)
                .pressScale(interactionSource, 0.98f)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (onSelectionChanged != null) {
                            onSelectionChanged(!isSelected)
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            SongListItemContent(
                song = song,
                isPlaying = isPlaying,
                isSelected = isSelected,
                menuItems = menuItems,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                artworkSize = artworkSize,
                selectionMode = selectionMode
            )
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource, 0.98f)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (onSelectionChanged != null) {
                            onSelectionChanged(!isSelected)
                        } else {
                            onClick()
                        }
                    }
                ),
            shape = MuseShapeTokens.Item,
            color = bgColor
        ) {
            SongListItemContent(
                song = song,
                isPlaying = isPlaying,
                isSelected = isSelected,
                menuItems = menuItems,
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
                artworkSize = artworkSize,
                selectionMode = selectionMode
            )
        }
    }
}

@Composable
private fun SongListItemContent(
    song: Song,
    isPlaying: Boolean,
    isSelected: Boolean,
    menuItems: List<SongMenuItem>,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    artworkSize: Dp,
    selectionMode: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(AppSpacing.sm))
        }

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
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (song.album.isNotBlank()) {
                    Text(
                        text = " · ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(AppSpacing.xs))

        if (isPlaying) {
            NowPlayingBars(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
                enabled = true
            )
            Spacer(Modifier.width(AppSpacing.xxs))
        }
        Text(
            song.formattedDuration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )

        // Overflow menu (only when menu items provided)
        if (menuItems.isNotEmpty()) {
            Box {
                IconButton(onClick = { onShowMenuChange(true) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.ui_action_more))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onShowMenuChange(false) }) {
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
                                onShowMenuChange(false)
                                item.onClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
