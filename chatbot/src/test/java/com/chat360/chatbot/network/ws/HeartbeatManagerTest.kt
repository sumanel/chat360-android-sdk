package com.chat360.chatbot.network.ws

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatManagerTest {

    @Test
    fun `slow callback fires only when no pong arrives within the wait window`() = runTest {
        var pingsSent = 0
        val slowStates = mutableListOf<Boolean>()
        val manager = HeartbeatManager(
            scope = this,
            pingWaitingTimerMs = 2_000,
            sendPing = { pingsSent++ },
            onSlowConnectionChanged = { slowStates.add(it) },
        )

        manager.start()
        advanceTimeBy(2_001) // idle window elapses, ping sent
        assertEquals(1, pingsSent)
        assertTrue(slowStates.isEmpty())

        advanceTimeBy(2_001) // no pong arrives within the wait window
        assertEquals(listOf(true), slowStates)
    }

    @Test
    fun `a pong within the wait window cancels the slow signal for that cycle`() = runTest {
        var pingsSent = 0
        val slowStates = mutableListOf<Boolean>()
        val manager = HeartbeatManager(
            scope = this,
            pingWaitingTimerMs = 2_000,
            sendPing = { pingsSent++ },
            onSlowConnectionChanged = { slowStates.add(it) },
        )

        manager.start()
        advanceTimeBy(2_001) // ping sent
        assertEquals(1, pingsSent)

        manager.onMessageReceived(isPong = true) // pong arrives in time
        advanceTimeBy(1_000) // well before the next cycle's ping is even due (at +2000 from here)
        assertTrue(slowStates.isEmpty())
    }

    @Test
    fun `any inbound frame reschedules the next ping, not only a pong`() = runTest {
        var pingsSent = 0
        val manager = HeartbeatManager(
            scope = this,
            pingWaitingTimerMs = 2_000,
            sendPing = { pingsSent++ },
            onSlowConnectionChanged = {},
        )

        manager.start()
        advanceTimeBy(1_000)
        manager.onMessageReceived(isPong = false) // a normal bot message resets the idle timer
        advanceTimeBy(1_999) // 1000 + 1999 = 2999 since start, but only 1999 since the reset
        assertEquals(0, pingsSent)
        advanceTimeBy(2)
        assertEquals(1, pingsSent)
    }
}
