package luzzr.muse.ui.screens.library.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import luzzr.muse.domain.model.SortType
import luzzr.muse.feature.library.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.components.LocalReduceMotion
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseIcons

@Composable
fun LibraryTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    currentSortType: SortType,
    onSortChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subTabs = listOf(
        stringResource(R.string.library_songs),
        stringResource(R.string.library_albums),
        stringResource(R.string.library_artists)
    )
    val reduceMotion = LocalReduceMotion.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = luzzr.muse.ui.theme.MuseShapeTokens.Pill,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            subTabs.forEachIndexed { index, label ->
                Surface(
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = luzzr.muse.ui.theme.MuseShapeTokens.Pill,
                    color = if (selectedTab == index) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    }
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal,
                            color = if (selectedTab == index) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = selectedTab == 0,
        enter = if (reduceMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(MotionDuration.medium1)) +
                slideInVertically(tween(MotionDuration.medium1)) { -it / 2 }
        },
        exit = if (reduceMotion) {
            ExitTransition.None
        } else {
            fadeOut(tween(MotionDuration.short)) +
                slideOutVertically(tween(MotionDuration.short)) { -it / 2 }
        }
    ) {
        AnimatedContent(
            targetState = currentSortType,
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (fadeIn(tween(MotionDuration.medium1)) + slideInVertically { it / 3 })
                        .togetherWith(fadeOut(tween(MotionDuration.short)))
                }
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
            Surface(
                onClick = onSortChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xs),
                shape = luzzr.muse.ui.theme.MuseShapeTokens.Item,
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        MuseIcons.Sort,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "排序：$sortLabel",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = AppSpacing.xxs)
                    )
                }
            }
        }
    }
}
