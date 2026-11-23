package dev.versatia.trade.event

import java.math.BigDecimal
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

/**
 * A trade event another plugin may [deny], such as a combat tag, a region rule or a rule for items
 * bound to their owner: what it announces does not happen, and the players it names read [reason], a
 * whole sentence from the denying plugin's messages, or VersatiaTrade's own sentence when a plugin
 * cancels the event without one. All events: docs/events.md.
 */
abstract class DeniableTradeEvent : Event(), Cancellable {
    private var cancelled = false

    /** Why it was denied; null while it is not, or when a plugin cancelled it without a reason. */
    var reason: Component? = null
        private set

    fun deny(reason: Component) {
        this.reason = reason
        cancelled = true
    }

    override fun isCancelled() = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
        if (!cancel) reason = null
    }
}

/**
 * Before a trade between [first], who asked, and [second] opens, once VersatiaTrade's own checks
 * passed, however the trade was asked for. Both players read the reason of a denial, so it names whom
 * it concerns.
 */
class TradeStartEvent(val first: Player, val second: Player) : DeniableTradeEvent() {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/**
 * When [player] clicks an item of their inventory to offer it to [partner], before it joins the
 * offer: [item] is a copy, one item of the kind, and [amount] how many the click asks for, of which
 * fewer join when the player has no more or the offer is full. [player] reads the reason of a denial.
 */
class TradeItemOfferEvent(val player: Player, val partner: Player, val item: ItemStack, val amount: Int) : DeniableTradeEvent() {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/**
 * Whenever the trade window of [viewer] shows an item [owner] offers: [item] is a copy for the
 * viewer's eyes alone, which a listener may replace or change, such as to colour the requirements
 * the viewer meets. What the owner offers and what the viewer gets do not change. Fired at every
 * redraw of the window, so a listener must be cheap.
 */
class TradeItemRenderEvent(val viewer: Player, val owner: Player, var item: ItemStack) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/**
 * After the exchange of a trade between [first], who asked, and [second], once both players' data
 * are saved: [firstGave] and [secondGave] are copies of the stacks that changed hands, [firstMoney]
 * and [secondMoney] the money, zero when none.
 */
class TradeCompletedEvent(
    val first: Player,
    val second: Player,
    val firstGave: List<ItemStack>,
    val secondGave: List<ItemStack>,
    val firstMoney: BigDecimal,
    val secondMoney: BigDecimal,
) : Event() {
    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}

/** After a trade between [first], who asked, and [second] ended without an exchange, for [cause]. */
class TradeCancelledEvent(val first: Player, val second: Player, val cause: Cause) : Event() {
    enum class Cause {
        /** One of the two closed the window, or another plugin opened an inventory over it. */
        CLOSED,

        /** One of the two left the server. */
        QUIT,

        /** One of the two died. */
        DEATH,

        /** The two moved farther apart than `trade.max-distance`, or into different worlds. */
        DISTANCE,

        /** One of the two may no longer trade, or no longer offer the money they offer. */
        PERMISSION,

        /** VersatiaTrade stopped. */
        STOPPED,
    }

    override fun getHandlers() = handlerList

    companion object {
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList() = handlerList
    }
}
