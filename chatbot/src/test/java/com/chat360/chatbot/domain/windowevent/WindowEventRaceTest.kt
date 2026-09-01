package com.chat360.chatbot.domain.windowevent

import com.chat360.chatbot.domain.ChatRepository
import com.chat360.chatbot.network.ws.Chat360WebSocketClient
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for two bugs that combined to mean the SDK stopped receiving bot replies
 * after a WINDOW_EVENT node:
 *
 * 1. [ChatRepository.handleWindowEventNode] used to reset WindowEventBridge's global `receiving`
 *    flag to false on *any* subsequent bot frame, not just a new window-event node. Since the
 *    host's response to a window event is asynchronous (it waits on user interaction with a
 *    native dialog/WebView), an unrelated bot frame landing in between silently dropped the
 *    host's eventual sendEventToBot() call.
 *
 * 2. [ChatRepository.sendWindowEvent] sent the host's response as an ordinary end_user chat
 *    message (OutgoingMessage, with `variables`/`nodeType`/`currentId`) instead of the
 *    `user: "bot"` system-jump frame (`data.target_id` / `curr_id` / `variable_values`) the
 *    backend flow engine actually recognizes as "advance past this window-event node" - the same
 *    shape the web widget's WindowEvent component sends via jumpToEleBot/sendSocketMessage. The
 *    end_user-shaped frame just looked like an ordinary reply, so the flow engine acked it and
 *    never emitted a next message - even with bug (1) fixed.
 */
class WindowEventRaceTest {

    @After
    fun tearDown() {
        WindowEventBridge.unregisterSession()
    }

    private fun invokeHandleIncoming(repo: ChatRepository, raw: String) {
        val method = ChatRepository::class.java.getDeclaredMethod(
            "handleIncoming",
            String::class.java,
            Function1::class.java,
        )
        method.isAccessible = true
        method.invoke(repo, raw, { _: String -> })
    }

    /** Wires WindowEventBridge to the repo's own private sendWindowEvent, exactly as
     * ChatRepository's real connect() does - so a test-driven [WindowEventBridge.sendToActiveSession]
     * call exercises the real outgoing-frame construction instead of a test stub. */
    private fun registerRealSender(repo: ChatRepository) {
        val method = ChatRepository::class.java.getDeclaredMethod("sendWindowEvent", Map::class.java)
        method.isAccessible = true
        WindowEventBridge.registerSession { event -> method.invoke(repo, event) }
    }

    /** Installs a fake okhttp WebSocket on the repo's Chat360WebSocketClient so outgoing frames
     * can be captured without a real connection. */
    private fun captureSentFrames(repo: ChatRepository): MutableList<String> {
        val sent = mutableListOf<String>()
        val wsClientField = ChatRepository::class.java.getDeclaredField("wsClient")
        wsClientField.isAccessible = true
        val wsClient = wsClientField.get(repo) as Chat360WebSocketClient

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
        val webSocketField = Chat360WebSocketClient::class.java.getDeclaredField("webSocket")
        webSocketField.isAccessible = true
        webSocketField.set(wsClient, fakeSocket)
        return sent
    }

    private val windowEventFrame = """
        {
          "user": "bot",
          "data": {
            "id": "node-1",
            "nodeType": "WINDOW_EVENT",
            "targetId": "next-1",
            "should_receive_data": true,
            "should_send_data": false,
            "send_data": {}
          }
        }
    """.trimIndent()

    // userInput:true keeps this node from triggering the auto-advance system-jump on its own
    // (see BotContent.autoAdvanceTargetIdOrNull) - this fixture exists purely to simulate an
    // unrelated frame landing mid-race, not to exercise auto-advance.
    private val unrelatedBotFrame = """
        {
          "user": "bot",
          "data": {
            "id": "node-2",
            "nodeType": "TEXT",
            "targetId": "next-2",
            "questionText": "Hello there",
            "userInput": true
          }
        }
    """.trimIndent()

    @Test
    fun `host's delayed sendEventToBot still reaches the session after an unrelated bot frame lands first`() {
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1")
        val sent = captureSentFrames(repo)
        registerRealSender(repo)

        // Window-event node arrives, opening the gate (shouldReceive = true).
        invokeHandleIncoming(repo, windowEventFrame)

        // An unrelated bot frame lands before the host has had a chance to respond - this used
        // to flip WindowEventBridge's `receiving` flag back to false.
        invokeHandleIncoming(repo, unrelatedBotFrame)

        // The host's response, arriving late (e.g. after a user interacted with a native
        // dialog/WebView), must still get through.
        WindowEventBridge.sendToActiveSession(mapOf("answer" to "42"))

        assertEquals(1, sent.size)
        assertTrue(sent.single().contains("\"variable_values\":{\"answer\":\"42\"}"))
    }

    @Test
    fun `gate still closes once a new window-event node explicitly turns receiving off`() {
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1")
        var captured: Map<String, String>? = null
        WindowEventBridge.registerSession { event -> captured = event }

        invokeHandleIncoming(repo, windowEventFrame)

        val closingWindowEventFrame = """
            {
              "user": "bot",
              "data": {
                "id": "node-3",
                "nodeType": "WINDOW_EVENT",
                "targetId": "next-3",
                "should_receive_data": false,
                "should_send_data": false,
                "send_data": {}
              }
            }
        """.trimIndent()
        invokeHandleIncoming(repo, closingWindowEventFrame)

        WindowEventBridge.sendToActiveSession(mapOf("answer" to "42"))

        assertNull(captured)
    }

    @Test
    fun `the wire frame sent for a window-event response is the bot-authored system-jump shape, not an end_user chat message`() {
        val repo = ChatRepository(baseUrl = "https://staging.example", botId = "bot-1")
        val sent = captureSentFrames(repo)
        registerRealSender(repo)

        invokeHandleIncoming(repo, windowEventFrame)

        // Host answers, as chat360.sendEventToBot(...) would from the host app.
        WindowEventBridge.sendToActiveSession(mapOf("dealer_id" to "DLR001"))

        assertTrue("expected exactly one outgoing frame, got: $sent", sent.size == 1)
        val frame = sent.single()

        // Matches the web widget's jumpToEleBot/sendSocketMessage payload shape.
        assertTrue("expected user:\"bot\", got: $frame", frame.contains("\"user\":\"bot\""))
        assertTrue("expected data.target_id, got: $frame", frame.contains("\"target_id\":\"next-1\""))
        assertTrue("expected curr_id, got: $frame", frame.contains("\"curr_id\":\"node-1\""))
        assertTrue("expected variable_values, got: $frame", frame.contains("\"variable_values\":{\"dealer_id\":\"DLR001\"}"))

        // Must NOT look like the old (broken) end_user chat-message shape the flow engine
        // doesn't recognize as a window-event advance.
        assertFalse("must not be an end_user frame, got: $frame", frame.contains("\"user\":\"end_user\""))
        assertFalse("must not carry nodeType, got: $frame", frame.contains("nodeType"))
        assertFalse("must not carry a top-level variables key, got: $frame", frame.contains("\"variables\""))
    }
}
