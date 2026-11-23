package dev.versatia.trade.offer

import dev.versatia.trade.ServerTest
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class OfferTest : ServerTest() {
    private val offer = Offer()
    private val diamond get() = ItemStack.of(Material.DIAMOND)

    @Test
    fun `offering adds up to what the player holds`() {
        assertTrue(offer.add(diamond, 15, available = 20, capacity = 16))
        assertTrue(offer.add(diamond, 15, available = 20, capacity = 16))
        assertEquals(20, offer.offered(diamond))
        assertFalse(offer.add(diamond, 1, available = 20, capacity = 16))
    }

    @Test
    fun `an offer names kinds of items, whatever stack they come from`() {
        offer.add(ItemStack.of(Material.DIAMOND, 64), 10, available = 74, capacity = 16)
        offer.add(ItemStack.of(Material.DIAMOND, 10), 10, available = 74, capacity = 16)
        assertEquals(listOf(20), offer.items.map { it.count })
        assertEquals(1, offer.items.size)
    }

    @Test
    fun `the offer never takes more stacks than the window holds`() {
        assertTrue(offer.add(diamond, 1000, available = 1000, capacity = 2))
        assertEquals(128, offer.offered(diamond))
        assertFalse(offer.add(ItemStack.of(Material.EMERALD), 1, available = 5, capacity = 2))
        offer.remove(diamond, 64)
        assertTrue(offer.add(ItemStack.of(Material.EMERALD), 5, available = 5, capacity = 2))
        assertEquals(2, offer.stackCount)
    }

    @Test
    fun `unstackable items take a stack each`() {
        val sword = ItemStack.of(Material.IRON_SWORD)
        offer.add(sword, 3, available = 3, capacity = 16)
        assertEquals(3, offer.stackCount)
        assertEquals(listOf(1, 1, 1), offer.items.single().stacks().map { it.amount })
    }

    @Test
    fun `taking back lowers the count and drops the kind at zero`() {
        offer.add(diamond, 10, available = 10, capacity = 16)
        assertTrue(offer.remove(diamond, 4))
        assertEquals(6, offer.offered(diamond))
        assertTrue(offer.remove(diamond, 6))
        assertTrue(offer.isEmpty)
        assertFalse(offer.remove(diamond, 1))
    }

    @Test
    fun `fitting to the inventory caps what the player no longer holds`() {
        offer.add(diamond, 10, available = 10, capacity = 16)
        offer.add(ItemStack.of(Material.EMERALD), 5, available = 5, capacity = 16)
        val storage = arrayOfNulls<ItemStack>(36).also { it[7] = ItemStack.of(Material.DIAMOND, 4) }
        val shortfalls = offer.fitTo(storage)
        assertEquals(listOf(Material.DIAMOND to 6, Material.EMERALD to 5), shortfalls.map { it.item.type to it.missing })
        assertEquals(4, offer.offered(diamond))
        assertEquals(listOf(Material.DIAMOND), offer.items.map { it.item.type })
        assertTrue(offer.fitTo(storage).isEmpty())
    }

    @Test
    fun `items with other components are another kind`() {
        val named = ItemStack.of(Material.DIAMOND).apply { editMeta { it.displayName(Component.text("Shiny")) } }
        offer.add(diamond, 1, available = 1, capacity = 16)
        offer.add(named, 1, available = 1, capacity = 16)
        assertEquals(2, offer.items.size)
    }

    @Test
    fun `money is never negative and only a new amount is a change`() {
        assertTrue(offer.setMoney(BigDecimal("12.5")))
        assertFalse(offer.setMoney(BigDecimal("12.50")))
        assertTrue(offer.setMoney(BigDecimal("-3")))
        assertEquals(BigDecimal.ZERO, offer.money)
        assertTrue(offer.isEmpty)
    }
}
