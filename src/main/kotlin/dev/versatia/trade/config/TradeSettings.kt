package dev.versatia.trade.config

import dev.versatia.config.Value
import dev.versatia.config.ValueMapper
import dev.versatia.di.LocalBean
import dev.versatia.util.config.ConfigurationValueException
import dev.versatia.util.config.getDoubleListOrThrow
import dev.versatia.util.config.getDurationOrThrow
import dev.versatia.util.config.getIntOrThrow
import dev.versatia.util.config.pathOf
import java.math.BigDecimal
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import net.kyori.adventure.key.Key
import org.bukkit.configuration.ConfigurationSection

/** `config.yml` of the data folder, read at start and at every reload. See docs/trade.md. */
@LocalBean
internal class TradeSettings {
    @Value("trade.countdown")
    var countdown = 3.seconds

    @Value("trade.max-distance", mapper = Blocks::class)
    var maxDistance = 10

    @Value("money.steps", mapper = MoneySteps::class)
    var moneySteps: List<BigDecimal> = listOf(1, 10, 100, 1000).map { BigDecimal(it) }

    @Value("request.timeout", mapper = PositiveDuration::class)
    var requestTimeout = 30.seconds

    @Value("log.enabled")
    var log = true

    @Value("window.icons", mapper = WindowIcons::class)
    var icons: Map<WindowIcon, Key> = emptyMap()

    @Value("sounds", mapper = TradeSounds::class)
    var sounds: Map<TradeSound, Key?> = emptyMap()
}

/** A distance in blocks, at least one. */
internal object Blocks : ValueMapper<Int> {
    override fun map(section: ConfigurationSection, key: String) = section.getIntOrThrow(key).also {
        if (it < 1) throw ConfigurationValueException("Value at \"${section.pathOf(key)}\" must be at least 1 block, got $it")
    }
}

/** A wait that must not be zero, such as `30s`. */
internal object PositiveDuration : ValueMapper<Duration> {
    override fun map(section: ConfigurationSection, key: String) = section.getDurationOrThrow(key).also {
        if (it == Duration.ZERO) throw ConfigurationValueException("Value at \"${section.pathOf(key)}\" must be longer than zero")
    }
}

/** `money.steps`: one to four positive whole amounts, one button each. */
internal object MoneySteps : ValueMapper<List<BigDecimal>> {
    override fun map(section: ConfigurationSection, key: String): List<BigDecimal> {
        val steps = section.getDoubleListOrThrow(key).map { amount ->
            BigDecimal.valueOf(amount).stripTrailingZeros().takeIf { it.signum() > 0 }
                ?: throw ConfigurationValueException("Element $amount of \"${section.pathOf(key)}\" is not a positive amount")
        }
        if (steps.size !in 1..MAX_STEPS) throw ConfigurationValueException("\"${section.pathOf(key)}\" must list 1 to $MAX_STEPS amounts, one button each; got ${steps.size}")
        return steps
    }

    const val MAX_STEPS = 4
}
