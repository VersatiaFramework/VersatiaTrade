package dev.versatia.trade.session

import dev.versatia.di.LocalBean
import dev.versatia.messages.Messages
import dev.versatia.text.Argument
import dev.versatia.text.arg
import dev.versatia.trade.config.TradeSettings
import dev.versatia.trade.config.TradeSound
import dev.versatia.trade.offer.Side
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/** The messages and the sounds of trades, in one place; every text comes from `messages/trade.yml`. */
@LocalBean
internal class TradeTexts(private val messages: Messages, private val settings: TradeSettings) {
    fun get(key: String, vararg arguments: Argument): Component = messages[key].get(*arguments)

    fun lines(key: String, vararg arguments: Argument) = messages.getOrNull(key)?.lines(*arguments).orEmpty()

    fun send(player: Player, key: String, vararg arguments: Argument) = messages[key].send(player, *arguments)

    fun play(player: Player, sound: TradeSound) {
        player.playSound(Sound.sound(settings.sounds[sound] ?: return, Sound.Source.UI, 1f, 1f))
    }

    /** The sentence telling the player on [side] where the trade stands, [partner] being the other player. */
    fun status(status: TradeStatus, side: Side, partner: Player, secondsLeft: Int?): Component {
        val partnerArgument = arg("partner", partner)
        if (secondsLeft != null && status == TradeStatus.Ready) return get("window.status.countdown", partnerArgument, arg("seconds", secondsLeft))
        fun own(target: Side) = if (target == side) "self" else "partner"
        return when (status) {
            TradeStatus.Empty -> get("window.status.empty", partnerArgument)
            TradeStatus.Ready -> get("window.status.ready", partnerArgument)
            is TradeStatus.NoRoom -> get("window.status.no-room-${own(status.side)}", partnerArgument, arg("count", status.items))
            is TradeStatus.NoMoney -> get("window.status.no-money-${own(status.side)}", partnerArgument)
            TradeStatus.NoEconomy -> get("window.status.no-economy", partnerArgument)
            is TradeStatus.Short -> get("window.status.short-${own(status.side)}", partnerArgument)
        }
    }
}
