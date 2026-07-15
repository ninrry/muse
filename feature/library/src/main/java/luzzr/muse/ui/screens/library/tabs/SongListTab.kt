package luzzr.muse.ui.screens.library.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.model.SortType
import luzzr.muse.ui.screens.library.Pinyin
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.ALPHABET_INDEX
import luzzr.muse.ui.components.AlphabetIndexBar
import luzzr.muse.ui.components.getAlphabetIndex
import luzzr.muse.ui.components.getLetterForIndex
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.components.SongMenuItem
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListTab(
    songs: List<Song>,
    currentSongId: Long?,
    onPlayShuffled: (List<Song>) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShareSong: (Song) -> Unit,
    onSearchMetadata: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    // Selection mode parameters
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<Long> = emptySet(),
    onEnterSelectionMode: () -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    onAddSelectedToPlaylist: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    // Alphabet index parameters
    showAlphabetIndex: Boolean = true,
    onLetterSelected: (Int) -> Unit = {},
    // Current sort order (drives the alphabet index key + available letters)
    sortType: SortType = SortType.TITLE_ASC
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.library_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Build section index for quick scroll, keyed on the active sort field
    val sectionIndexMap = remember(songs, sortType) {
        buildSectionIndex(songs) { sortKeyOf(it, sortType) }
    }

    // Only letters that actually exist among the current songs (in ALPHABET_INDEX order)
    val availableLetters = remember(sectionIndexMap) {
        ALPHABET_INDEX.filter { it in sectionIndexMap.keys }
    }

    // The alphabet bar only makes sense for alphabetical sort orders
    val alphabetEnabled = showAlphabetIndex && !isSelectionMode &&
        sortType in setOf(
            SortType.TITLE_ASC, SortType.TITLE_DESC,
            SortType.ARTIST_ASC, SortType.ARTIST_DESC,
            SortType.ALBUM_ASC, SortType.ALBUM_DESC
        )

    val listState = rememberLazyListState()
    var currentAlphabetIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    // Update current alphabet index based on scroll position (matches active sort key)
    val currentIndex by remember {
        derivedStateOf {
            val songIndex = listState.firstVisibleItemIndex - 1
            if (songIndex in songs.indices) {
                val letter = getLetterForIndex(getAlphabetIndex(sortKeyOf(songs[songIndex], sortType)))
                availableLetters.indexOf(letter)
            } else {
                -1
            }
        }
    }

    LaunchedEffect(currentIndex) {
        currentAlphabetIndex = currentIndex
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = AppSpacing.xs,
                    end = if (alphabetEnabled) AppSpacing.xs + 32.dp else AppSpacing.xs,
                    top = AppSpacing.xxxs,
                    bottom = AppSpacing.xxxs
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxxs)
            ) {
                // Shuffle-all header (only outside selection mode)
                if (!isSelectionMode) {
                    item(key = "header") {
                        ElevatedCard(
                            onClick = { onPlayShuffled(songs) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = AppSpacing.xxxs, start = AppSpacing.xs, end = AppSpacing.xs),
                            shape = MuseShapeTokens.Card,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MuseDimens.CardSpacing, vertical = MuseDimens.SpacingLarge),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(MuseDimens.IconSizeNormal),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(AppSpacing.xs))
                                Text(
                                    stringResource(R.string.library_shuffle_all_count, songs.size),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    val isSelected = selectedSongIds.contains(song.id)

                    if (isSelectionMode) {
                        SongListItem(
                            song = song,
                            isPlaying = currentSongId == song.id,
                            onClick = { onToggleSelection(song.id) },
                            onLongClick = { onToggleSelection(song.id) },
                            menuItems = emptyList(),
                            artworkSize = MuseDimens.ArtworkSizeSmall,
                            selectionMode = true,
                            isSelected = isSelected,
                            onSelectionChanged = { onToggleSelection(song.id) }
                        )
                    } else {
                        SongListItem(
                            song = song,
                            isPlaying = currentSongId == song.id,
                            onClick = { onPlaySongs(songs, index) },
                            onLongClick = {
                                onEnterSelectionMode()
                                onToggleSelection(song.id)
                            },
                            menuItems = listOf(
                                SongMenuItem(stringResource(R.string.action_share_song), onClick = { onShareSong(song) }),
                                SongMenuItem(stringResource(R.string.player_search_metadata), onClick = { onSearchMetadata(song) }),
                                SongMenuItem(stringResource(R.string.player_edit_metadata), onClick = { onEditMetadata(song) }),
                                SongMenuItem(stringResource(R.string.add_to_playlist), onClick = { onAddToPlaylist(song) }),
                                SongMenuItem(stringResource(R.string.action_delete), isDestructive = true, onClick = { onDeleteSong(song) })
                            )
                        )
                    }
                }
                item { Spacer(Modifier.height(MuseDimens.MiniPlayerClearance)) }
            }

            // Alphabet Index Bar (right side) — only existing letters, adapts to sort order
            if (alphabetEnabled) {
                AlphabetIndexBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    letters = availableLetters,
                    onLetterSelected = { index ->
                        val letter = availableLetters.getOrNull(index) ?: return@AlphabetIndexBar
                        val targetSongIndex = sectionIndexMap[letter]
                        if (targetSongIndex != null) {
                            // Account for the shuffle-all header item when not in selection mode
                            val headerOffset = if (isSelectionMode) 0 else 1
                            scope.launch { listState.animateScrollToItem(targetSongIndex + headerOffset) }
                        }
                    },
                    currentIndex = currentAlphabetIndex,
                    showCurrentIndicator = true
                )
            }
        }

        // Pinned bottom action bar while in selection mode (no scroll-to-top jump)
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            SelectionActionBar(
                selectedCount = selectedSongIds.size,
                totalCount = songs.size,
                onSelectAll = onSelectAll,
                onExitSelectionMode = onExitSelectionMode,
                onAddToPlaylist = onAddSelectedToPlaylist
            )
        }
    }
}

/**
 * Build a mapping from the first-letter (in [ALPHABET_INDEX] space) to the first
 * LazyList index of a song starting with that letter. `keyOf` selects the field
 * the list is currently sorted by (title / artist / album).
 */
private fun buildSectionIndex(songs: List<Song>, keyOf: (Song) -> String): Map<Char, Int> {
    val map = mutableMapOf<Char, Int>()
    for ((index, song) in songs.withIndex()) {
        val letter = getLetterForIndex(getAlphabetIndex(keyOf(song)))
        if (!map.containsKey(letter)) {
            // +1 to account for the header item at LazyList index 0
            map[letter] = index + 1
        }
    }
    return map
}

/**
 * Field used to group the alphabet index, matching the active sort order so the
 * quick-scroll bar lines up with what the user actually sees.
 */
private fun sortKeyOf(song: Song, sortType: SortType): String {
    val raw = when (sortType) {
        SortType.ARTIST_ASC, SortType.ARTIST_DESC -> song.artist
        SortType.ALBUM_ASC, SortType.ALBUM_DESC -> song.album
        else -> song.title
    }
    // 中文按拼音首字母参与分组定位，与列表排序保持一致
    return Pinyin.sortKey(raw)
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onExitSelectionMode) {
                Text(stringResource(R.string.cancel))
            }

            Text(
                text = "$selectedCount / $totalCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onSelectAll,
                    enabled = selectedCount < totalCount
                ) {
                    Text(stringResource(R.string.select_all))
                }
                FilledTonalButton(
                    onClick = onAddToPlaylist,
                    enabled = selectedCount > 0
                ) {
                    Text(stringResource(R.string.add_to_playlist))
                }
            }
        }
    }
}