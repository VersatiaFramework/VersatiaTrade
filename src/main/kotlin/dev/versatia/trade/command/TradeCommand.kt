package dev.versatia.trade.command

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.commandAPICommand
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.stringArgument
import dev.jorel.commandapi.kotlindsl.subcommand
import dev.versatia.ConditionalOnPlugin
import dev.versatia.Phase
import dev.versatia.VersatiaEngine
import dev.versatia.lifecycle.AutoInvoke
import dev.versatia.onlinePlayers
import dev.versatia.playerOrNull
import dev.versatia.text.arg
import dev.versatia.trade.TradePlugin
import dev.versatia.trade.request.ChatTradeRequests
import dev.versatia.trade.request.TradeRequests
import dev.versatia.trade.session.TradeTexts
import org.bukkit.entity.Player

/**
 * `/trade <player>` asks a player to trade; without VersatiaInteractions also `/trade accept <player>`
 * and `/trade decline <player>`, which the request's chat line runs. Registered only while the
 * CommandAPI plugin is on the server.
 */
@ConditionalOnPlugin("CommandAPI")
internal object TradeCommand {
    const val NAME = "trade"
    const val PERMISSION = "versatia.trade.command.trade"

    @AutoInvoke(Phase.PLUGIN_START_ONLY)
    fun register(plugin: TradePlugin, requests: TradeRequests, texts: TradeTexts) = commandAPICommand(NAME, plugin) {
        withPermission(PERMISSION)
        if (requests.answersByCommand) for (answer in listOf("accept", "decline")) subcommand(answer) {
            stringArgument("player") {
                replaceSuggestions(ArgumentSuggestions.stringCollection { info -> (info.sender as? Player)?.let { (requests as? ChatTradeRequests)?.waitingSenders(it) }.orEmpty() })
            }
            playerExecutor { player, args -> requests.answer(player, args["player"] as String, yes = answer == "accept") }
        }
        stringArgument("player") {
            replaceSuggestions(ArgumentSuggestions.stringCollection { info ->
                val sender = info.sender as? Player
                VersatiaEngine.onlinePlayers.filter { it != sender && sender?.canSee(it) != false }.map { it.name }
            })
        }
        playerExecutor { player, args ->
            val name = args["player"] as String
            val target = VersatiaEngine.playerOrNull(name)?.takeIf(player::canSee)
            if (target == null) texts.send(player, "request.no-player", arg("target", name)) else requests.send(player, target)
        }
    }

    @AutoInvoke(Phase.PLUGIN_STOP)
    fun unregister() = CommandAPI.unregister(NAME, true)
}
