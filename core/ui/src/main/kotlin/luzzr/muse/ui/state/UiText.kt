package luzzr.muse.ui.state

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Resource(
        @StringRes val resId: Int,
        val formatArgs: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(resId, *formatArgs.toTypedArray())
    is UiText.Dynamic -> value
}

fun UiText.asString(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(resId, *formatArgs.toTypedArray())
    is UiText.Dynamic -> value
}
