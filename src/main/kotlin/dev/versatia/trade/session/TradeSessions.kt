package dev.versatia.trade.session

import dev.versatia.Phase
import dev.versatia.di.LocalBean
import dev.versatia.lifecycle.AutoInvoke
import dev.versatia.task.PluginScope
import dev.versatia.task.delayTicks
import dev.versatia.text.arg
import dev.versatia.trade.config.TradeSettings
import dev.versatia.trade.config.TradeSound
import dev.versatia.trade.config.configName
import dev.versatia.trade.event.TradeCancelledEvent
import dev.versatia.trade.event.TradeCancelledEvent.Cause
import dev.versatia.trade.event.TradeCompletedEvent
import dev.versatia.trade.event.TradeItemOfferEvent
import dev.versatia.trade.event.TradeStartEvent
import dev.versatia.trade.exchange.Exchange
import dev.versatia.trade.exchange.ExchangeOutcome
import dev.versatia.trade.exchange.Refusal
import dev.versatia.trade.identity.ItemIdentities
import dev.versatia.trade.interaction.EngagementNotifier
import dev.versatia.trade.log.TradeLog
import dev.versatia.trade.money.Money
import dev.versatia.trade.window.TradeWindow
import dev.versatia.trade.window.TradeWindows
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAttemptPickupItemEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack

/**
 * The trades going on, one per player at most, driven by events: the windows' clicks, a player
 * quitting, dying, moving, teleporting or changing world, and changes to a trader's inventory, which
 * are worked out once per tick however many slots changed; nothing polls. A trade ends when either
 * closes the window. Before the exchange everything is checked again, permissions included. Server
 * thread only. See docs/trade.md.
 */
