package dev.versatia.trade.exchange

import dev.versatia.trade.ServerTest
import dev.versatia.trade.money.Funds
import dev.versatia.trade.offer.Offer
import dev.versatia.trade.offer.count
import dev.versatia.trade.offer.Side
import java.math.BigDecimal
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.entity.PlayerMock

class ExchangeTest : ServerTest() {
    private lateinit var alice: PlayerMock
    private lateinit var bob: PlayerMock
    private val aliceOffer = Offer()
    private val bobOffer = Offer()
    private val exchange = Exchange(Logger.getLogger("ExchangeTest"))

    @BeforeTest
    fun players() {
        alice = server.addPlayer("Alice")
        bob = server.addPlayer("Bob")
    }

    private fun Player.put(slot: Int, material: Material, amount: Int = 1) = inventory.setItem(slot, ItemStack.of(material, amount))

    private fun Player.offerFrom(offer: Offer, slot: Int, amount: Int) = offer.add(inventory.getItem(slot)!!, amount, inventory.storageContents.count(inventory.getItem(slot)!!), 16)

    private fun Player.count(material: Material) = inventory.storageContents.filterNotNull().filter { it.type == material }.sumOf { it.amount }

    private fun snapshot(player: Player) = player.inventory.storageContents.map { it?.clone() }

    private fun run(funds: Funds? = null) = exchange.run(alice, aliceOffer, bob, bobOffer, funds)

    @Test
    fun `both sides get what the other gave, and the offered items leave their slots`() {
        alice.put(0, Material.DIAMOND, 64)
        bob.put(5, Material.IRON_SWORD)
        alice.offerFrom(aliceOffer, 0, 10)
        bob.offerFrom(bobOffer, 5, 1)
        val done = assertIs<ExchangeOutcome.Done>(run())
        assertEquals(54, alice.inventory.getItem(0)!!.amount)
        assertEquals(1, alice.count(Material.IRON_SWORD))
        assertEquals(10, bob.count(Material.DIAMOND))
        assertNull(bob.inventory.getItem(5))
        assertEquals(listOf(10), done.firstGave.map { it.amount })
        assertEquals(listOf(Material.IRON_SWORD), done.secondGave.map { it.type })
    }

    @Test
    fun `received items fill similar stacks first, then the first empty slots`() {
        bob.put(0, Material.DIAMOND, 60)
        alice.put(0, Material.DIAMOND, 20)
        alice.offerFrom(aliceOffer, 0, 20)
        assertIs<ExchangeOutcome.Done>(run())
        assertEquals(64, bob.inventory.getItem(0)!!.amount)
        assertEquals(16, bob.inventory.getItem(1)!!.amount)
        assertNull(alice.inventory.getItem(0))
    }

    @Test
    fun `without room for everything nothing moves`() {
        for (slot in 0 until 36) bob.put(slot, Material.DIRT, 64)
        alice.put(0, Material.DIAMOND, 5)
        alice.offerFrom(aliceOffer, 0, 5)
        val before = snapshot(alice) to snapshot(bob)
        val refused = assertIs<ExchangeOutcome.Refused>(run())
        assertEquals(Refusal.NoRoom(Side.SECOND, 5), refused.reason)
        assertEquals(before, snapshot(alice) to snapshot(bob))
    }

    @Test
    fun `the room the receiver's own offer frees counts`() {
        for (slot in 0 until 36) bob.put(slot, Material.DIRT, 64)
        alice.put(0, Material.DIAMOND, 5)
        alice.offerFrom(aliceOffer, 0, 5)
        bob.offerFrom(bobOffer, 7, 64)
        assertIs<ExchangeOutcome.Done>(run())
        assertEquals(5, bob.count(Material.DIAMOND))
        assertEquals(64, alice.count(Material.DIRT))
    }

    @Test
    fun `offered items are taken wherever they lie when the exchange runs`() {
        alice.put(0, Material.DIAMOND, 10)
        alice.offerFrom(aliceOffer, 0, 10)
        alice.inventory.setItem(0, null)
        alice.put(8, Material.DIAMOND, 4)
        alice.put(30, Material.DIAMOND, 6)
        assertIs<ExchangeOutcome.Done>(run())
        assertEquals(0, alice.count(Material.DIAMOND))
        assertEquals(10, bob.count(Material.DIAMOND))
    }

    @Test
    fun `items lost before the exchange refuse it, naming what is missing, and nothing moves`() {
        alice.put(0, Material.DIAMOND, 10)
        alice.offerFrom(aliceOffer, 0, 10)
        alice.put(0, Material.DIAMOND, 3)
        val before = snapshot(alice) to snapshot(bob)
        val short = assertIs<Refusal.Short>(assertIs<ExchangeOutcome.Refused>(run()).reason)
        assertEquals(Side.FIRST, short.side)
        assertEquals(7, short.missing)
        assertEquals(before, snapshot(alice) to snapshot(bob))
        alice.put(0, Material.EMERALD, 10)
        assertIs<Refusal.Short>(assertIs<ExchangeOutcome.Refused>(run()).reason)
    }

