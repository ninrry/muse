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
import luzzr.muse.R
import luzzr.muse.data.model.SortType
import luzzr.muse.ui.theme.AppSpacing

@Composable
fun LibraryTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    currentSortType: SortType,
    modifier: Modifier = Modifier
) {
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
        Text(
            text = currentSortType.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = AppSpacing.md, bottom = AppSpacing.xxs)
        )
    }
}
