package dev.versatia.trade.identity

import dev.versatia.trade.ItemIdentity
import dev.versatia.trade.ServerTest
import dev.versatia.trade.exchange.Exchange
import dev.versatia.trade.exchange.ExchangeOutcome
import dev.versatia.trade.offer.Side
import dev.versatia.trade.session.TradeSession
import dev.versatia.trade.session.TradeStatus
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class ItemIdentitiesTest : ServerTest() {
    private val logger = Logger.getLogger("ItemIdentitiesTest")
    private val identities = ItemIdentities(logger)

    /** Like an RPG plugin: the name is the item's identity, the lore is rendered for the holder. */
    private val byName = object : ItemIdentity {
        override fun of(item: ItemStack) = item.itemMeta?.displayName()?.let(PlainTextComponentSerializer.plainText()::serialize)
    }

    private fun sword(name: String, lore: String) = ItemStack.of(Material.IRON_SWORD).apply {
        editMeta {
            it.displayName(Component.text(name))
            it.lore(listOf(Component.text(lore)))
        }
    }

    @Test
    fun `the first identity knowing either stack decides, else Bukkit's similarity`() {
        identities.register("Rpg", listOf(byName))
        assertTrue(identities.same(sword("Fang", "Requires level 5"), sword("Fang", "Requires level 5 (met)")))
        assertFalse(identities.same(sword("Fang", "x"), sword("Claw", "x")))
        assertFalse(identities.same(sword("Fang", "x"), ItemStack.of(Material.IRON_SWORD)))
        assertTrue(identities.same(ItemStack.of(Material.DIAMOND), ItemStack.of(Material.DIAMOND, 5)))
        identities.withdraw("Rpg")
        assertFalse(identities.same(sword("Fang", "a"), sword("Fang", "b")))
    }

    @Test
    fun `an identity that throws counts as not knowing the item`() {
        identities.register("Broken", listOf(object : ItemIdentity {
            override fun of(item: ItemStack): Any = error("broken")
        }))
        assertTrue(identities.same(ItemStack.of(Material.DIAMOND), ItemStack.of(Material.DIAMOND)))
    }

    @Test
    fun `an item whose lore is rendered again stays offered, and the other side gets it as it is now`() {
        identities.register("Rpg", listOf(byName))
        val alice = server.addPlayer("Alice")
        val bob = server.addPlayer("Bob")
        val session = TradeSession(TestTimeSource(), 3.seconds, capacity = 16, matcher = identities)
        alice.inventory.setItem(0, sword("Fang", "Requires level 5"))
        session.offerItem(Side.FIRST, alice.inventory.getItem(0)!!, 1, alice.inventory.storageContents)
        alice.inventory.setItem(0, sword("Fang", "Requires level 5 (met)"))
        assertTrue(session.fitTo(alice.inventory.storageContents, bob.inventory.storageContents).values.all { it.isEmpty() })
        assertEquals(TradeStatus.Ready, session.status(alice.inventory.storageContents, bob.inventory.storageContents) { null })
        assertEquals(listOf(Component.text("Requires level 5 (met)")), session.items(Side.FIRST).single().item.itemMeta!!.lore())
        assertIs<ExchangeOutcome.Done>(session.exchange(Exchange(logger), alice, bob, null))
        assertEquals(listOf(Component.text("Requires level 5 (met)")), bob.inventory.getItem(0)!!.itemMeta!!.lore())
        assertEquals(null, alice.inventory.getItem(0))
    }
}
