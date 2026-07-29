package luzzr.muse.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationResultTest {

    @Test
    fun `success exposes value and success state`() {
        val result = OperationResult.Success("saved")

        assertTrue(result.isSuccess)
        assertEquals("saved", result.value)
    }

    @Test
    fun `failure preserves typed reason and message`() {
        val result = OperationResult.Failure(OperationError.PERMISSION_DENIED, "read only")

        assertFalse(result.isSuccess)
        assertEquals(OperationError.PERMISSION_DENIED, result.error)
        assertEquals("read only", result.message)
    }
}
