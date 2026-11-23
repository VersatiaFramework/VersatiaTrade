package dev.versatia.trade.config

import dev.versatia.config.ValueMapper
import dev.versatia.util.config.ConfigurationValueException
import dev.versatia.util.config.getConfigurationSectionOrThrow
import dev.versatia.util.config.getStringOrThrow
import dev.versatia.util.config.pathOf
import net.kyori.adventure.key.InvalidKeyException
import net.kyori.adventure.key.Key
import org.bukkit.configuration.ConfigurationSection

/** The name of a constant in the configuration: lower case, words joined by `-`. */
internal val Enum<*>.configName get() = name.lowercase().replace('_', '-')

/** The parts of the trade window that show an icon of their own, as `window.icons` names them. */
internal enum class WindowIcon {
    DIVIDER,
    ACCEPT,
    ACCEPTED,
    BLOCKED,
    STATUS,
    COUNTDOWN,
    MONEY,
    PARTNER_MONEY,
    PARTNER_ACCEPTED,
    PARTNER_WAITING,
}

/** The moments of a trade a sound marks, as `sounds` names them. */
internal enum class TradeSound {
    /** A request reached its receiver (without VersatiaInteractions). */
    REQUEST,

    /** An offer changed; both hear it. */
    CHANGED,

    /** A player accepted; both hear it. */
    ACCEPTED,

    /** The exchange happened; both hear it. */
    COMPLETED,

    /** The exchange did not happen, or a click was not possible. */
    REFUSED,

    /** The trade ended without an exchange. */
    CANCELLED,
}

/** `window.icons`: the item model of every part. */
internal object WindowIcons : ValueMapper<Map<WindowIcon, Key>> {
    override fun map(section: ConfigurationSection, key: String) = readEvery<WindowIcon>(section.getConfigurationSectionOrThrow(key), allowEmpty = false).mapValues { it.value!! }
}

/** `sounds`: the sound of every moment, `''` for none. */
internal object TradeSounds : ValueMapper<Map<TradeSound, Key?>> {
    override fun map(section: ConfigurationSection, key: String) = readEvery<TradeSound>(section.getConfigurationSectionOrThrow(key), allowEmpty = true)
}

/** Every constant of [T] as a key of [section], each holding a namespaced key; `''` gives null where [allowEmpty]. */
private inline fun <reified T : Enum<T>> readEvery(section: ConfigurationSection, allowEmpty: Boolean): Map<T, Key?> {
    val names = enumValues<T>().associateBy { it.configName }
    section.getKeys(false).firstOrNull { it !in names }?.let {
        throw ConfigurationValueException("Unknown entry \"$it\" in \"${section.currentPath}\"; use ${names.keys.joinToString()}")
    }
    return names.entries.associate { (name, constant) ->
        if (!section.contains(name)) throw ConfigurationValueException("\"${section.currentPath}\" lacks $name")
        val value = section.getStringOrThrow(name).trim()
        constant to when {
            value.isEmpty() -> if (allowEmpty) null else throw ConfigurationValueException("Value at \"${section.pathOf(name)}\" must not be empty")
            else -> try {
                Key.key(value)
            } catch (e: InvalidKeyException) {
                throw ConfigurationValueException("Value at \"${section.pathOf(name)}\" is not a namespaced key such as minecraft:paper: \"$value\" (${e.message})")
            }
        }
    }
}
