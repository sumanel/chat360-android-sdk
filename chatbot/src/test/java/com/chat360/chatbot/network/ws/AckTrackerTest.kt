package com.chat360.chatbot.network.ws

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AckTrackerTest {

    @Test
    fun `times out at exactly ackTimeoutMs with no ack`() = runTest {
        var timedOutId: String? = null
        val tracker = AckTracker(scope = this, ackTimeoutMs = 10_000, onTimeout = { timedOutId = it })

        tracker.trackSend("msg-1")
        advanceTimeBy(9_999)
        assertNull(timedOutId)
        advanceTimeBy(2)
        assertEquals("msg-1", timedOutId)
    }

    @Test
    fun `acknowledging before the timeout cancels it`() = runTest {
        var timedOutId: String? = null
        val tracker = AckTracker(scope = this, ackTimeoutMs = 10_000, onTimeout = { timedOutId = it })

        tracker.trackSend("msg-1")
        advanceTimeBy(5_000)
        tracker.acknowledge("msg-1")
        advanceTimeBy(10_000)
        assertNull(timedOutId)
    }

    @Test
    fun `each chat_msg_id is tracked independently`() = runTest {
        val timedOut = mutableListOf<String>()
        val tracker = AckTracker(scope = this, ackTimeoutMs = 10_000, onTimeout = { timedOut.add(it) })

        tracker.trackSend("msg-1")
        advanceTimeBy(4_000)
        tracker.trackSend("msg-2")
        tracker.acknowledge("msg-1")

        advanceTimeBy(6_001) // msg-1 acked; msg-2 still short of its own 10s window
        assertEquals(emptyList<String>(), timedOut)

        advanceTimeBy(4_000) // msg-2 now past its 10s window
        assertEquals(listOf("msg-2"), timedOut)
    }
}
