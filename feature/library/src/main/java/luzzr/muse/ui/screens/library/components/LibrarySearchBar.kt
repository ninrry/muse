package luzzr.muse.ui.screens.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import luzzr.muse.feature.library.R
import luzzr.muse.ui.animation.MotionDuration
import luzzr.muse.ui.theme.AppSpacing
import luzzr.muse.ui.theme.MuseDimens
import luzzr.muse.ui.components.LocalReduceMotion

@Composable
fun LibrarySearchBar(searchQuery: String, onSearchQueryChange: (String) -> Unit, showSearch: Boolean = true, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val enterDuration = if (reduceMotion) 0 else MotionDuration.medium1
    val exitDuration = if (reduceMotion) 0 else MotionDuration.short
    AnimatedVisibility(
        visible = showSearch,
        enter = fadeIn(animationSpec = tween(enterDuration)),
        exit = fadeOut(animationSpec = tween(exitDuration))
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = LocalContentColor.current
                        )
                    }
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = MuseDimens.TouchTarget)
                .padding(horizontal = AppSpacing.md),
            shape = RoundedCornerShape(MuseDimens.CardCornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
