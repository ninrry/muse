package luzzr.muse.ui.screens.library.tabs

import androidx.activity.compose.BackHandler
import luzzr.muse.ui.theme.MuseIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import luzzr.muse.domain.model.Song
import luzzr.muse.domain.model.SortType
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.ALPHABET_INDEX
import luzzr.muse.ui.components.AlphabetIndexBar
import luzzr.muse.ui.components.MusePrimaryAction
import luzzr.muse.ui.components.MuseSelectionDock
import luzzr.muse.ui.components.SongListItem
import luzzr.muse.ui.components.SongMenuItem
import luzzr.muse.ui.components.getAlphabetIndex
import luzzr.muse.ui.components.getLetterForIndex
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.animation.MotionList
import luzzr.muse.ui.screens.library.Pinyin
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.theme.MuseShapeTokens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListTab(
    songs: List<Song>,
    currentSongId: Long?,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onShareSong: (Song) -> Unit,
    onSearchMetadata: (Song) -> Unit,
    onEditMetadata: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<Long> = emptySet(),
    onEnterSelectionMode: () -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    onAddSelectedToPlaylist: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onFetchLyricsForSelected: () -> Unit = {},
    lyricsFetchProgress: Pair<Int, Int>? = null,
    showAlphabetIndex: Boolean = true,
    sortType: SortType = SortType.TITLE_ASC
) {
    BackHandler(enabled = isSelectionMode) {
        onExitSelectionMode()
    }

    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.library_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val indexMode = remember(sortType) { IndexMode.from(sortType) }
    val headerOffset = 0

    // Pinyin conversion is comparatively expensive. Cache each song's section key
    // so scrolling only reads a Char instead of converting the visible row again.
    val sectionKeys = remember(songs, sortType) {
        songs.map { sectionKeyOf(it, sortType) }
    }

    // songIndex -> first LazyList item index of that section key
    val sectionIndexMap = remember(sectionKeys, headerOffset) {
        buildSectionIndex(sectionKeys, headerOffset)
    }

    val letters = remember(sectionIndexMap, indexMode, sortType) {
        when (indexMode) {
            IndexMode.Alphabet -> {
                val base = ALPHABET_INDEX.filter { it in sectionIndexMap.keys }
                if (sortType == SortType.TITLE_DESC ||
                    sortType == SortType.ARTIST_DESC ||
                    sortType == SortType.ALBUM_DESC
                ) {
                    base.asReversed()
                } else {
                    base
                }
            }
            IndexMode.Duration -> {
                val base = DURATION_BUCKETS.filter { it.letter in sectionIndexMap.keys }.map { it.letter }
                if (sortType == SortType.DURATION_DESC) base.asReversed() else base
            }
            IndexMode.Date -> {
                // DATE_ADDED_DESC = newest first → T,W,M,Y,O; ASC reverses
                val base = DATE_BUCKETS.filter { it.letter in sectionIndexMap.keys }.map { it.letter }
                if (sortType == SortType.DATE_ADDED_ASC) base.asReversed() else base
            }
            IndexMode.None -> emptyList()
        }
    }

    val alphabetEnabled = showAlphabetIndex && !isSelectionMode && letters.isNotEmpty()
    val reduceMotion = luzzr.muse.ui.components.LocalReduceMotion.current

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentIndex by remember(songs, sectionKeys, letters, headerOffset) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val songIndex = (first - headerOffset).coerceAtLeast(0)
            if (songIndex in songs.indices && letters.isNotEmpty()) {
                val key = sectionKeys[songIndex]
                letters.indexOf(key).takeIf { it >= 0 } ?: -1
            } else {
                -1
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = AppSpacing.xs,
                    end = if (alphabetEnabled) AppSpacing.xs + 44.dp else AppSpacing.xs,
                    top = AppSpacing.xxxs,
                    bottom = 160.dp
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxxs)
            ) {
                val enableItemAnim = songs.size <= 80
                itemsIndexed(
                    songs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "song" }
                ) { index, song ->
                    val isSelected = selectedSongIds.contains(song.id)
                    Box(
                        modifier = if (enableItemAnim && !reduceMotion) {
                            Modifier.animateItem(
                                fadeInSpec = MotionList.fadeIn,
                                fadeOutSpec = MotionList.fadeOut,
                                placementSpec = MotionList.placement
                            )
                        } else {
                            Modifier
                        }
                    ) {
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
                                ),
                                artworkSize = MuseDimens.ArtworkSizeMedium
                            )
                        }
                    }
                }
            }

            if (alphabetEnabled) {
                AlphabetIndexBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp, top = AppSpacing.xs, bottom = 116.dp),
                    letters = letters,
                    onLetterSelected = { index ->
                        val letter = letters.getOrNull(index) ?: return@AlphabetIndexBar
                        val target = sectionIndexMap[letter] ?: return@AlphabetIndexBar
                        scope.launch {
                            listState.scrollToItem(target)
                        }
                    },
                    currentIndex = currentIndex,
                    availableIndices = letters.indices.toSet(),
                    showCurrentIndicator = true
                )
            }
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None
            else slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None
            else slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SelectionActionBar(
                selectedCount = selectedSongIds.size,
                totalCount = songs.size,
                onSelectAll = onSelectAll,
                onExitSelectionMode = onExitSelectionMode,
                onAddToPlaylist = onAddSelectedToPlaylist,
                onFetchLyrics = onFetchLyricsForSelected,
                lyricsFetchProgress = lyricsFetchProgress
            )
        }
    }
}

