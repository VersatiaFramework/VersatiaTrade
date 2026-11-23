package dev.versatia.trade.offer

import org.bukkit.inventory.ItemStack

/** Tells whether two stacks are the same item in a trade, whatever their amounts. */
internal fun interface ItemMatcher {
    fun same(a: ItemStack, b: ItemStack): Boolean

    companion object {
        /** As Bukkit compares items: the same type and the same components. */
        val SIMILAR = ItemMatcher { a, b -> a.isSimilar(b) }
    }
}
