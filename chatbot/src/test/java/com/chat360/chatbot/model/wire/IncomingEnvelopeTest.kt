package com.chat360.chatbot.model.wire

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingEnvelopeTest {

    @Test
    fun `update_status flips to LiveChatEnded regardless of other fields`() {
        val envelope = RawSocketEnvelope(user = "admin", update_status = true)
        assertTrue(envelope.toIncomingEvent() is IncomingSocketEvent.LiveChatEnded)
    }

    @Test
    fun `assigned_user present maps to AgentAssigned with the right fields`() {
        val data = buildJsonObject {
            put("assigned_user", buildJsonObject {
                put("operator_name", "Jordan")
                put("user_designation", "Support Lead")
                put("avatar", "https://example.com/a.png")
            })
        }
        val envelope = RawSocketEnvelope(user = "bot", data = data)
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.AgentAssigned)
        val agent = (event as IncomingSocketEvent.AgentAssigned).agent
        assertEquals("Jordan", agent.name)
        assertEquals("Support Lead", agent.designation)
        assertEquals("https://example.com/a.png", agent.avatarUrl)
    }

    @Test
    fun `assigned_user takes precedence over an otherwise-normal bot node`() {
        val data = buildJsonObject {
            put("nodeType", "MULTI_CHOICE")
            put("questionText", "Pick one")
            put("assigned_user", buildJsonObject {
                put("operator_name", "Riley")
            })
        }
        val envelope = RawSocketEnvelope(user = "bot", data = data)
        assertTrue(envelope.toIncomingEvent() is IncomingSocketEvent.AgentAssigned)
    }

    @Test
    fun `empty assigned_user object is ignored, falls through to normal bot dispatch`() {
        val data = buildJsonObject {
            put("nodeType", "TEXT")
            put("questionText", "Hello")
            put("assigned_user", buildJsonObject {})
        }
        val envelope = RawSocketEnvelope(user = "bot", data = data)
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.BotMessage)
    }

    @Test
    fun `admin-authored message dispatches as BotMessage tagged AGENT`() {
        val data = buildJsonObject {
            put("nodeType", "TEXT")
            put("questionText", "Hi, how can I help?")
        }
        val envelope = RawSocketEnvelope(user = "admin", data = data)
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.BotMessage)
        assertEquals(BotNode.MessageAuthor.AGENT, (event as IncomingSocketEvent.BotMessage).node.author)
    }

    @Test
    fun `operator-authored message also dispatches as BotMessage tagged AGENT`() {
        val data = buildJsonObject {
            put("nodeType", "TEXT")
            put("questionText", "Following up on your request")
        }
        val envelope = RawSocketEnvelope(user = "operator", data = data)
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.BotMessage)
        assertEquals(BotNode.MessageAuthor.AGENT, (event as IncomingSocketEvent.BotMessage).node.author)
    }

    @Test
    fun `bot-authored message still dispatches tagged BOT (no regression)`() {
        val data = buildJsonObject {
            put("nodeType", "TEXT")
            put("questionText", "Hello from the bot")
        }
        val envelope = RawSocketEnvelope(user = "bot", data = data)
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.BotMessage)
        assertEquals(BotNode.MessageAuthor.BOT, (event as IncomingSocketEvent.BotMessage).node.author)
    }

    @Test
    fun `unrecognized user with no data falls through to Unhandled`() {
        val envelope = RawSocketEnvelope(user = "someone_else", data = null)
        assertTrue(envelope.toIncomingEvent() is IncomingSocketEvent.Unhandled)
    }

    // --- chatgpt_message streaming: text must stay raw/unstripped per chunk -----------------

    @Test
    fun `chatgpt_message chunk with nested data keeps its HTML tags unstripped`() {
        val data = buildJsonObject {
            put("questionText", "- <stro")
        }
        val envelope = RawSocketEnvelope(
            user = "bot",
            type = "chatgpt_message",
            data = data,
            stream_id = "s1",
            end_stream = false,
        )
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.BotMessage)
        val node = (event as IncomingSocketEvent.BotMessage).node
        assertEquals("s1", node.streamId)
        assertEquals(false, node.streamEnded)
        // Not "- <stro" with the dangling tag start stripped away - a lone chunk must never run
        // stripHtml(), since the matching ">" may only arrive in the next chunk.
        assertEquals("- <stro", node.text)
    }

    @Test
    fun `chatgpt_message chunk with no nested data keeps its HTML tags unstripped`() {
        val envelope = RawSocketEnvelope(
            user = "bot",
            type = "chatgpt_message",
            message = kotlinx.serialization.json.JsonPrimitive("ng>Design</strong>: modern"),
            stream_id = "s1",
            end_stream = true,
        )
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.BotMessage)
        val node = (event as IncomingSocketEvent.BotMessage).node
        assertEquals("ng>Design</strong>: modern", node.text)
    }

    @Test
    fun `merging raw chunks split mid-tag then parsing once recovers real bold formatting`() {
        // Reproduces the real-world split: chunk N ends mid open-tag, chunk N+1 finishes it.
        val chunk1 = "Key features.\n- <stro"
        val chunk2 = "ng>Design</strong>: modern and sporty"
        val parsed = com.chat360.chatbot.model.richtext.RichTextParser.parse(chunk1 + chunk2)
        val textRuns = parsed.runs.filterIsInstance<com.chat360.chatbot.model.richtext.RichText.TextRun>()
        assertEquals("Key features.\n- ", textRuns[0].text)
        assertEquals(false, textRuns[0].bold)
        assertEquals("Design", textRuns[1].text)
        assertEquals(true, textRuns[1].bold)
        assertEquals(": modern and sporty", textRuns[2].text)
        assertEquals(false, textRuns[2].bold)
        // Parsing each chunk individually before concatenating is exactly the bug this reproduces:
        // the dangling "<stro" / "ng>" halves never form a complete tag on their own, so a naive
        // per-chunk parse would leave "<strong>" as literal text instead of real bold.
        val parsedIndividuallyThenJoined =
            com.chat360.chatbot.model.richtext.RichTextParser.parse(chunk1).runs +
                com.chat360.chatbot.model.richtext.RichTextParser.parse(chunk2).runs
        val joinedText = parsedIndividuallyThenJoined
            .filterIsInstance<com.chat360.chatbot.model.richtext.RichText.TextRun>()
            .joinToString("") { it.text }
        assertTrue(joinedText.contains("<strong>"))
    }

    // --- `time` (server send time) parsing - see RawSocketEnvelope.time's doc ---------------

    @Test
    fun `bot message's time field parses into the node's timestampMs, UTC`() {
        val data = buildJsonObject { put("nodeType", "TEXT"); put("questionText", "Hi") }
        val envelope = RawSocketEnvelope(user = "bot", data = data, time = "12/08/2026 20:04:53")
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.BotMessage)
        val node = (event as IncomingSocketEvent.BotMessage).node
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = requireNotNull(node.timestampMs)
        }
        assertEquals(2026, calendar.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.AUGUST, calendar.get(java.util.Calendar.MONTH))
        assertEquals(12, calendar.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(20, calendar.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(4, calendar.get(java.util.Calendar.MINUTE))
        assertEquals(53, calendar.get(java.util.Calendar.SECOND))
    }

    @Test
    fun `echoed user message's time field parses into timestampMs`() {
        val envelope = RawSocketEnvelope(user = "end_user", message = kotlinx.serialization.json.JsonPrimitive("hi"), time = "12/08/2026 20:04:40")
        val event = envelope.toIncomingEvent()
        assertTrue(event is IncomingSocketEvent.EchoedUserMessage)
        assertTrue((event as IncomingSocketEvent.EchoedUserMessage).timestampMs != null)
    }

    @Test
    fun `missing time field leaves timestampMs null rather than throwing`() {
        val data = buildJsonObject { put("nodeType", "TEXT"); put("questionText", "Hi") }
        val envelope = RawSocketEnvelope(user = "bot", data = data)
        val event = envelope.toIncomingEvent()
        assertEquals(null, (event as IncomingSocketEvent.BotMessage).node.timestampMs)
    }

    @Test
    fun `timestamp_int wins over time when both present`() {
        val data = buildJsonObject { put("nodeType", "TEXT"); put("questionText", "Hi") }
        // Deliberately mismatched from `time` so the assertion proves timestamp_int, not time, won.
        val envelope = RawSocketEnvelope(user = "bot", data = data, time = "01/01/2000 00:00:00", timestamp_int = "1786565354.987089")
        val event = envelope.toIncomingEvent()
        assertEquals(1786565354987L, (event as IncomingSocketEvent.BotMessage).node.timestampMs)
    }

    @Test
    fun `unparseable time field leaves timestampMs null rather than throwing`() {
        val data = buildJsonObject { put("nodeType", "TEXT"); put("questionText", "Hi") }
        val envelope = RawSocketEnvelope(user = "bot", data = data, time = "not-a-date")
        val event = envelope.toIncomingEvent()
        assertEquals(null, (event as IncomingSocketEvent.BotMessage).node.timestampMs)
    }
}
