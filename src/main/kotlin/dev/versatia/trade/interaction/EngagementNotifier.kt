package dev.versatia.trade.interaction

import dev.versatia.ConditionalOnPlugin
import dev.versatia.di.LocalBean
import dev.versatia.di.Primary
import dev.versatia.interactions.Interactions
import org.bukkit.entity.Player

/** Told when a player starts or ends a trade, for the menus of VersatiaInteractions to show it at once. */
internal interface EngagementNotifier {
    fun changed(player: Player)
}

/** Without VersatiaInteractions nobody needs to know. */
@LocalBean
internal class SilentEngagementNotifier : EngagementNotifier {
    override fun changed(player: Player) = Unit
}

/** With VersatiaInteractions, its menus showing the player are redrawn, the trade button turning busy or in progress. */
@LocalBean
@Primary
@ConditionalOnPlugin("VersatiaInteractions")
internal class InteractionsEngagementNotifier(private val interactions: Interactions) : EngagementNotifier {
    override fun changed(player: Player) = interactions.refresh(player)
}
