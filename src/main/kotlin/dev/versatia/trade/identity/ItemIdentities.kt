package dev.versatia.trade.identity

import dev.versatia.di.ExposeSingleton
import dev.versatia.trade.ItemIdentity
import dev.versatia.trade.offer.ItemMatcher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.Logger
import org.bukkit.inventory.ItemStack

/**
 * The identities other plugins contribute with `@TradeItemIdentity`, asked in the order their plugins
 * started: the first that knows either of two stacks decides whether they are the same item; when
 * none knows them, Bukkit's similarity does. An identity that throws counts as not knowing the item,
 * and is logged once. Exposed, so that the processor of `@TradeItemIdentity`, running for the
 * contributing plugin, reaches it through that plugin's beans. See docs/trade.md.
 */
@ExposeSingleton
internal class ItemIdentities(private val logger: Logger) : ItemMatcher {
    private val registered = CopyOnWriteArrayList<Registered>()
    private val failed = HashSet<String>()

    private class Registered(val owner: String, val identity: ItemIdentity)

    fun register(owner: String, identities: List<ItemIdentity>) = identities.forEach { registered += Registered(owner, it) }

    fun withdraw(owner: String) = registered.removeIf { it.owner == owner }

    override fun same(a: ItemStack, b: ItemStack): Boolean {
        for (entry in registered) {
            val first = identityOf(entry, a)
            val second = identityOf(entry, b)
            if (first != null || second != null) return first == second
        }
        return a.isSimilar(b)
    }

    private fun identityOf(entry: Registered, item: ItemStack) = try {
        entry.identity.of(item)
    } catch (e: RuntimeException) {
        if (failed.add(entry.owner)) logger.log(Level.SEVERE, "The item identity of ${entry.owner} failed; its later failures are not logged", e)
        null
    }
}
