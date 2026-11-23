package dev.versatia.trade.money

import dev.versatia.ConditionalOnPlugin
import dev.versatia.di.LocalBean
import dev.versatia.di.Primary
import dev.versatia.trade.TradePlugin
import java.math.BigDecimal
import org.bukkit.entity.Player

/** The money of players as a trade moves it, in the server's economy. Server thread only. */
internal interface Funds {
    fun balance(player: Player): BigDecimal

    /** Takes [amount] from [player]; false when the economy refuses, for want of money say. */
    fun withdraw(player: Player, amount: BigDecimal): Boolean

    /** Gives [amount] to [player]; false when the economy refuses. */
    fun deposit(player: Player, amount: BigDecimal): Boolean

    /** [amount] as the economy writes it, `1,250.00 coins` say. */
    fun format(amount: BigDecimal): String
}

/**
 * The server's economy, looked up whenever a trade needs it, since an economy plugin may register
 * after this plugin started; null while there is none, and trades are then items only.
 */
internal interface Money {
    fun funds(): Funds?
}

/** Without Vault on the server, trades are items only. */
@LocalBean
internal class NoMoney : Money {
    override fun funds() = null
}

/**
 * The economy behind Vault: VaultUnlocked's API, which counts in `BigDecimal`, when its classes
 * exist and an economy provides it, else the older Vault API most economy plugins provide. The
 * classes of each API are touched only by [UnlockedFunds] and [LegacyFunds], loaded once it is
 * known they exist.
 */
@LocalBean
@Primary
@ConditionalOnPlugin("Vault")
internal class VaultMoney(private val plugin: TradePlugin) : Money {
    private val unlocked = runCatching { Class.forName(UNLOCKED_ECONOMY, false, javaClass.classLoader) }.isSuccess

    override fun funds() = (if (unlocked) UnlockedFunds.find(plugin.name) else null) ?: LegacyFunds.find()

    private companion object {
        const val UNLOCKED_ECONOMY = "net.milkbowl.vault2.economy.Economy"
    }
}
