package dev.versatia.trade.identity

import dev.versatia.EnhancementContext
import dev.versatia.EnhancementProcessor
import dev.versatia.StateKey
import dev.versatia.di.BeanResolver
import dev.versatia.engine.descriptor.ClassTarget
import dev.versatia.engine.descriptor.EnhancedElement
import dev.versatia.engine.descriptor.Parameter
import dev.versatia.failure
import dev.versatia.targetAs
import dev.versatia.trade.ItemIdentity

/**
 * Registers the `@TradeItemIdentity` beans of a plugin with VersatiaTrade once its beans exist, and
 * withdraws them when the plugin stops: the registration is an entry of the plugin's state, closed when
 * that state goes. Loaded by VersatiaCore through the class loader of the plugin using the annotation,
 * which reaches this class through `softdepend: [VersatiaTrade]`. See docs/trade.md.
 */
class ItemIdentityEnhancementProcessor : EnhancementProcessor {
    override fun process(elements: List<EnhancedElement>, context: EnhancementContext) = context.onPluginEnhanced { register(elements, context) }

    private fun register(elements: List<EnhancedElement>, context: EnhancementContext) {
        val beans = context.state[BeanResolver.KEY]
            ?: throw context.failure("${elements.first().target.className} is a @TradeItemIdentity, but the plugin has no bean container; extend VersatiaPlugin")
        val registry = beans.resolve("@TradeItemIdentity", listOf(Parameter(ItemIdentities::class.java.name))).single() as? ItemIdentities
            ?: throw context.failure("the item identities of VersatiaTrade cannot be reached; is VersatiaTrade enabled before this plugin (softdepend)?")
        val identities = elements.map { element ->
            val target = element.targetAs<ClassTarget>(this)
            beans.instanceOf(target.className) as? ItemIdentity
                ?: throw context.failure("${target.className} is a @TradeItemIdentity, but does not implement ${ItemIdentity::class.java.name}")
        }
        registry.register(context.plugin.name, identities)
        context.state.getOrPut(REGISTERED) { Registered(registry, context.plugin.name) }
    }

    /** What a plugin registered, withdrawn when its state goes. */
    private class Registered(private val registry: ItemIdentities, private val owner: String) : AutoCloseable {
        override fun close() {
            registry.withdraw(owner)
        }
    }

    private companion object {
        val REGISTERED = StateKey<Registered>("tradeItemIdentities")
    }
}
