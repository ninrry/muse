package luzzr.muse.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpEngineTest {

    @Test
    fun `safeCall never swallows coroutine cancellation`() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                safeCall<Unit>("test", "cancel") {
                    throw CancellationException("cancelled")
                }
            }
        }
    }
}
