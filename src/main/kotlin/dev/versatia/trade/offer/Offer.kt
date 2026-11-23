package dev.versatia.trade.offer

import java.math.BigDecimal
import org.bukkit.inventory.ItemStack

/**
 * One kind of item in an offer: [item] stands for every stack similar to it (same type and the same
 * components, durability, enchantments and contents included), [count] for how many such items are
 * offered, wherever they lie in the inventory.
 */
internal class OfferedItem(item: ItemStack, val count: Int) {
    val item: ItemStack = item.asOne()

    /** The offered items as stacks of at most the item's stack size, as the window shows them and the exchange gives them. */
    fun stacks(): List<ItemStack> = buildList {
        var left = count
        while (left > 0) {
            val amount = minOf(left, item.maxStackSize)
            add(item.asQuantity(amount))
            left -= amount
        }
    }

    /** How many stacks [count] items take. */
    val stackCount get() = (count + item.maxStackSize - 1) / item.maxStackSize
}

/** [missing] items like [item] are offered but no longer in the inventory. */
internal class Shortfall(val item: ItemStack, val missing: Int)

/**
 * What one side puts into a trade: how many items of which kinds, and an amount of money. An offer
 * names items, not slots, so moving them around the inventory changes nothing; the items stay in the
 * inventory until the exchange, which takes them from wherever they are. [matcher] tells which stacks
 * are one kind. Server thread only. See docs/trade.md.
 */
internal class Offer(private val matcher: ItemMatcher = ItemMatcher.SIMILAR) {
    private val kinds = ArrayList<OfferedItem>()

    /** The offered kinds, in the order they were first offered. */
    val items: List<OfferedItem> get() = kinds.toList()

    var money: BigDecimal = BigDecimal.ZERO
        private set

    val isEmpty get() = kinds.isEmpty() && money.signum() == 0

    /** How many stacks the offered items take in the window. */
    val stackCount get() = kinds.sumOf { it.stackCount }

    /** How many items like [item] are offered. */
    fun offered(item: ItemStack) = kinds.firstOrNull { matcher.same(it.item, item) }?.count ?: 0

    /**
     * Offers [amount] more items like [item], at most [available] of them, the player's own, and no
     * more than the stacks of the whole offer fit into [capacity] slots. False when nothing changed.
     */
    fun add(item: ItemStack, amount: Int, available: Int, capacity: Int): Boolean {
        if (item.isEmpty || amount <= 0) return false
        val index = kinds.indexOfFirst { matcher.same(it.item, item) }
        val before = if (index >= 0) kinds[index].count else 0
        val freeStacks = capacity - kinds.filterIndexed { other, _ -> other != index }.sumOf { it.stackCount }
        val after = minOf(before.toLong() + amount, available.toLong(), freeStacks.toLong() * item.maxStackSize).toInt()
        if (after <= before) return false
        if (index >= 0) kinds[index] = OfferedItem(item, after) else kinds += OfferedItem(item, after)
        return true
    }

    /** Offers [amount] fewer items like [item], none at the end. False when nothing changed. */
    fun remove(item: ItemStack, amount: Int): Boolean {
        val index = kinds.indexOfFirst { matcher.same(it.item, item) }
        if (index < 0 || amount <= 0) return false
        val after = kinds[index].count - amount
        if (after > 0) kinds[index] = OfferedItem(kinds[index].item, after) else kinds.removeAt(index)
        return true
    }

    /**
     * Caps every kind at what [storage] holds now, as when the player lost items, and shows each kind
     * as its stacks look now, for items whose look changes in the inventory; the kinds that shrank.
     */
    fun fitTo(storage: Array<ItemStack?>): List<Shortfall> {
        val shortfalls = ArrayList<Shortfall>()
        kinds.replaceAll { offered ->
            val held = storage.count(offered.item, matcher)
            val look = storage.firstOrNull { it != null && !it.isEmpty && matcher.same(it, offered.item) } ?: offered.item
            if (held < offered.count) shortfalls += Shortfall(offered.item, offered.count - held)
            OfferedItem(look, minOf(held, offered.count))
        }
        kinds.removeAll { it.count == 0 }
        return shortfalls
    }

    /** Offers [amount] of money, never less than zero. False when nothing changed. */
    fun setMoney(amount: BigDecimal): Boolean {
        val now = amount.max(BigDecimal.ZERO)
        if (now.compareTo(money) == 0) return false
        money = now
        return true
    }
}

/** How many items like [item], by [matcher], these storage slots hold. */
internal fun Array<ItemStack?>.count(item: ItemStack, matcher: ItemMatcher = ItemMatcher.SIMILAR) =
    sumOf { stack -> if (stack != null && !stack.isEmpty && matcher.same(stack, item)) stack.amount else 0 }
