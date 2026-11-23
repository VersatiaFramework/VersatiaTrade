package dev.versatia.trade.offer

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/** The two sides of a trade: [FIRST] sent the request, [SECOND] accepted it. */
internal enum class Side {
    FIRST,
    SECOND,
    ;

    val other get() = if (this == FIRST) SECOND else FIRST
}

/**
 * Who of the two accepted the offers as they stand, and the countdown that starts once both did:
 * any change of either offer takes both acceptances and the countdown back, so nobody can swap an
 * item in the last moment. Pure bookkeeping, timed by [time]; server thread only. See docs/trade.md.
 */
internal class Acceptance(private val time: TimeSource.WithComparableMarks, private val countdown: Duration) {
    private val accepted = HashSet<Side>()
    private var endsAt: ComparableTimeMark? = null

    fun isAccepted(side: Side) = side in accepted

    /** Whether both accepted and the countdown runs. */
    val counting get() = endsAt != null

    /** An offer changed: both acceptances and the countdown are gone. True when there was one to lose. */
    fun reset() = (accepted.isNotEmpty() || endsAt != null).also {
        accepted.clear()
        endsAt = null
    }

    /** [side] accepts, or takes its acceptance back when it had accepted; both accepting starts the countdown. */
    fun toggle(side: Side) {
        if (!accepted.add(side)) {
            accepted.remove(side)
            endsAt = null
        } else if (accepted.size == 2) {
            endsAt = time.markNow() + countdown
        }
    }

    /** Whether the countdown ran out: the exchange is due. */
    fun isDue() = endsAt?.hasPassedNow() == true

    /** The whole seconds the countdown has left, rounded up; null while it does not run. */
    fun secondsLeft() = endsAt?.let { mark -> (-mark.elapsedNow()).coerceAtLeast(Duration.ZERO).inWholeMilliseconds.let { ((it + 999) / 1000).toInt() } }
}
