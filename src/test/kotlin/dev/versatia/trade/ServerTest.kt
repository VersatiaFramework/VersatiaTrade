package dev.versatia.trade

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

/** A test against a mocked Paper server, for real inventories and items. */
abstract class ServerTest {
    protected lateinit var server: ServerMock

    @BeforeTest
    fun mockServer() {
        server = MockBukkit.mock()
    }

    @AfterTest
    fun unmockServer() = MockBukkit.unmock()
}