    @Test
    fun `the items taken leave the hotbar for last`() {
        alice.put(0, Material.DIAMOND, 10)
        alice.put(20, Material.DIAMOND, 10)
        alice.offerFrom(aliceOffer, 0, 12)
        assertIs<ExchangeOutcome.Done>(run())
        assertEquals(8, alice.inventory.getItem(0)!!.amount)
        assertNull(alice.inventory.getItem(20))
    }

    @Test
    fun `offering touches no inventory, so ending a trade has nothing to give back`() {
        alice.put(0, Material.DIAMOND, 10)
        bob.put(3, Material.GOLD_INGOT, 7)
        val before = snapshot(alice) to snapshot(bob)
        alice.offerFrom(aliceOffer, 0, 10)
        bob.offerFrom(bobOffer, 3, 7)
        aliceOffer.setMoney(BigDecimal.TEN)
        assertEquals(before, snapshot(alice) to snapshot(bob))
    }

    @Test
    fun `money moves both ways together with the items`() {
        val funds = FakeFunds(alice.uniqueId to BigDecimal(100), bob.uniqueId to BigDecimal(50))
        alice.put(0, Material.DIAMOND, 1)
        alice.offerFrom(aliceOffer, 0, 1)
        aliceOffer.setMoney(BigDecimal(30))
        bobOffer.setMoney(BigDecimal(50))
        val done = assertIs<ExchangeOutcome.Done>(run(funds))
        assertEquals(BigDecimal(120), funds.balance(alice))
        assertEquals(BigDecimal(30), funds.balance(bob))
        assertEquals(BigDecimal(30), done.firstMoney)
        assertEquals(1, bob.count(Material.DIAMOND))
    }

    @Test
    fun `money a side does not have refuses the exchange before anything moves`() {
        val funds = FakeFunds(alice.uniqueId to BigDecimal(10), bob.uniqueId to BigDecimal(10))
        bob.put(0, Material.DIAMOND, 1)
        bob.offerFrom(bobOffer, 0, 1)
        aliceOffer.setMoney(BigDecimal(11))
        val before = snapshot(alice) to snapshot(bob)
        assertEquals(Refusal.NoMoney(Side.FIRST), assertIs<ExchangeOutcome.Refused>(run(funds)).reason)
        assertEquals(before, snapshot(alice) to snapshot(bob))
        assertEquals(listOf(BigDecimal(10), BigDecimal(10)), listOf(funds.balance(alice), funds.balance(bob)))
        assertEquals(0, funds.calls)
    }

    @Test
    fun `an economy failing midway undoes the money already moved and moves no item`() {
        for (failing in 1..4) {
            val funds = FakeFunds(alice.uniqueId to BigDecimal(100), bob.uniqueId to BigDecimal(100), failOn = failing)
            alice.put(0, Material.DIAMOND, 1)
            alice.offerFrom(aliceOffer, 0, 1)
            aliceOffer.setMoney(BigDecimal(40))
            bobOffer.setMoney(BigDecimal(25))
            val before = snapshot(alice) to snapshot(bob)
            val reason = assertIs<ExchangeOutcome.Refused>(run(funds)).reason
            assertEquals(if (failing <= 2) Refusal.NoMoney(if (failing == 1) Side.FIRST else Side.SECOND) else Refusal.EconomyFailed, reason, "failing call $failing")
            assertEquals(before, snapshot(alice) to snapshot(bob), "failing call $failing")
            assertEquals(listOf(BigDecimal(100), BigDecimal(100)), listOf(funds.balance(alice), funds.balance(bob)), "failing call $failing")
        }
    }

    @Test
    fun `money offered without an economy refuses the exchange`() {
        aliceOffer.setMoney(BigDecimal.ONE)
        assertEquals(Refusal.NoEconomy, assertIs<ExchangeOutcome.Refused>(run(null)).reason)
    }

    @Test
    fun `stacks are split by the item's stack size`() {
        alice.put(0, Material.ENDER_PEARL, 16)
        alice.put(1, Material.ENDER_PEARL, 16)
        alice.offerFrom(aliceOffer, 0, 16)
        alice.offerFrom(aliceOffer, 1, 16)
        bob.put(0, Material.ENDER_PEARL, 10)
        assertIs<ExchangeOutcome.Done>(run())
        assertContentEquals(listOf(16, 16, 10), bob.inventory.storageContents.filterNotNull().map { it.amount }.take(3))
    }

    /** An economy in a map; the call numbered [failOn], counting withdrawals and deposits from 1, is refused. */
    private class FakeFunds(vararg balances: Pair<UUID, BigDecimal>, private val failOn: Int? = null) : Funds {
        private val balances = mutableMapOf(*balances)
        var calls = 0
            private set

        override fun balance(player: Player) = balances[player.uniqueId] ?: BigDecimal.ZERO

        override fun withdraw(player: Player, amount: BigDecimal): Boolean {
            if (++calls == failOn || balance(player) < amount) return false
            balances[player.uniqueId] = balance(player) - amount
            return true
        }

        override fun deposit(player: Player, amount: BigDecimal): Boolean {
            if (++calls == failOn) return false
            balances[player.uniqueId] = balance(player) + amount
            return true
        }

        override fun format(amount: BigDecimal): String = amount.toPlainString()
    }
}
