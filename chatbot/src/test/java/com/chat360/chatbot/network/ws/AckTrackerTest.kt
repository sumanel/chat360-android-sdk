package com.chat360.chatbot.network.ws

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AckTrackerTest {

    @Test
    fun `retries on the configured schedule before finally timing out`() = runTest {
        var timedOutId: String? = null
        var resends = 0
        val tracker = AckTracker(scope = this, retryDelaysMs = listOf(1_000, 2_000), onTimeout = { timedOutId = it })

        tracker.trackSend("msg-1") { resends++ }
        advanceTimeBy(999)
        assertEquals(0, resends)
        assertNull(timedOutId)

        advanceTimeBy(2) // first delay elapsed: resend, not yet timed out
        assertEquals(1, resends)
        assertNull(timedOutId)

        advanceTimeBy(1_999)
        assertNull(timedOutId)
        advanceTimeBy(2) // second (final) delay elapsed: give up, no further resend
        assertEquals(1, resends)
        assertEquals("msg-1", timedOutId)
    }

    @Test
    fun `acknowledging before the final timeout cancels it and stops retries`() = runTest {
        var timedOutId: String? = null
        var resends = 0
        val tracker = AckTracker(scope = this, retryDelaysMs = listOf(1_000, 2_000), onTimeout = { timedOutId = it })

        tracker.trackSend("msg-1") { resends++ }
        advanceTimeBy(1_001) // one retry fires
        tracker.acknowledge("msg-1")
        advanceTimeBy(10_000)
        assertEquals(1, resends)
        assertNull(timedOutId)
    }

    @Test
    fun `each chat_msg_id is tracked independently`() = runTest {
        val timedOut = mutableListOf<String>()
        val tracker = AckTracker(scope = this, retryDelaysMs = listOf(10_000), onTimeout = { timedOut.add(it) })

        tracker.trackSend("msg-1") {}
        advanceTimeBy(4_000)
        tracker.trackSend("msg-2") {}
        tracker.acknowledge("msg-1")

        advanceTimeBy(6_001) // msg-1 acked; msg-2 still short of its own 10s window
        assertEquals(emptyList<String>(), timedOut)

        advanceTimeBy(4_000) // msg-2 now past its 10s window
        assertEquals(listOf("msg-2"), timedOut)
    }
}
