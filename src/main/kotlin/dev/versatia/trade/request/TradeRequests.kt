package dev.versatia.trade.request

import dev.versatia.VersatiaEngine
import dev.versatia.di.LocalBean
import dev.versatia.playerOrNull
import dev.versatia.task.PluginScope
import dev.versatia.text.arg
import dev.versatia.trade.config.TradeSettings
import dev.versatia.trade.config.TradeSound
import dev.versatia.trade.session.TradeSessions
import dev.versatia.trade.session.TradeTexts
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/** How a trade is asked for and answered: by VersatiaInteractions when it is on the server, by chat otherwise. */
internal interface TradeRequests {
    /** Whether `/trade accept` and `/trade decline` answer requests; VersatiaInteractions answers its own. */
    val answersByCommand: Boolean

    /** [from] asks [to] to trade, as `/trade <player>` does. */
    fun send(from: Player, to: Player)

    /** [receiver] accepts, or declines, the request of the player named [senderName]. */
    fun answer(receiver: Player, senderName: String, yes: Boolean)
}

/**
 * Requests without VersatiaInteractions: a chat line with clickable answers for the receiver, a
 * timeout, and two requests to each other accepting each other; a request ends when either player
 * quits. The trade itself is checked again when it opens.
 */
@LocalBean
internal class ChatTradeRequests(
    private val sessions: TradeSessions,
    private val settings: TradeSettings,
    private val texts: TradeTexts,
    private val scope: PluginScope,
) : TradeRequests, Listener {
    private val pending = HashMap<Pair<UUID, UUID>, Job>()

    override val answersByCommand get() = true

    override fun send(from: Player, to: Player) {
        sessions.reasonAgainst(from, to)?.let {
            from.sendMessage(it)
            return texts.play(from, TradeSound.REFUSED)
        }
        pending.remove(to.uniqueId to from.uniqueId)?.let {
            it.cancel()
            sessions.open(to, from)
            return
        }
        val key = from.uniqueId to to.uniqueId
        if (key in pending) return texts.send(from, "request.already-sent", arg("target", to))
        pending[key] = scope.launch {
            delay(settings.requestTimeout)
            pending.remove(key)
            if (from.isOnline) texts.send(from, "request.expired", arg("target", to))
        }
        texts.send(from, "request.sent", arg("target", to))
        texts.send(to, "request.received", arg("target", from))
        texts.play(to, TradeSound.REQUEST)
    }

    override fun answer(receiver: Player, senderName: String, yes: Boolean) {
        val sender = VersatiaEngine.playerOrNull(senderName)?.takeIf(receiver::canSee) ?: return texts.send(receiver, "request.none", arg("target", senderName))
        val job = pending.remove(sender.uniqueId to receiver.uniqueId) ?: return texts.send(receiver, "request.none", arg("target", sender))
        job.cancel()
        if (yes) sessions.open(sender, receiver) else texts.send(sender, "request.declined", arg("target", receiver))
    }

    /** The names of the players whose requests wait for [receiver]'s answer, for command suggestions. */
    fun waitingSenders(receiver: Player) = pending.keys.filter { it.second == receiver.uniqueId }.mapNotNull { VersatiaEngine.playerOrNull(it.first)?.name }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val id = event.player.uniqueId
        pending.keys.filter { id == it.first || id == it.second }.forEach { pending.remove(it)?.cancel() }
    }
}
