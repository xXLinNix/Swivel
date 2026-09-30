package io.github.xxlinnix.swivel.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkWatchdogTest {
    @Test
    fun withNoReportItResendsThenSwitchesThenGivesUp() {
        val dog = LinkWatchdog(connectedAtMs = 0)
        assertEquals(WatchdogAction.NONE, dog.onTick(500, null))
        assertEquals(WatchdogAction.RESEND, dog.onTick(1_000, null))
        assertEquals(WatchdogAction.NONE, dog.onTick(1_250, null))
        assertEquals(WatchdogAction.SWITCH_GENERATION, dog.onTick(2_500, null))
        assertEquals(WatchdogAction.NONE, dog.onTick(3_000, null))
        assertEquals(WatchdogAction.FAIL_NO_ANSWER, dog.onTick(6_000, null))
    }

    @Test
    fun aQuietControllerIsPolledEveryTwoSeconds() {
        val dog = LinkWatchdog(connectedAtMs = 0)
        assertEquals(WatchdogAction.NONE, dog.onTick(1_500, lastReportAtMs = 0))
        assertEquals(WatchdogAction.POLL, dog.onTick(2_000, lastReportAtMs = 0))
        assertEquals(WatchdogAction.NONE, dog.onTick(3_000, lastReportAtMs = 0))
        // The poll was answered at 2.1 s, so the next is due at 4.1 s.
        assertEquals(WatchdogAction.NONE, dog.onTick(4_000, lastReportAtMs = 2_100))
        assertEquals(WatchdogAction.POLL, dog.onTick(4_250, lastReportAtMs = 2_100))
    }

    @Test
    fun silenceAfterAnsweringIsADeadLink() {
        val dog = LinkWatchdog(connectedAtMs = 0)
        assertEquals(WatchdogAction.FAIL_SILENT, dog.onTick(7_000, lastReportAtMs = 1_000))
    }

    @Test
    fun reconnectDelaysDoubleUpToThirtySeconds() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..6).map { ReconnectPolicy.delayMs(it) })
    }

    @Test
    fun reconnectingStopsAfterFiveMinutes() {
        assertFalse(ReconnectPolicy.shouldGiveUp(4 * 60_000L))
        assertTrue(ReconnectPolicy.shouldGiveUp(5 * 60_000L))
    }
}
