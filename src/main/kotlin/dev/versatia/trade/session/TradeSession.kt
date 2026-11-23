package dev.versatia.trade.session

import dev.versatia.trade.exchange.Exchange
import dev.versatia.trade.exchange.ExchangePlan
import dev.versatia.trade.money.Funds
import dev.versatia.trade.offer.Acceptance
import dev.versatia.trade.offer.ItemMatcher
import dev.versatia.trade.offer.Offer
import dev.versatia.trade.offer.OfferedItem
import dev.versatia.trade.offer.Shortfall
import dev.versatia.trade.offer.Side
import dev.versatia.trade.offer.count
import java.math.BigDecimal
import java.util.EnumMap
import kotlin.time.Duration
import kotlin.time.TimeSource
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Where a trade stands as the offers are now, shown in both windows: whether it can be accepted, and why not. */
internal sealed interface TradeStatus {
    /** Nothing is offered on either side. */
    data object Empty : TradeStatus

    /** The exchange can happen as the offers stand. */
    data object Ready : TradeStatus

    /** [side] has no room for [items] of what the other side offers. */
    data class NoRoom(val side: Side, val items: Int) : TradeStatus

    /** [side] offers more money than it has. */
    data class NoMoney(val side: Side) : TradeStatus

    /** Money is offered, but the server has no economy now. */
    data object NoEconomy : TradeStatus

    /** [side] offers items it no longer holds, until the offer is brought in line with its inventory. */
    data class Short(val side: Side) : TradeStatus
}

/**
 * The two offers of one trade, the acceptance and where the trade stands, worked out from the
 * players' storages and balances the caller passes in, so it runs without a server; server thread
 * only. Every change of an offer, and every worsening of the status, takes both acceptances back;
 * accepting is possible only while the status is [TradeStatus.Ready]. See docs/trade.md.
 */
internal class TradeSession(
    time: TimeSource.WithComparableMarks,
    countdown: Duration,
    private val capacity: Int,
    private val matcher: ItemMatcher = ItemMatcher.SIMILAR,
) {
    private val offers = EnumMap<Side, Offer>(Side::class.java).apply { Side.entries.forEach { put(it, Offer(matcher)) } }
    val acceptance = Acceptance(time, countdown)

    fun items(side: Side): List<OfferedItem> = offers.getValue(side).items

    fun money(side: Side): BigDecimal = offers.getValue(side).money

    /** How many items like [item] [side] offers. */
    fun offered(side: Side, item: ItemStack) = offers.getValue(side).offered(item)

    /** [storage] as it stays for [side] once its offered items are taken, taken as the exchange takes them: what the window shows. */
    fun remainingIn(side: Side, storage: Array<ItemStack?>) =
        ExchangePlan.copyOf(storage).also { ExchangePlan.takeOffered(it, offers.getValue(side), matcher) }

    /** [side] offers [amount] more items like [item], at most what [storage] holds and what fits the window. False when nothing changed. */
    fun offerItem(side: Side, item: ItemStack, amount: Int, storage: Array<ItemStack?>) =
        changed(offers.getValue(side).add(item, amount, storage.count(item, matcher), capacity))

    /** [side] takes [amount] items like [item] back from its offer. False when nothing changed. */
    fun withdrawItem(side: Side, item: ItemStack, amount: Int) = changed(offers.getValue(side).remove(item, amount))

    /** [side] offers [amount] of money. False when nothing changed. */
    fun offerMoney(side: Side, amount: BigDecimal) = changed(offers.getValue(side).setMoney(amount))

    /**
     * Brings both offers in line with the storages, for items a player lost since offering them (taken
     * by a command or another plugin, say): each side's shortfalls; any of them takes both acceptances
     * back.
     */
    fun fitTo(first: Array<ItemStack?>, second: Array<ItemStack?>): Map<Side, List<Shortfall>> {
        val shortfalls = mapOf(Side.FIRST to offers.getValue(Side.FIRST).fitTo(first), Side.SECOND to offers.getValue(Side.SECOND).fitTo(second))
        changed(shortfalls.values.any { it.isNotEmpty() })
        return shortfalls
    }

    /**
     * Where the trade stands with the storages [first] and [second] and the balances [balance] gives,
     * null for a side whose money cannot be told because there is no economy. Takes both acceptances
     * back unless the trade is ready, so a player who filled their inventory after accepting must
     * accept again.
     */
    fun status(first: Array<ItemStack?>, second: Array<ItemStack?>, balance: (Side) -> BigDecimal?): TradeStatus {
        val status = statusOf(first, second, balance)
        if (status != TradeStatus.Ready) acceptance.reset()
        return status
    }

    /**
     * Runs the exchange of the two offers between [first] and [second], the players of the two sides,
     * re-checking everything as [Exchange] does; the caller ends the trade after it, whatever the outcome.
     */
    fun exchange(exchange: Exchange, first: Player, second: Player, funds: Funds?) =
        exchange.run(first, offers.getValue(Side.FIRST), second, offers.getValue(Side.SECOND), funds, matcher)

    /** [side] clicks accept: takes its acceptance back, or accepts when [status] is ready. False when accepting is not possible now. */
    fun toggleAcceptance(side: Side, status: TradeStatus): Boolean {
        if (!acceptance.isAccepted(side) && status != TradeStatus.Ready) return false
        acceptance.toggle(side)
        return true
    }

    private fun statusOf(first: Array<ItemStack?>, second: Array<ItemStack?>, balance: (Side) -> BigDecimal?): TradeStatus {
        if (offers.values.all(Offer::isEmpty)) return TradeStatus.Empty
        for (side in Side.entries) {
            val money = offers.getValue(side).money
            if (money.signum() == 0) continue
            val held = balance(side) ?: return TradeStatus.NoEconomy
            if (held < money) return TradeStatus.NoMoney(side)
        }
        return when (val plan = ExchangePlan.of(first, offers.getValue(Side.FIRST), second, offers.getValue(Side.SECOND), matcher)) {
            is ExchangePlan.Ready -> TradeStatus.Ready
            is ExchangePlan.NoRoom -> TradeStatus.NoRoom(plan.side, plan.items)
            is ExchangePlan.Short -> TradeStatus.Short(plan.side)
        }
    }

    private fun changed(changed: Boolean) = changed.also { if (it) acceptance.reset() }
}
