package com.chat360.chatbot.network.ws

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectManagerTest {

    @Test
    fun `delay doubles on each successive scheduled attempt`() = runTest {
        var reconnectCount = 0
        val manager = ReconnectManager(scope = this, baseDelayMs = 1000, reconnect = { reconnectCount++ })

        manager.scheduleReconnect(suppress = false) // delay = 1000 * 1
        advanceTimeBy(1001)
        assertEquals(1, reconnectCount)

        manager.scheduleReconnect(suppress = false) // delay = 1000 * 2
        advanceTimeBy(1999)
        assertEquals(1, reconnectCount)
        advanceTimeBy(2)
        assertEquals(2, reconnectCount)

        manager.scheduleReconnect(suppress = false) // delay = 1000 * 4
        advanceTimeBy(3999)
        assertEquals(2, reconnectCount)
        advanceTimeBy(2)
        assertEquals(3, reconnectCount)
    }

    @Test
    fun `onConnected resets the interval back to 1`() = runTest {
        var reconnectCount = 0
        val manager = ReconnectManager(scope = this, baseDelayMs = 1000, reconnect = { reconnectCount++ })

        manager.scheduleReconnect(suppress = false) // delay = 1000 * 1
        advanceTimeBy(1001)
        assertEquals(1, reconnectCount)

        manager.onConnected()

        manager.scheduleReconnect(suppress = false) // back to base delay, not doubled
        advanceTimeBy(999)
        assertEquals(1, reconnectCount)
        advanceTimeBy(2)
        assertEquals(2, reconnectCount)
    }

    @Test
    fun `suppress prevents scheduling entirely`() = runTest {
        var reconnectCount = 0
        val manager = ReconnectManager(scope = this, baseDelayMs = 1000, reconnect = { reconnectCount++ })

        manager.scheduleReconnect(suppress = true)
        advanceTimeBy(10_000)
        assertEquals(0, reconnectCount)
    }
}
