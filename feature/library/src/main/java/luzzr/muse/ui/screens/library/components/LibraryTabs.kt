package luzzr.muse.ui.screens.library.components

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
import luzzr.muse.ui.theme.AppSpacing

@Composable
fun LibraryTabs(selectedTab: Int, onTabSelected: (Int) -> Unit, currentSortType: SortType, modifier: Modifier = Modifier) {
    val subTabs = listOf(
        stringResource(R.string.library_songs),
        stringResource(R.string.library_albums),
        stringResource(R.string.library_artists)
    )

    SingleChoiceSegmentedButtonRow(
        modifier = modifier
    ) {
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

    if (selectedTab == 0) {
        val sortLabel = stringResource(
            when (currentSortType) {
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
            modifier = Modifier.padding(start = AppSpacing.md, bottom = AppSpacing.xxs)
        )
    }
}
