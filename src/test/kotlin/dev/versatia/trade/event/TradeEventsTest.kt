package dev.versatia.trade.event

import dev.versatia.trade.config.configName
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.HandlerList

class TradeEventsTest {
    private fun event() = object : DeniableTradeEvent() {
        override fun getHandlers() = HandlerList()
    }

    @Test
    fun `a denial cancels the event and keeps its reason`() {
        val event = event()
        val reason = Component.text("No trading in combat")
        event.deny(reason)
        assertTrue(event.isCancelled)
        assertEquals(reason, event.reason)
    }

    @Test
    fun `a plain cancel has no reason, and allowing the event again drops the reason`() {
        val event = event()
        event.isCancelled = true
        assertNull(event.reason)
        event.deny(Component.text("No"))
        event.isCancelled = false
        assertFalse(event.isCancelled)
        assertNull(event.reason)
    }

    @Test
    fun `every way a trade ends without an exchange has its message`() {
        val stream = javaClass.getResourceAsStream("/resources/messages/trade.yml")!!
        val messages = YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8))
        for (cause in TradeCancelledEvent.Cause.entries) assertTrue(messages.isString("trade.ended.${cause.configName}"), cause.name)
        assertTrue(messages.isString("deny.unavailable"))
        assertTrue(messages.isString("trade.item-denied"))
    }
}