private enum class IndexMode {
    Alphabet, Duration, Date, None;

    companion object {
        fun from(sortType: SortType): IndexMode = when (sortType) {
            SortType.TITLE_ASC, SortType.TITLE_DESC,
            SortType.ARTIST_ASC, SortType.ARTIST_DESC,
            SortType.ALBUM_ASC, SortType.ALBUM_DESC -> Alphabet
            SortType.DURATION_ASC, SortType.DURATION_DESC -> Duration
            SortType.DATE_ADDED_ASC, SortType.DATE_ADDED_DESC -> Date
        }
    }
}

private data class Bucket(val letter: Char, val label: String)

/** Duration buckets: 1=<3m, 2=3–5, 3=5–10, 4=10+ */
private val DURATION_BUCKETS = listOf(
    Bucket('1', "<3"),
    Bucket('2', "3-5"),
    Bucket('3', "5-10"),
    Bucket('4', "10+")
)

/** Date buckets: T=today, W=week, M=month, Y=year, O=older */
private val DATE_BUCKETS = listOf(
    Bucket('T', "今"),
    Bucket('W', "周"),
    Bucket('M', "月"),
    Bucket('Y', "年"),
    Bucket('O', "更早")
)

/**
 * Section key as a single Char for the side index.
 * Alphabet: A–Z / # via pinyin. Duration/Date: bucket letters.
 */
private fun sectionKeyOf(song: Song, sortType: SortType): Char {
    return when (sortType) {
        SortType.ARTIST_ASC, SortType.ARTIST_DESC ->
            getLetterForIndex(getAlphabetIndex(Pinyin.sortKey(song.artist)))
        SortType.ALBUM_ASC, SortType.ALBUM_DESC ->
            getLetterForIndex(getAlphabetIndex(Pinyin.sortKey(song.album)))
        SortType.TITLE_ASC, SortType.TITLE_DESC ->
            getLetterForIndex(getAlphabetIndex(Pinyin.sortKey(song.title)))
        SortType.DURATION_ASC, SortType.DURATION_DESC -> durationBucket(song.duration)
        SortType.DATE_ADDED_ASC, SortType.DATE_ADDED_DESC -> dateBucket(song.dateAdded)
    }
}

private fun durationBucket(durationMs: Long): Char {
    val min = durationMs / 60_000f
    return when {
        min < 3f -> '1'
        min < 5f -> '2'
        min < 10f -> '3'
        else -> '4'
    }
}

private fun dateBucket(dateAdded: Long): Char {
    val now = System.currentTimeMillis()
    val age = now - dateAdded
    val day = 86_400_000L
    return when {
        age < day -> 'T'
        age < 7 * day -> 'W'
        age < 30 * day -> 'M'
        age < 365 * day -> 'Y'
        else -> 'O'
    }
}

/**
 * Map section letter → absolute LazyList index (header already included in [headerOffset]).
 */
private fun buildSectionIndex(
    sectionKeys: List<Char>,
    headerOffset: Int
): Map<Char, Int> {
    val map = mutableMapOf<Char, Int>()
    for ((index, letter) in sectionKeys.withIndex()) {
        if (!map.containsKey(letter)) {
            map[letter] = index + headerOffset
        }
    }
    return map
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFetchLyrics: () -> Unit = {},
    lyricsFetchProgress: Pair<Int, Int>? = null
) {
    val busy = lyricsFetchProgress != null
    val progressLabel = lyricsFetchProgress?.let { (done, total) ->
        stringResource(R.string.lyrics_batch_progress, done, total)
    }
    MuseSelectionDock(
        selectedLabel = stringResource(R.string.selection_count, selectedCount, totalCount),
        onCancel = onExitSelectionMode,
        cancelLabel = stringResource(R.string.cancel),
        onSelectAll = onSelectAll,
        selectAllLabel = stringResource(R.string.select_all),
        selectAllEnabled = selectedCount < totalCount && !busy,
        progress = lyricsFetchProgress,
        progressLabel = progressLabel,
        primaryActions = listOf(
            MusePrimaryAction(
                label = stringResource(R.string.lyrics_batch_fetch),
                icon = MuseIcons.MusicNote,
                onClick = onFetchLyrics,
                enabled = selectedCount > 0 && !busy
            ),
            MusePrimaryAction(
                label = stringResource(R.string.add_to_playlist),
                icon = MuseIcons.Queue,
                onClick = onAddToPlaylist,
                enabled = selectedCount > 0 && !busy
            )
        )
    )
}
