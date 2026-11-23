package dev.versatia.trade.offer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class AcceptanceTest {
    private val time = TestTimeSource()
    private val acceptance = Acceptance(time, 3.seconds)

    @Test
    fun `the countdown starts only once both accepted, and the exchange is due when it ends`() {
        acceptance.toggle(Side.FIRST)
        assertTrue(acceptance.isAccepted(Side.FIRST))
        assertFalse(acceptance.counting)
        acceptance.toggle(Side.SECOND)
        assertTrue(acceptance.counting)
        assertEquals(3, acceptance.secondsLeft())
        time += 2.seconds + 1.milliseconds
        assertEquals(1, acceptance.secondsLeft())
        assertFalse(acceptance.isDue())
        time += 999.milliseconds
        assertTrue(acceptance.isDue())
    }

    @Test
    fun `any change takes both acceptances and the countdown back`() {
        acceptance.toggle(Side.FIRST)
        acceptance.toggle(Side.SECOND)
        assertTrue(acceptance.reset())
        assertFalse(acceptance.isAccepted(Side.FIRST))
        assertFalse(acceptance.isAccepted(Side.SECOND))
        assertFalse(acceptance.counting)
        assertNull(acceptance.secondsLeft())
        time += 10.seconds
        assertFalse(acceptance.isDue())
        assertFalse(acceptance.reset())
    }

    @Test
    fun `taking an acceptance back stops the countdown`() {
        acceptance.toggle(Side.FIRST)
        acceptance.toggle(Side.SECOND)
        acceptance.toggle(Side.FIRST)
        assertFalse(acceptance.isAccepted(Side.FIRST))
        assertTrue(acceptance.isAccepted(Side.SECOND))
        assertFalse(acceptance.counting)
        acceptance.toggle(Side.FIRST)
        assertEquals(3, acceptance.secondsLeft())
    }

    @Test
    fun `without a countdown the exchange is due at once`() {
        val immediate = Acceptance(time, Duration.ZERO)
        immediate.toggle(Side.SECOND)
        immediate.toggle(Side.FIRST)
        assertTrue(immediate.isDue())
    }
}
