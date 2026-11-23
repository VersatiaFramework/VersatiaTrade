package dev.versatia.trade.exchange

import dev.versatia.di.LocalBean
import dev.versatia.trade.money.Funds
import dev.versatia.trade.offer.ItemMatcher
import dev.versatia.trade.offer.Offer
import dev.versatia.trade.offer.Side
import java.math.BigDecimal
import java.util.logging.Level
import java.util.logging.Logger
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** What an exchange did. */
internal sealed interface ExchangeOutcome {
    /** Both sides got what the other gave. */
    class Done(val firstGave: List<ItemStack>, val secondGave: List<ItemStack>, val firstMoney: BigDecimal, val secondMoney: BigDecimal) : ExchangeOutcome

    /** Nothing moved, for [reason]. */
    class Refused(val reason: Refusal) : ExchangeOutcome
}

/** Why an exchange did not happen; nothing moved in any of these cases. */
internal sealed interface Refusal {
    /** [side] no longer holds [missing] of the offered items like [item]. */
    class Short(val side: Side, val item: ItemStack, val missing: Int) : Refusal

    /** [side] has no room for [items] of the items the other side gives. */
    data class NoRoom(val side: Side, val items: Int) : Refusal

    /** [side] does not have the money it offers. */
    data class NoMoney(val side: Side) : Refusal

    /** Money is offered, but the server has no economy any more. */
    data object NoEconomy : Refusal

    /** The economy refused a step midway; the steps before it were undone. */
    data object EconomyFailed : Refusal
}

/**
 * Carries out an exchange on the server thread, in one go: plans it on copies of both storages,
 * checks the money, moves the money step by step, undoing the earlier steps when one fails, and only
 * then sets both storages to the planned contents. Nothing can change the inventories in between,
 * so either everything moves or nothing does. See docs/trade.md.
 */
@LocalBean
internal class Exchange(private val logger: Logger) {
    fun run(first: Player, firstOffer: Offer, second: Player, secondOffer: Offer, funds: Funds?, matcher: ItemMatcher = ItemMatcher.SIMILAR): ExchangeOutcome {
        val plan = when (val planned = ExchangePlan.of(first.inventory.storageContents, firstOffer, second.inventory.storageContents, secondOffer, matcher)) {
            is ExchangePlan.Short -> return ExchangeOutcome.Refused(Refusal.Short(planned.side, planned.item, planned.missing))
            is ExchangePlan.NoRoom -> return ExchangeOutcome.Refused(Refusal.NoRoom(planned.side, planned.items))
            is ExchangePlan.Ready -> planned
        }
        val firstMoney = firstOffer.money
        val secondMoney = secondOffer.money
        if (firstMoney.signum() > 0 || secondMoney.signum() > 0) {
            moveMoney(first, firstMoney, second, secondMoney, funds)?.let { return ExchangeOutcome.Refused(it) }
        }
        first.inventory.storageContents = plan.first
        second.inventory.storageContents = plan.second
        return ExchangeOutcome.Done(plan.firstGives, plan.secondGives, firstMoney, secondMoney)
    }

    /** Moves [firstMoney] from [first] to [second] and [secondMoney] back; null when it did, else why nothing moved. */
    private fun moveMoney(first: Player, firstMoney: BigDecimal, second: Player, secondMoney: BigDecimal, funds: Funds?): Refusal? {
        funds ?: return Refusal.NoEconomy
        if (attempt { funds.balance(first) < firstMoney } != false) return Refusal.NoMoney(Side.FIRST)
        if (attempt { funds.balance(second) < secondMoney } != false) return Refusal.NoMoney(Side.SECOND)
        val steps = listOf(
            Step(first, firstMoney, withdraw = true),
            Step(second, secondMoney, withdraw = true),
            Step(second, firstMoney, withdraw = false),
            Step(first, secondMoney, withdraw = false),
        ).filter { it.amount.signum() > 0 }
        val done = ArrayDeque<Step>()
        for (step in steps) {
            if (attempt { step.run(funds) } == true) {
                done.addFirst(step)
                continue
            }
            for (undone in done) if (attempt { undone.undo(funds) } != true) logger.severe("Could not undo ${undone.description} after a failed trade between ${first.name} and ${second.name}; check their balances")
            return if (step.withdraw) Refusal.NoMoney(if (step.player == first) Side.FIRST else Side.SECOND) else Refusal.EconomyFailed
        }
        return null
    }

    /** A call into the economy plugin; one that throws counts as refused, and is logged. */
    private inline fun <T : Any> attempt(call: () -> T): T? = try {
        call()
    } catch (e: RuntimeException) {
        logger.log(Level.SEVERE, "The economy failed during a trade", e)
        null
    }

    /** One movement of money and its inverse. */
    private class Step(val player: Player, val amount: BigDecimal, val withdraw: Boolean) {
        fun run(funds: Funds) = if (withdraw) funds.withdraw(player, amount) else funds.deposit(player, amount)

        fun undo(funds: Funds) = if (withdraw) funds.deposit(player, amount) else funds.withdraw(player, amount)

        val description get() = "${if (withdraw) "taking" else "giving"} ${amount.toPlainString()} ${if (withdraw) "from" else "to"} ${player.name}"
    }
}
