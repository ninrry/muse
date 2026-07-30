package luzzr.muse.ui.screens.library.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import luzzr.muse.ui.theme.MuseIcons
import luzzr.muse.ui.theme.MuseShapeTokens
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import luzzr.muse.domain.model.Artist
import luzzr.muse.feature.library.R
import luzzr.muse.ui.components.ALPHABET_INDEX
import luzzr.muse.ui.components.AlphabetIndexBar
import luzzr.muse.ui.components.getAlphabetIndex
import luzzr.muse.ui.components.getLetterForIndex
import luzzr.muse.ui.haptic.pressScale
import luzzr.muse.ui.screens.library.Pinyin
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
fun ArtistListTab(artists: List<Artist>, onArtistClick: (Artist) -> Unit) {
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.library_artists_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sorted = remember(artists) {
        artists.sortedWith(compareBy({ Pinyin.sortKey(it.name) }, { it.name }))
    }
    val sectionIndexMap = remember(sorted) {
        val map = mutableMapOf<Char, Int>()
        sorted.forEachIndexed { index, artist ->
            val letter = getLetterForIndex(getAlphabetIndex(Pinyin.sortKey(artist.name)))
            if (letter !in map) map[letter] = index
        }
        map
    }
    val letters = remember(sectionIndexMap) {
        ALPHABET_INDEX.filter { it in sectionIndexMap.keys }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentIndex by remember(sorted, letters) {
        derivedStateOf {
            val i = listState.firstVisibleItemIndex
            if (i in sorted.indices && letters.isNotEmpty()) {
                val letter = getLetterForIndex(getAlphabetIndex(Pinyin.sortKey(sorted[i].name)))
                letters.indexOf(letter)
            } else -1
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = MuseDimens.ScreenPaddingH,
                end = MuseDimens.ScreenPaddingH + if (letters.isNotEmpty()) 44.dp else 0.dp,
                top = AppSpacing.md,
                bottom = 160.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            itemsIndexed(sorted, key = { _, artist -> artist.name }) { _, artist ->
                val interaction = remember { MutableInteractionSource() }
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(MuseDimens.SmallCardCornerRadius)
                        )
                        .pressScale(interaction, 0.98f)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onArtistClick(artist) }
                        ),
                    shape = RoundedCornerShape(MuseDimens.SmallCardCornerRadius),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(AppSpacing.sm).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(AppSpacing.xxlg),
                            shape = MuseShapeTokens.Item,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    MuseIcons.Library,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(AppSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.library_artist_summary, artist.songCount, artist.albumCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (letters.isNotEmpty()) {
            AlphabetIndexBar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 2.dp, top = AppSpacing.xs, bottom = 116.dp),
                letters = letters,
                onLetterSelected = { index ->
                    val letter = letters.getOrNull(index) ?: return@AlphabetIndexBar
                    val target = sectionIndexMap[letter] ?: return@AlphabetIndexBar
                    scope.launch { listState.animateScrollToItem(target) }
                },
                currentIndex = currentIndex,
                showCurrentIndicator = true
            )
        }
    }
}
