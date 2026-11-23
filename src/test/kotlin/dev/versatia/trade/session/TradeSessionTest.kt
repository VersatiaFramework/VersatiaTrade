package dev.versatia.trade.session

import dev.versatia.trade.ServerTest
import dev.versatia.trade.exchange.Exchange
import dev.versatia.trade.exchange.ExchangeOutcome
import dev.versatia.trade.offer.Side
import java.math.BigDecimal
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class TradeSessionTest : ServerTest() {
    private val session = TradeSession(TestTimeSource(), 3.seconds, capacity = 16)
    private val first = arrayOfNulls<ItemStack>(36)
    private val second = arrayOfNulls<ItemStack>(36)
    private val balances = mutableMapOf<Side, BigDecimal?>(Side.FIRST to BigDecimal(100), Side.SECOND to BigDecimal(100))

    private fun status() = session.status(first, second, balances::get)

    private fun fill(storage: Array<ItemStack?>, material: Material = Material.DIRT) = storage.indices.forEach { storage[it] = ItemStack.of(material, 64) }

    @Test
    fun `nothing offered cannot be accepted`() {
        assertEquals(TradeStatus.Empty, status())
        assertFalse(session.toggleAcceptance(Side.FIRST, status()))
    }

    @Test
    fun `a receiver without room is shown before anyone accepts, and accepting waits for room`() {
        fill(second)
        first[0] = ItemStack.of(Material.DIAMOND, 5)
        session.offerItem(Side.FIRST, first[0]!!, 5, first)
        assertEquals(TradeStatus.NoRoom(Side.SECOND, 5), status())
        assertFalse(session.toggleAcceptance(Side.FIRST, status()))
        session.offerItem(Side.SECOND, second[35]!!, 64, second)
        assertEquals(TradeStatus.Ready, status())
        assertTrue(session.toggleAcceptance(Side.FIRST, status()))
        assertTrue(session.toggleAcceptance(Side.SECOND, status()))
        assertTrue(session.acceptance.counting)
    }

    @Test
    fun `an inventory filling up after both accepted takes the acceptance back`() {
        first[0] = ItemStack.of(Material.DIAMOND, 5)
        session.offerItem(Side.FIRST, first[0]!!, 5, first)
        session.toggleAcceptance(Side.FIRST, status())
        session.toggleAcceptance(Side.SECOND, status())
        fill(second, Material.COBBLESTONE)
        assertEquals(TradeStatus.NoRoom(Side.SECOND, 5), status())
        assertFalse(session.acceptance.isAccepted(Side.FIRST))
        assertFalse(session.acceptance.counting)
    }

    @Test
    fun `any change of either offer takes both acceptances back`() {
        first[0] = ItemStack.of(Material.DIAMOND, 5)
        second[0] = ItemStack.of(Material.EMERALD, 5)
        session.offerItem(Side.FIRST, first[0]!!, 5, first)
        session.toggleAcceptance(Side.FIRST, status())
        session.toggleAcceptance(Side.SECOND, status())
        assertTrue(session.offerItem(Side.SECOND, second[0]!!, 1, second))
        assertFalse(session.acceptance.isAccepted(Side.FIRST))
        assertFalse(session.acceptance.isAccepted(Side.SECOND))
        session.toggleAcceptance(Side.FIRST, status())
        assertTrue(session.withdrawItem(Side.SECOND, second[0]!!, 1))
        assertFalse(session.acceptance.isAccepted(Side.FIRST))
        session.toggleAcceptance(Side.FIRST, status())
        assertTrue(session.offerMoney(Side.SECOND, BigDecimal.TEN))
        assertFalse(session.acceptance.isAccepted(Side.FIRST))
    }

    @Test
    fun `moving the offered items around the inventory changes nothing`() {
        first[0] = ItemStack.of(Material.DIAMOND, 10)
        session.offerItem(Side.FIRST, first[0]!!, 10, first)
        session.toggleAcceptance(Side.FIRST, status())
        first[20] = first[0]
        first[0] = null
        assertTrue(session.fitTo(first, second).values.all { it.isEmpty() })
        assertEquals(TradeStatus.Ready, status())
        assertTrue(session.acceptance.isAccepted(Side.FIRST))
    }

    @Test
    fun `items lost after offering shrink the offer and take the acceptance back`() {
        first[0] = ItemStack.of(Material.DIAMOND, 10)
        session.offerItem(Side.FIRST, first[0]!!, 10, first)
        session.toggleAcceptance(Side.FIRST, status())
        first[0]!!.amount = 3
        assertEquals(TradeStatus.Short(Side.FIRST), status())
        val shortfall = session.fitTo(first, second).getValue(Side.FIRST).single()
        assertEquals(7, shortfall.missing)
        assertEquals(3, session.offered(Side.FIRST, first[0]!!))
        assertFalse(session.acceptance.isAccepted(Side.FIRST))
        assertEquals(TradeStatus.Ready, status())
    }

    @Test
    fun `money the side does not have, or no economy, blocks the trade`() {
        session.offerMoney(Side.SECOND, BigDecimal(150))
        assertEquals(TradeStatus.NoMoney(Side.SECOND), status())
        balances[Side.SECOND] = BigDecimal(150)
        assertEquals(TradeStatus.Ready, status())
        balances[Side.SECOND] = null
        assertEquals(TradeStatus.NoEconomy, status())
    }

    @Test
    fun `the session exchanges the offers it holds`() {
        val alice = server.addPlayer("Alice")
        val bob = server.addPlayer("Bob")
        alice.inventory.setItem(4, ItemStack.of(Material.DIAMOND, 10))
        session.offerItem(Side.FIRST, alice.inventory.getItem(4)!!, 6, alice.inventory.storageContents)
        val done = assertIs<ExchangeOutcome.Done>(session.exchange(Exchange(Logger.getLogger("TradeSessionTest")), alice, bob, null))
        assertEquals(listOf(6), done.firstGave.map { it.amount })
        assertEquals(4, alice.inventory.getItem(4)!!.amount)
        assertEquals(6, bob.inventory.getItem(0)!!.amount)
    }

    @Test
    fun `received items count by the stack size of each kind`() {
        for (slot in 0 until 35) second[slot] = ItemStack.of(Material.DIRT, 64)
        first[0] = ItemStack.of(Material.ENDER_PEARL, 16)
        first[1] = ItemStack.of(Material.ENDER_PEARL, 16)
        session.offerItem(Side.FIRST, first[0]!!, 32, first)
        assertEquals(TradeStatus.NoRoom(Side.SECOND, 16), status())
        second[34] = ItemStack.of(Material.ENDER_PEARL, 2)
        assertEquals(TradeStatus.NoRoom(Side.SECOND, 2), status())
    }
}
