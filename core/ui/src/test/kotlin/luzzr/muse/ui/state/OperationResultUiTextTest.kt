package luzzr.muse.ui.state

import luzzr.muse.core.result.OperationError
import luzzr.muse.core.result.OperationResult
import luzzr.muse.ui.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationResultUiTextTest {

    @Test
    fun `every operation error maps to a user facing resource`() {
        val expected = mapOf(
            OperationError.PERMISSION_DENIED to R.string.error_permission_denied,
            OperationError.UNSUPPORTED_FILE to R.string.error_unsupported_file,
            OperationError.NETWORK to R.string.error_network,
            OperationError.IO to R.string.error_io,
            OperationError.NOT_FOUND to R.string.error_not_found,
            OperationError.DATABASE to R.string.error_database,
            OperationError.CONFLICT to R.string.error_conflict,
            OperationError.UNKNOWN to R.string.error_unknown
        )

        val actual = OperationError.entries.associateWith { error ->
            val uiText = OperationResult.Failure(error).toUiText() as UiText.Resource
            uiText.resId
        }

        assertEquals(expected, actual)
    }
}
