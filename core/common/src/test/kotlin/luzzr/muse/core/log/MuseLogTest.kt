package luzzr.muse.core.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class MuseLogTest {
    @After
    fun tearDown() {
        MuseLog.clear()
    }

    @Test
    fun `installed sink receives typed log event`() {
        var event: List<Any?> = emptyList()
        MuseLog.install { level, tag, message, throwable ->
            event = listOf(level, tag, message, throwable)
        }

        MuseLog.w("Scanner", "Permission denied")

        assertEquals(
            listOf(MuseLogLevel.WARN, "Scanner", "Permission denied", null),
            event
        )
    }
}
