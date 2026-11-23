package dev.versatia.trade.money

import dev.versatia.VersatiaEngine
import dev.versatia.loadServiceOrNull
import java.math.BigDecimal
import net.milkbowl.vault2.economy.Economy
import org.bukkit.entity.Player

/** VaultUnlocked's economy API, in the economy's default currency; [pluginName] is told to the economy with every call. */
internal class UnlockedFunds(private val economy: Economy, private val pluginName: String) : Funds {
    override fun balance(player: Player): BigDecimal = economy.balance(pluginName, player.uniqueId)

    override fun withdraw(player: Player, amount: BigDecimal) = economy.withdraw(pluginName, player.uniqueId, amount).transactionSuccess()

    override fun deposit(player: Player, amount: BigDecimal) = economy.deposit(pluginName, player.uniqueId, amount).transactionSuccess()

    override fun format(amount: BigDecimal): String = economy.format(pluginName, amount)

    companion object {
        /** The enabled VaultUnlocked economy of the server, null when no economy provides one. */
        fun find(pluginName: String) = VersatiaEngine.loadServiceOrNull<Economy>()?.takeIf { it.isEnabled }?.let { UnlockedFunds(it, pluginName) }
    }
}
