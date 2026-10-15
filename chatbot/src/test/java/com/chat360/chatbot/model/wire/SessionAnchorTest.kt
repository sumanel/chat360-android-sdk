package com.chat360.chatbot.model.wire

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The countdown anchors to the session's `updated_at` (fixed once the session exists) - never to `created_at`. */
class SessionAnchorTest {
    private fun eventOf(session: JsonObject) = RawSocketEnvelope(data_type = "session_time_hyundai", session = session).toIncomingEvent()

    @Test
    fun `updated_at is the anchor even when created_at differs`() {
        val event = eventOf(buildJsonObject {
            put("created_at", "2026-01-01T10:00:00Z")
            put("updated_at", "2026-01-01T10:05:00Z")
        }) as IncomingSocketEvent.SessionTime
        assertEquals(java.time.Instant.parse("2026-01-01T10:05:00Z").toEpochMilli(), event.createdAtMs)
    }

    @Test
    fun `a reply with only created_at starts no timer`() {
        assertTrue(eventOf(buildJsonObject { put("created_at", "2026-01-01T10:00:00Z") }) !is IncomingSocketEvent.SessionTime)
    }

    @Test
    fun `an unreadable updated_at starts no timer`() {
        val event = eventOf(buildJsonObject {
            put("created_at", "2026-01-01T10:00:00Z")
            put("updated_at", "not a date")
        })
        assertTrue(event !is IncomingSocketEvent.SessionTime)
    }

    @Test
    fun `a reply with neither starts no timer`() {
        assertTrue(eventOf(buildJsonObject { put("room_id", "r") }) !is IncomingSocketEvent.SessionTime)
    }
}
