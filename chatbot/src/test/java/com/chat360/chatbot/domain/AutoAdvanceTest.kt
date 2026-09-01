package com.chat360.chatbot.domain

import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the "no next message" symptom on any bot node that doesn't need user
 * input: the server only advances the flow once the client re-submits that node's own targetId
 * as a system jump - the web widget does this automatically (see MessageHandlers.ts's
 * `[FLOW] auto-advance triggered` effect). Without an equivalent on Android, a chain of passive
 * messages (plain text, link cards, download-media notices, ...) renders only its first bubble
 * and stalls forever, even with no bug in the window-event flow at all.
 */
class AutoAdvanceTest {

    private fun invokeHandleIncoming(repo: ChatRepository, raw: String) {
        val method = ChatRepository::class.java.getDeclaredMethod(
            "handleIncoming",
            String::class.java,
            Function1::class.java,
        )
        method.isAccessible = true
        method.invoke(repo, raw, { _: String -> })
    }

    private fun captureSentFrames(repo: ChatRepository): MutableList<String> {
        val sent = mutableListOf<String>()
        val wsClientField = ChatRepository::class.java.getDeclaredField("wsClient")
        wsClientField.isAccessible = true
        val wsClient = wsClientField.get(repo) as com.chat360.chatbot.network.ws.Chat360WebSocketClient

        val fakeSocket = object : WebSocket {
            override fun request(): Request = throw UnsupportedOperationException()
            override fun queueSize(): Long = 0
            override fun send(text: String): Boolean {
                sent += text
                return true
            }
            override fun send(bytes: ByteString): Boolean = true
            override fun close(code: Int, reason: String?): Boolean = true
            override fun cancel() = Unit
        }
        val webSocketField = com.chat360.chatbot.network.ws.Chat360WebSocketClient::class.java.getDeclaredField("webSocket")
        webSocketField.isAccessible = true
        webSocketField.set(wsClient, fakeSocket)
        return sent
    }

    @Test
    fun `a plain text node with no buttons auto-advances to its own targetId`() {
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1")
        val sent = captureSentFrames(repo)

        val plainTextFrame = """
            {
              "user": "bot",
              "data": {
                "id": "node-1",
                "nodeType": "TEXT",
                "targetId": "next-node",
                "questionText": "Hello there"
              }
            }
        """.trimIndent()
        invokeHandleIncoming(repo, plainTextFrame)

        assertEquals(1, sent.size)
        val frame = sent.single()
        assertTrue("expected user:\"bot\", got: $frame", frame.contains("\"user\":\"bot\""))
        assertTrue("expected target_id next-node, got: $frame", frame.contains("\"target_id\":\"next-node\""))
        assertTrue("expected curr_id node-1, got: $frame", frame.contains("\"curr_id\":\"node-1\""))
        assertTrue("must not carry variable_values, got: $frame", !frame.contains("variable_values"))
    }

    @Test
    fun `a node with userInput true does not auto-advance`() {
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1")
        val sent = captureSentFrames(repo)

        val freeTextPromptFrame = """
            {
              "user": "bot",
              "data": {
                "id": "node-1",
                "nodeType": "TEXT",
                "targetId": "next-node",
                "questionText": "What's your name?",
                "userInput": true
              }
            }
        """.trimIndent()
        invokeHandleIncoming(repo, freeTextPromptFrame)

        assertTrue("expected no auto-advance frame, got: $sent", sent.isEmpty())
    }

    @Test
    fun `a MULTI_CHOICE node with buttons does not auto-advance`() {
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1")
        val sent = captureSentFrames(repo)

        val multiChoiceFrame = """
            {
              "user": "bot",
              "data": {
                "id": "node-1",
                "nodeType": "MULTI_CHOICE",
                "targetId": "next-node",
                "questionText": "Pick one",
                "buttons": [{"text": "Option A", "targetId": "a"}]
              }
            }
        """.trimIndent()
        invokeHandleIncoming(repo, multiChoiceFrame)

        assertTrue("expected no auto-advance frame for an interactive node, got: $sent", sent.isEmpty())
    }

    @Test
    fun `an END node does not auto-advance`() {
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1")
        val sent = captureSentFrames(repo)

        val endFrame = """
            {
              "user": "bot",
              "data": {
                "id": "node-1",
                "nodeType": "TEXT",
                "targetId": "END",
                "questionText": "Bye!"
              }
            }
        """.trimIndent()
        invokeHandleIncoming(repo, endFrame)

        assertTrue("expected no auto-advance frame past END, got: $sent", sent.isEmpty())
    }
}
