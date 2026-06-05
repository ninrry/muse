package luzzr.muse.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import luzzr.muse.feature.library.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens

@Composable
fun LibrarySearchBar(searchQuery: String, onSearchQueryChange: (String) -> Unit, showSearch: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = showSearch,
        enter = expandVertically(animationSpec = tween(MotionDuration.medium1)) +
            fadeIn(animationSpec = tween(MotionDuration.medium1)),
        exit = shrinkVertically(animationSpec = tween(MotionDuration.short)) +
            fadeOut(animationSpec = tween(MotionDuration.short))
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
            shape = RoundedCornerShape(MuseDimens.CardCornerRadius)
        )
    }
}
