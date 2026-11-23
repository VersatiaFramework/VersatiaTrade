package dev.versatia.trade.exchange

import dev.versatia.trade.offer.ItemMatcher
import dev.versatia.trade.offer.Offer
import dev.versatia.trade.offer.Shortfall
import dev.versatia.trade.offer.Side
import org.bukkit.inventory.ItemStack

/** What an exchange would do, worked out on copies of the two inventories' storage before anything moves. */
internal sealed interface ExchangePlan {
    /** The storage of each side after the exchange, ready to be set, and the stacks each side gives, as they lay in its inventory. */
    class Ready(
        val first: Array<ItemStack?>,
        val second: Array<ItemStack?>,
        val firstGives: List<ItemStack>,
        val secondGives: List<ItemStack>,
    ) : ExchangePlan

    /** [side] no longer holds [missing] of the offered items like [item]. */
    class Short(val side: Side, val item: ItemStack, val missing: Int) : ExchangePlan

    /** [side] has no room for [items] of the items the other side gives. */
    data class NoRoom(val side: Side, val items: Int) : ExchangePlan

    companion object {
        /**
         * Plans the exchange of [firstOffer] and [secondOffer] between the storages [first] and
         * [second] (36 slots, the hotbar first): each side's offered items leave it from wherever they
         * lie, [matcher] telling which stacks are offered, then the other side gets those very stacks
         * as `Inventory.addItem` would place them, filling similar stacks first and the first empty
         * slots after. Pure: the arrays given are not changed.
         */
        fun of(first: Array<ItemStack?>, firstOffer: Offer, second: Array<ItemStack?>, secondOffer: Offer, matcher: ItemMatcher = ItemMatcher.SIMILAR): ExchangePlan {
            val firstLeft = copyOf(first)
            val secondLeft = copyOf(second)
            val firstGives = ArrayList<ItemStack>()
            val secondGives = ArrayList<ItemStack>()
            takeOffered(firstLeft, firstOffer, matcher, firstGives)?.let { return Short(Side.FIRST, it.item, it.missing) }
            takeOffered(secondLeft, secondOffer, matcher, secondGives)?.let { return Short(Side.SECOND, it.item, it.missing) }
            val firstLeftover = add(firstLeft, secondGives)
            if (firstLeftover > 0) return NoRoom(Side.FIRST, firstLeftover)
            val secondLeftover = add(secondLeft, firstGives)
            if (secondLeftover > 0) return NoRoom(Side.SECOND, secondLeftover)
            return Ready(firstLeft, secondLeft, firstGives, secondGives)
        }

        /** A copy of [storage] whose empty slots are null, whether the server gave null or air. */
        fun copyOf(storage: Array<ItemStack?>) = Array(storage.size) { storage[it]?.takeUnless(ItemStack::isEmpty)?.clone() }

        /**
         * Takes the items [offer] names out of [storage], a copy, from the last slot backwards, so the
         * hotbar keeps its stacks the longest; the window shows what remains the same way. Each part
         * taken goes into [taken], as it lay. Returns the first kind it cannot take in full, with how
         * many items are missing, or null.
         */
        fun takeOffered(storage: Array<ItemStack?>, offer: Offer, matcher: ItemMatcher = ItemMatcher.SIMILAR, taken: MutableList<ItemStack>? = null): Shortfall? {
            for (offered in offer.items) {
                var left = offered.count
                for (index in storage.indices.reversed()) {
                    if (left == 0) break
                    val stack = storage[index] ?: continue
                    if (!matcher.same(stack, offered.item)) continue
                    val amount = minOf(left, stack.amount)
                    taken?.add(stack.asQuantity(amount))
                    left -= amount
                    storage[index] = stack.takeIf { it.amount > amount }?.apply { this.amount -= amount }
                }
                if (left > 0) return Shortfall(offered.item, left)
            }
            return null
        }

        /** Adds [items] to [storage]; how many items found no room. */
        private fun add(storage: Array<ItemStack?>, items: List<ItemStack>): Int {
            var leftover = 0
            for (item in items) {
                var remaining = item.amount
                for (current in storage) {
                    if (remaining == 0) break
                    if (current == null || !current.isSimilar(item) || current.amount >= current.maxStackSize) continue
                    val moved = minOf(remaining, current.maxStackSize - current.amount)
                    current.amount += moved
                    remaining -= moved
                }
                for (index in storage.indices) {
                    if (remaining == 0) break
                    if (storage[index] != null) continue
                    val moved = minOf(remaining, item.maxStackSize)
                    storage[index] = item.asQuantity(moved)
                    remaining -= moved
                }
                leftover += remaining
            }
            return leftover
        }
    }

}
