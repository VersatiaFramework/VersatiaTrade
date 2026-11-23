package dev.versatia.trade.money

import dev.versatia.VersatiaEngine
import dev.versatia.loadServiceOrNull
import java.math.BigDecimal
import net.milkbowl.vault.economy.Economy
import org.bukkit.entity.Player

/**
 * The older Vault economy API, which counts in `double`. VaultUnlocked deprecates it in favour of
 * its own, but most economy plugins still provide only this one, so a trade falls back to it when
 * no economy provides VaultUnlocked's.
 */
@Suppress("DEPRECATION")
internal class LegacyFunds(private val economy: Economy) : Funds {
    override fun balance(player: Player): BigDecimal = BigDecimal.valueOf(economy.getBalance(player))

    override fun withdraw(player: Player, amount: BigDecimal) = economy.withdrawPlayer(player, amount.toDouble()).transactionSuccess()

    override fun deposit(player: Player, amount: BigDecimal) = economy.depositPlayer(player, amount.toDouble()).transactionSuccess()

    override fun format(amount: BigDecimal): String = economy.format(amount.toDouble())

    companion object {
        /** The enabled Vault economy of the server, null when no economy provides one. */
        fun find() = VersatiaEngine.loadServiceOrNull<Economy>()?.takeIf { it.isEnabled }?.let(::LegacyFunds)
    }
}
