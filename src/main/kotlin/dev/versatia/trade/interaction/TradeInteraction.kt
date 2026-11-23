package dev.versatia.trade.interaction

import dev.versatia.ConditionalOnPlugin
import dev.versatia.di.LocalBean
import dev.versatia.di.Primary
import dev.versatia.interactions.Interaction
import dev.versatia.interactions.InteractionAction
import dev.versatia.interactions.InteractionCheck
import dev.versatia.interactions.Interactions
import dev.versatia.trade.request.TradeRequests
import dev.versatia.trade.session.TradeSessions
import org.bukkit.entity.Player

/**
 * The trade button of VersatiaInteractions' menu: busy and in progress come from the trades going on,
 * a denial from the same rules as `/trade`, and an accepted request opens the trade.
 */
@InteractionAction(TradeInteraction.ID)
internal class TradeInteraction(private val sessions: TradeSessions) : Interaction {
    override fun engagedWith(player: Player) = sessions.partnerOf(player)

    override fun check(from: Player, to: Player) = sessions.reasonAgainst(from, to)?.let(InteractionCheck::Denied) ?: InteractionCheck.Allowed

    override fun accepted(from: Player, to: Player) = sessions.open(from, to)

    companion object {
        const val ID = "trade"
    }
}

/** With VersatiaInteractions, `/trade <player>` sends the request of its menu, with its waiting button and its answers. */
@LocalBean
@Primary
@ConditionalOnPlugin("VersatiaInteractions")
internal class InteractionTradeRequests(private val interactions: Interactions) : TradeRequests {
    override val answersByCommand get() = false

    override fun send(from: Player, to: Player) = interactions.request(from, to, TradeInteraction.ID)

    override fun answer(receiver: Player, senderName: String, yes: Boolean) = Unit
}