@LocalBean
internal class TradeSessions(
    private val settings: TradeSettings,
    private val money: Money,
    private val exchange: Exchange,
    private val identities: ItemIdentities,
    private val log: TradeLog,
    private val texts: TradeTexts,
    private val windows: TradeWindows,
    private val notifier: EngagementNotifier,
    private val scope: PluginScope,
) : Listener {
    private val trades = HashMap<UUID, ActiveTrade>()
    private val dirty = LinkedHashSet<ActiveTrade>()
    private var flushScheduled = false

    /** The player [player] trades with right now, null when none. */
    fun partnerOf(player: Player) = trades[player.uniqueId]?.partnerOf(player)

    /** The sentence telling [from] why they cannot trade with [to] now, null when they can. */
    fun reasonAgainst(from: Player, to: Player): Component? = when {
        from.uniqueId == to.uniqueId -> texts.get("deny.self")
        !from.hasPermission(USE_PERMISSION) -> texts.get("deny.no-permission")
        !to.hasPermission(USE_PERMISSION) -> texts.get("deny.target-no-permission", arg("target", to))
        from.uniqueId in trades -> texts.get("deny.busy-self")
        to.uniqueId in trades -> texts.get("deny.busy", arg("target", to))
        !withinReach(from, to) -> texts.get("deny.too-far", arg("target", to))
        else -> null
    }

    /** Opens a trade between [first], who asked, and [second]; false when it cannot be, once both were told why. */
    fun open(first: Player, second: Player): Boolean {
        val reason = reasonAgainst(first, second)
        if (reason != null) {
            first.sendMessage(reason)
            reasonAgainst(second, first)?.let(second::sendMessage)
            listOf(first, second).forEach { texts.play(it, TradeSound.REFUSED) }
            return false
        }
        val start = TradeStartEvent(first, second)
        if (!start.callEvent()) {
            listOf(first to second, second to first).forEach { (player, partner) ->
                player.sendMessage(start.reason ?: texts.get("deny.unavailable", arg("target", partner)))
                texts.play(player, TradeSound.REFUSED)
            }
            return false
        }
        if (!first.isOnline || !second.isOnline) return false
        val trade = ActiveTrade(first, second, TradeSession(TimeSource.Monotonic, settings.countdown, TradeWindows.OFFER_SLOTS, identities))
        trade.players.forEach { trades[it.uniqueId] = trade }
        refresh(trade)
        trade.players.forEach { trade.windows += windows.open(trade, it, this) }
        trade.players.forEach(notifier::changed)
        return true
    }

    fun offerItem(trade: ActiveTrade, player: Player, item: ItemStack, amount: Int) {
        if (trade.ended) return
        val offer = TradeItemOfferEvent(player, trade.partnerOf(player), item.asOne(), amount)
        if (!offer.callEvent()) {
            player.sendMessage(offer.reason ?: texts.get("trade.item-denied", arg("item", item)))
            return texts.play(player, TradeSound.REFUSED)
        }
        if (trade.ended) return
        if (trade.session.offerItem(trade.sideOf(player), item, amount, player.inventory.storageContents)) changed(trade) else texts.play(player, TradeSound.REFUSED)
    }

    fun withdrawItem(trade: ActiveTrade, player: Player, item: ItemStack, amount: Int) {
        if (!trade.ended && trade.session.withdrawItem(trade.sideOf(player), item, amount)) changed(trade)
    }

    /** [player] changes the money they offer by [delta], within zero and their balance. */
    fun changeMoney(trade: ActiveTrade, player: Player, delta: BigDecimal) {
        if (trade.ended) return
        val funds = money.funds()
        if (funds == null || !player.hasPermission(MONEY_PERMISSION)) return texts.play(player, TradeSound.REFUSED)
        val side = trade.sideOf(player)
        val wanted = (trade.session.money(side) + delta).max(BigDecimal.ZERO).min(funds.balance(player).max(BigDecimal.ZERO))
        if (trade.session.offerMoney(side, wanted)) changed(trade) else texts.play(player, TradeSound.REFUSED)
    }

    /** [player] clicks accept: accepts while the trade is ready, or takes the acceptance back. */
    fun toggleAcceptance(trade: ActiveTrade, player: Player) {
        if (trade.ended) return
        refresh(trade)
        if (!permitted(trade)) return cancel(trade, Cause.PERMISSION)
        val accepted = trade.session.acceptance.isAccepted(trade.sideOf(player))
        if (!trade.session.toggleAcceptance(trade.sideOf(player), trade.status)) {
            texts.play(player, TradeSound.REFUSED)
            return redraw(trade)
        }
        if (!accepted) trade.players.forEach { texts.play(it, TradeSound.ACCEPTED) }
        if (trade.session.acceptance.counting) startCountdown(trade) else stopCountdown(trade)
        redraw(trade)
    }

    /** [player] closed their window, or another plugin opened an inventory over it. */
    fun closed(trade: ActiveTrade, player: Player) {
        if (!trade.ended) cancel(trade, Cause.CLOSED, player)
    }

    @AutoInvoke(Phase.PLUGIN_STOP)
    fun stop() = trades.values.distinct().forEach { cancel(it, Cause.STOPPED) }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        trades[event.player.uniqueId]?.let { cancel(it, Cause.QUIT, event.player) }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        trades[event.player.uniqueId]?.let { cancel(it, Cause.DEATH, event.player) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event.hasChangedBlock()) trades[event.player.uniqueId]?.let(::checkReach)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val trade = trades[event.player.uniqueId] ?: return
        // The event comes before the teleport; the distance counts from where the player lands
        scope.launch {
            delayTicks(1)
            checkReach(trade)
        }
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        trades[event.player.uniqueId]?.let(::checkReach)
    }

    /** A trader picks nothing up: their inventory should change only through the exchange. */
    @EventHandler(ignoreCancelled = true)
    fun onPickup(event: PlayerAttemptPickupItemEvent) {
        if (event.player.uniqueId in trades) event.isCancelled = true
    }

    /** Another plugin or a command changed a trader's inventory: worked out again at the next tick. */
    @EventHandler
    fun onSlotChange(event: PlayerInventorySlotChangeEvent) {
        trades[event.player.uniqueId]?.let(::markDirty)
    }

    private fun changed(trade: ActiveTrade) {
        stopCountdown(trade)
        refresh(trade)
        redraw(trade)
        trade.players.forEach { texts.play(it, TradeSound.CHANGED) }
    }

    /** Brings the offers in line with the inventories, telling a player what went missing, and works the status out. */
    private fun refresh(trade: ActiveTrade) {
        val first = trade.first.inventory.storageContents
        val second = trade.second.inventory.storageContents
        trade.session.fitTo(first, second).forEach { (side, shortfalls) ->
            shortfalls.forEach { texts.send(trade.player(side), "trade.shortfall", arg("count", it.missing), arg("item", it.item)) }
        }
        val funds = money.funds()
        trade.status = trade.session.status(first, second) { side -> funds?.balance(trade.player(side)) }
        if (!trade.session.acceptance.counting) stopCountdown(trade)
    }

    private fun redraw(trade: ActiveTrade) = trade.windows.forEach(TradeWindow::redraw)

    /** Counts down once both accepted, redrawing as the seconds go, then exchanges. */
    private fun startCountdown(trade: ActiveTrade) {
        trade.countdown?.cancel()
        trade.countdown = scope.launch {
            var shown: Int? = null
            while (!trade.session.acceptance.isDue()) {
                val left = trade.session.acceptance.secondsLeft()
                if (left != shown) {
                    shown = left
                    redraw(trade)
                }
                delay(100.milliseconds)
            }
            finish(trade)
        }
    }

    private fun stopCountdown(trade: ActiveTrade) {
        trade.countdown?.cancel()
        trade.countdown = null
    }

    /** The countdown ran out: everything is checked again, then the exchange runs. */
    private fun finish(trade: ActiveTrade) {
        if (trade.ended) return
        refresh(trade)
        if (trade.status != TradeStatus.Ready || !trade.session.acceptance.isDue()) return redraw(trade)
        if (!permitted(trade)) return cancel(trade, Cause.PERMISSION)
        when (val outcome = trade.session.exchange(exchange, trade.first, trade.second, money.funds())) {
            is ExchangeOutcome.Done -> complete(trade, outcome)
            is ExchangeOutcome.Refused -> refused(trade, outcome.reason)
        }
    }

    private fun complete(trade: ActiveTrade, done: ExchangeOutcome.Done) {
        end(trade)
        // Saved at once, so that a crash before the next autosave cannot roll the items back while the economy kept the payment
        trade.players.forEach(Player::saveData)
        log.record(trade.first, trade.second, done)
        TradeCompletedEvent(
            trade.first,
            trade.second,
            done.firstGave.map(ItemStack::clone),
            done.secondGave.map(ItemStack::clone),
            done.firstMoney,
            done.secondMoney,
        ).callEvent()
        trade.players.forEach {
            texts.send(it, "trade.completed", arg("partner", trade.partnerOf(it)))
            texts.play(it, TradeSound.COMPLETED)
        }
    }

    private fun refused(trade: ActiveTrade, reason: Refusal) {
        trade.session.acceptance.reset()
        refresh(trade)
        trade.players.forEach { player ->
            texts.send(player, "trade.refused", arg("reason", sentence(reason, trade, player)))
            texts.play(player, TradeSound.REFUSED)
        }
        redraw(trade)
    }

    /** The refusal as [player] reads it. */
    private fun sentence(reason: Refusal, trade: ActiveTrade, player: Player): Component {
        val status = when (reason) {
            is Refusal.Short -> TradeStatus.Short(reason.side)
            is Refusal.NoRoom -> TradeStatus.NoRoom(reason.side, reason.items)
            is Refusal.NoMoney -> TradeStatus.NoMoney(reason.side)
            Refusal.NoEconomy -> TradeStatus.NoEconomy
            Refusal.EconomyFailed -> return texts.get("trade.economy-failed")
        }
        return texts.status(status, trade.sideOf(player), trade.partnerOf(player), null)
    }

    /** Ends [trade] without an exchange for [cause], telling the players other than [by], who caused it, why. */
    private fun cancel(trade: ActiveTrade, cause: Cause, by: Player? = null) {
        if (trade.ended) return
        end(trade)
        TradeCancelledEvent(trade.first, trade.second, cause).callEvent()
        trade.players.filter { it.uniqueId != by?.uniqueId && it.isOnline }.forEach {
            texts.send(it, "trade.ended.${cause.configName}", arg("partner", trade.partnerOf(it)))
            texts.play(it, TradeSound.CANCELLED)
        }
    }

    private fun end(trade: ActiveTrade) {
        trade.ended = true
        stopCountdown(trade)
        trade.players.forEach { trades.remove(it.uniqueId, trade) }
        dirty.remove(trade)
        trade.windows.forEach(TradeWindow::close)
        trade.players.filter { it.isOnline }.forEach(notifier::changed)
    }

    private fun markDirty(trade: ActiveTrade) {
        dirty += trade
        if (flushScheduled) return
        flushScheduled = true
        scope.launch {
            delayTicks(1)
            flushScheduled = false
            val due = dirty.toList()
            dirty.clear()
            due.filterNot { it.ended }.forEach {
                refresh(it)
                redraw(it)
            }
        }
    }

    private fun checkReach(trade: ActiveTrade) {
        if (!trade.ended && !withinReach(trade.first, trade.second)) cancel(trade, Cause.DISTANCE)
    }

    /** Both may still trade, and a player offering money may still offer it. */
    private fun permitted(trade: ActiveTrade) = trade.players.all { player ->
        player.hasPermission(USE_PERMISSION) && (trade.session.money(trade.sideOf(player)).signum() == 0 || player.hasPermission(MONEY_PERMISSION))
    }

    private fun withinReach(a: Player, b: Player) = a.world == b.world && a.location.distanceSquared(b.location) <= settings.maxDistance.toDouble() * settings.maxDistance

    private companion object {
        const val USE_PERMISSION = "versatia.trade.use"
        const val MONEY_PERMISSION = "versatia.trade.money"
    }
}
