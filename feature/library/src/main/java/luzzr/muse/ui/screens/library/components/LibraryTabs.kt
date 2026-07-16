package luzzr.muse.ui.screens.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import luzzr.muse.domain.model.SortType
import luzzr.muse.feature.library.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.theme.AppSpacing

@Composable
fun LibraryTabs(selectedTab: Int, onTabSelected: (Int) -> Unit, currentSortType: SortType, modifier: Modifier = Modifier) {
    val subTabs = listOf(
        stringResource(R.string.library_songs),
        stringResource(R.string.library_albums),
        stringResource(R.string.library_artists)
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        subTabs.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = subTabs.size),
                onClick = { onTabSelected(index) },
                selected = selectedTab == index
            ) {
                Text(label)
            }
        }
    }

    AnimatedVisibility(
        visible = selectedTab == 0,
        enter = fadeIn(tween(MotionDuration.medium1)) +
            slideInVertically(tween(MotionDuration.medium1)) { -it / 2 },
        exit = fadeOut(tween(MotionDuration.short)) +
            slideOutVertically(tween(MotionDuration.short)) { -it / 2 }
    ) {
        AnimatedContent(
            targetState = currentSortType,
            transitionSpec = {
                (fadeIn(tween(MotionDuration.medium1)) + slideInVertically { it / 3 })
                    .togetherWith(fadeOut(tween(MotionDuration.short)))
            },
            label = "sort_label"
        ) { sort ->
            val sortLabel = stringResource(
                when (sort) {
                    SortType.TITLE_ASC -> R.string.sort_title_asc
                    SortType.TITLE_DESC -> R.string.sort_title_desc
                    SortType.ARTIST_ASC -> R.string.sort_artist_asc
                    SortType.ARTIST_DESC -> R.string.sort_artist_desc
                    SortType.ALBUM_ASC -> R.string.sort_album_asc
                    SortType.ALBUM_DESC -> R.string.sort_album_desc
                    SortType.DURATION_ASC -> R.string.sort_duration_asc
                    SortType.DURATION_DESC -> R.string.sort_duration_desc
                    SortType.DATE_ADDED_DESC -> R.string.sort_date_desc
                    SortType.DATE_ADDED_ASC -> R.string.sort_date_asc
                }
            )
            Text(
                text = sortLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = AppSpacing.md, bottom = AppSpacing.xxs, top = AppSpacing.xxs)
            )
        }
    }
}
