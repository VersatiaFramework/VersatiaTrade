package dev.versatia.trade

import dev.versatia.ConditionalOnPlugin
import dev.versatia.Phase
import dev.versatia.VersatiaEnhancement
import dev.versatia.di.LocalBean
import org.bukkit.inventory.ItemStack

/**
 * Tells VersatiaTrade which stacks are the same item, for a plugin whose items change while they lie
 * in an inventory, a lore rendered for the holder say: `@TradeItemIdentity class RpgItemIdentity(...)
 * : ItemIdentity`. Without one, two stacks are the same item when Bukkit finds them similar: the same
 * type and the same components.
 */
interface ItemIdentity {
    /**
     * The identity of [item] for this plugin, null for an item it does not know. Two stacks are the
     * same item in a trade when the first identity knowing either of them gives both equal values;
     * a stack it knows and one it does not are different items. Called often and on the server
     * thread: cheap, and free of side effects.
     */
    fun of(item: ItemStack): Any?
}

/**
 * Contributes an [ItemIdentity] to VersatiaTrade: the annotated class implements it and is a local
 * bean of its plugin, so its constructor is injected. The annotation carries
 * `@ConditionalOnPlugin("VersatiaTrade")`: on a server without VersatiaTrade the class is skipped and
 * never loaded, so the plugin lists VersatiaTrade under `softdepend:` and compiles against it with
 * `compileOnly`. Identities are asked in the order their plugins started; one is withdrawn when its
 * plugin stops.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@ConditionalOnPlugin("VersatiaTrade")
@LocalBean
@VersatiaEnhancement(TradeProcessors.ITEM_IDENTITY, enhancementPhase = Phase.PLUGIN_START_ONLY)
annotation class TradeItemIdentity

/** The processors of VersatiaTrade, by name: the descriptor of a contributing plugin names them. */
object TradeProcessors {
    const val ITEM_IDENTITY = "dev.versatia.trade.identity.ItemIdentityEnhancementProcessor"
}
