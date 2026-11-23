package dev.versatia.trade.log

import dev.versatia.Phase
import dev.versatia.di.LocalBean
import dev.versatia.lifecycle.AutoInvoke
import dev.versatia.trade.TradePlugin
import dev.versatia.trade.config.TradeSettings
import dev.versatia.trade.exchange.ExchangeOutcome
import io.papermc.paper.datacomponent.DataComponentTypes
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Every completed trade as one line in `logs/trades-<date>.log` of the data folder, written on a
 * thread of its own in the order of the trades; the lines still waiting are written when the
 * plugin stops. See docs/trade.md.
 */
@LocalBean
internal class TradeLog(private val plugin: TradePlugin, private val settings: TradeSettings, private val logger: Logger) {
    private var writer: ExecutorService? = null

    fun record(first: Player, second: Player, done: ExchangeOutcome.Done) {
        if (!settings.log) return
        val line = "${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)} ${side(first, done.firstGave, done.firstMoney)} | ${side(second, done.secondGave, done.secondMoney)}\n"
        val file = plugin.dataFolder.toPath().resolve("logs").resolve("trades-${LocalDate.now()}.log")
        val executor = writer ?: Executors.newSingleThreadExecutor { Thread(it, "VersatiaTrade log") }.also { writer = it }
        executor.execute {
            try {
                Files.createDirectories(file.parent)
                Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Could not write the trade log $file: $line", e)
            }
        }
    }

    @AutoInvoke(Phase.PLUGIN_STOP)
    fun stop() {
        val executor = writer ?: return
        writer = null
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) logger.warning("The trade log did not finish writing within 5 seconds")
    }

    /** `Alice (uuid) gave [3 × minecraft:diamond, 1 × minecraft:iron_sword "Fang"] and 100`. */
    private fun side(player: Player, items: List<ItemStack>, money: BigDecimal) =
        "${player.name} (${player.uniqueId}) gave [${items.joinToString(transform = ::describe)}] and ${money.toPlainString()}"

    private fun describe(item: ItemStack): String {
        val name = item.getData(DataComponentTypes.CUSTOM_NAME)?.let { " \"${PlainTextComponentSerializer.plainText().serialize(it)}\"" }.orEmpty()
        return "${item.amount} × ${item.type.key.asString()}$name"
    }
}
