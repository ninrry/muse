package luzzr.muse.ui.state

import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.ui.R

fun OperationResult.Failure.toUiText(): UiText {
    val resource = when (error) {
        OperationError.PERMISSION_DENIED -> R.string.error_permission_denied
        OperationError.UNSUPPORTED_FILE -> R.string.error_unsupported_file
        OperationError.NETWORK -> R.string.error_network
        OperationError.IO -> R.string.error_io
        OperationError.NOT_FOUND -> R.string.error_not_found
        OperationError.DATABASE -> R.string.error_database
        OperationError.CONFLICT -> R.string.error_conflict
        OperationError.UNKNOWN -> R.string.error_unknown
    }
    return UiText.Resource(resource)
}
