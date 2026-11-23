package dev.versatia.trade.session

import dev.versatia.trade.offer.Side
import dev.versatia.trade.window.TradeWindow
import kotlinx.coroutines.Job
import org.bukkit.entity.Player

/** A trade going on between [first], who asked, and [second]: its offers and status, its windows and its countdown. */
internal class ActiveTrade(val first: Player, val second: Player, val session: TradeSession) {
    /** Set once the trade ended, exchanged or not; nothing acts on it afterwards. */
    var ended = false

    var status: TradeStatus = TradeStatus.Empty

    var countdown: Job? = null

    val windows = ArrayList<TradeWindow>(2)

    val players get() = listOf(first, second)

    fun sideOf(player: Player) = if (player.uniqueId == first.uniqueId) Side.FIRST else Side.SECOND

    fun player(side: Side) = if (side == Side.FIRST) first else second

    fun partnerOf(player: Player) = player(sideOf(player).other)
}
