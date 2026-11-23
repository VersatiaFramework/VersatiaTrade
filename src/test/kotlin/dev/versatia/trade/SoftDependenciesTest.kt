package dev.versatia.trade

import dev.versatia.ConditionalOnPlugin
import dev.versatia.VersatiaInternalApi
import dev.versatia.engine.descriptor.MemberTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Trade with and without its soft dependencies, as the generated descriptor tells VersatiaCore: every
 * element of a class naming another plugin's classes carries that plugin's `@ConditionalOnPlugin`, so
 * VersatiaCore skips it, and never loads the class, on a server without the plugin; what remains
 * works alone.
 */
@OptIn(VersatiaInternalApi::class)
class SoftDependenciesTest {
    private fun conditionsOf(simpleName: String): Set<String> {
        val elements = VersatiaDescriptor.elements.filter { it.target.className.substringAfterLast('.') == simpleName }
        assertTrue(elements.isNotEmpty(), "$simpleName is not in the descriptor")
        return elements.flatMap { element ->
            val annotations = element.target.annotations + (element.target as? MemberTarget)?.classAnnotations.orEmpty()
            annotations.filter { it.annotationClass == ConditionalOnPlugin::class.java.name }.flatMap { it.arguments["value"] as List<*> }.map { it.toString() }
        }.toSet()
    }

    @Test
    fun `the trade button and requests through the menu exist only with VersatiaInteractions`() {
        for (name in listOf("TradeInteraction", "InteractionTradeRequests", "InteractionsEngagementNotifier"))
            assertEquals(setOf("VersatiaInteractions"), conditionsOf(name), name)
    }

    @Test
    fun `without VersatiaInteractions requests go by chat, and nobody is told of engagements`() {
        for (name in listOf("ChatTradeRequests", "SilentEngagementNotifier", "TradeSessions", "TradeWindows"))
            assertEquals(emptySet(), conditionsOf(name), name)
    }

    @Test
    fun `money needs Vault and the command needs CommandAPI`() {
        assertEquals(setOf("Vault"), conditionsOf("VaultMoney"))
        assertEquals(emptySet(), conditionsOf("NoMoney"))
        assertEquals(setOf("CommandAPI"), conditionsOf("TradeCommand"))
    }
}
