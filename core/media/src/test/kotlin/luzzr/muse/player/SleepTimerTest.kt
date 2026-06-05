package luzzr.muse.player

import luzzr.muse.media.SleepTimerMode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Unit tests for [SleepTimer] — covers mode activation, countdown,
 * track-remaining update, and cancellation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var timer: SleepTimer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        timer = SleepTimer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Mode activation --------------------------------------------

    @Test
    fun `start with OFF mode does nothing`() {
        timer.start(SleepTimerMode.OFF)
        assertNull(timer.remainingMs.value)
        assertNull(timer.activeMode.value)
        assertFalse(timer.isActive)
    }

    @Test
    fun `start with MIN_15 sets remaining to 15 minutes`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_15)
        assertEquals(SleepTimerMode.MIN_15, timer.activeMode.value)
        assertEquals(15 * 60 * 1000L, timer.remainingMs.value)
        assertTrue(timer.isActive)
    }

    @Test
    fun `start with MIN_30 sets remaining to 30 minutes`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_30)
        assertEquals(30 * 60 * 1000L, timer.remainingMs.value)
    }

    @Test
    fun `start with MIN_60 sets remaining to 60 minutes`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_60)
        assertEquals(60 * 60 * 1000L, timer.remainingMs.value)
    }

    @Test
    fun `start with END_OF_TRACK requires trackRemaining`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.END_OF_TRACK, currentTrackRemainingMs = 120_000L)
        assertEquals(SleepTimerMode.END_OF_TRACK, timer.activeMode.value)
        assertEquals(120_000L, timer.remainingMs.value)
    }

    @Test
    fun `start with END_OF_TRACK without trackRemaining does nothing`() {
        timer.start(SleepTimerMode.END_OF_TRACK, currentTrackRemainingMs = null)
        assertFalse(timer.isActive)
    }

    // -- Stop -------------------------------------------------------

    @Test
    fun `stop clears timer state`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_15)
        assertTrue(timer.isActive)

        timer.stop()
        assertFalse(timer.isActive)
        assertNull(timer.remainingMs.value)
        assertNull(timer.activeMode.value)
    }

    // -- Countdown --------------------------------------------------

    @Test
    fun `countdown decreases remaining over time`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_15)
        advanceTimeBy(3_001L) // 3 seconds + 1ms to ensure execution
        assertEquals(15 * 60 * 1000L - 3_000L, timer.remainingMs.value)
    }

    @Test
    fun `timer invokes callback when countdown reaches zero`() = runTest(testDispatcher) {
        var callbackInvoked = false
        timer.onTimerElapsed = { callbackInvoked = true }

        // Start a very short timer (END_OF_TRACK with 2 seconds)
        timer.start(SleepTimerMode.END_OF_TRACK, currentTrackRemainingMs = 2_000L)
        advanceTimeBy(3_001L) // Advance past the 2-second countdown

        assertTrue(callbackInvoked)
        assertFalse(timer.isActive)
    }

    // -- Track remaining update -------------------------------------

    @Test
    fun `updateTrackRemaining updates value for END_OF_TRACK mode`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.END_OF_TRACK, currentTrackRemainingMs = 120_000L)
        timer.updateTrackRemaining(90_000L)
        assertEquals(90_000L, timer.remainingMs.value)
    }

    @Test
    fun `updateTrackRemaining is ignored for non-END_OF_TRACK mode`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_15)
        val before = timer.remainingMs.value
        timer.updateTrackRemaining(5_000L)
        // Value should not be set to 5000; the countdown might have changed `before` from its start value
        assertNotEquals(5_000L, timer.remainingMs.value)
        // 'before' is captured so the countdown is exercised across the update call
        assertTrue(before!! >= 0L)
    }

    // -- Restart ----------------------------------------------------

    @Test
    fun `starting a new mode stops the previous timer`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_15)
        advanceTimeBy(5_001L)

        timer.start(SleepTimerMode.MIN_30)
        assertEquals(30 * 60 * 1000L, timer.remainingMs.value)
        assertEquals(SleepTimerMode.MIN_30, timer.activeMode.value)
    }

    // -- formatRemaining --------------------------------------------

    @Test
    fun `formatRemaining returns empty string when no timer`() {
        assertEquals("", timer.formatRemaining())
    }

    @Test
    fun `formatRemaining formats MM colon SS`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.MIN_15)
        advanceTimeBy(60_001L) // 1 minute elapsed → 14 minutes remaining
        val formatted = timer.formatRemaining()
        assertEquals("14:00", formatted)
    }

    @Test
    fun `formatRemaining pads seconds with zero`() = runTest(testDispatcher) {
        timer.start(SleepTimerMode.END_OF_TRACK, currentTrackRemainingMs = 65_000L)
        // No advance — 65 seconds = 1:05
        assertEquals("1:05", timer.formatRemaining())
    }
}
