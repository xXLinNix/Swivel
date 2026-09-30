package io.github.xxlinnix.swivel.core.protocol

/** What the Mode A link should do on a watchdog tick. */
enum class WatchdogAction {
    NONE,

    /** No report yet: send the poll and stream commands again. The controller sometimes ignores the first. */
    RESEND,

    /** Still no report: the name guessed the wrong generation, so try the other one. */
    SWITCH_GENERATION,

    /** Quiet for a while: poll, which a live controller always answers. */
    POLL,

    /** Connected, but nothing ever answered in the MOGA protocol. */
    FAIL_NO_ANSWER,

    /** It answered before but has gone silent: the link is dead (usually the controller slept). */
    FAIL_SILENT,
}

/**
 * Decides when a Mode A link needs a nudge or is dead. In stream mode the controller
 * only reports changes, so silence alone is normal; a poll proves it is still there.
 * Timings follow MogaSerial's: re-poll after 2 s of quiet. Time is passed in, so this
 * is tested without waiting.
 */
class LinkWatchdog(private val connectedAtMs: Long) {
    private var resent = false
    private var switched = false
    private var lastPollAtMs: Long? = null

    fun onTick(nowMs: Long, lastReportAtMs: Long?): WatchdogAction {
        if (lastReportAtMs == null) {
            val waited = nowMs - connectedAtMs
            return when {
                waited >= NO_ANSWER_MS -> WatchdogAction.FAIL_NO_ANSWER
                waited >= SWITCH_AFTER_MS && !switched -> { switched = true; WatchdogAction.SWITCH_GENERATION }
                waited >= RESEND_AFTER_MS && !resent -> { resent = true; WatchdogAction.RESEND }
                else -> WatchdogAction.NONE
            }
        }
        val quiet = nowMs - lastReportAtMs
        val lastPoll = lastPollAtMs
        val pollDue = lastPoll == null || nowMs - lastPoll >= POLL_AFTER_MS
        return when {
            quiet >= SILENT_MS -> WatchdogAction.FAIL_SILENT
            quiet >= POLL_AFTER_MS && pollDue -> {
                lastPollAtMs = nowMs
                WatchdogAction.POLL
            }
            else -> WatchdogAction.NONE
        }
    }

    companion object {
        const val TICK_MS = 250L
        const val RESEND_AFTER_MS = 1_000L
        const val SWITCH_AFTER_MS = 2_500L
        const val NO_ANSWER_MS = 6_000L
        const val POLL_AFTER_MS = 2_000L
        const val SILENT_MS = 6_000L
    }
}

/**
 * How long to wait between reconnection attempts after the link drops, and when to stop.
 * A sleeping controller cannot be woken from the phone, so trying forever only costs
 * battery: after [WINDOW_MS] the link stops and waits for the controller to reconnect
 * itself, or for the user.
 */
object ReconnectPolicy {
    const val WINDOW_MS = 5 * 60_000L
    private const val FIRST_DELAY_MS = 2_000L
    private const val MAX_DELAY_MS = 30_000L

    /** 2, 4, 8, 16, then 30 seconds. [attempt] counts from 1. */
    fun delayMs(attempt: Int): Long =
        minOf(MAX_DELAY_MS, FIRST_DELAY_MS shl (attempt - 1).coerceIn(0, 4))

    fun shouldGiveUp(sinceFirstFailureMs: Long): Boolean = sinceFirstFailureMs >= WINDOW_MS
}
