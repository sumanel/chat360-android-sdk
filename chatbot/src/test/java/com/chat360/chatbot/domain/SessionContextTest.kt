package com.chat360.chatbot.domain

import com.chat360.chatbot.MainDispatcherRule
import com.chat360.chatbot.model.wire.IncomingSocketEvent
import com.chat360.chatbot.network.ws.Chat360WebSocketClient
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The session timer and the host's variables (emp id, dealer id, ...) - feedback from the Hyundai integration:
 * - the timer started by itself when a new chat opened, before the user typed anything;
 * - it stuck on 00:00 after the hour, and did not come back with the next question;
 * - a room joined without session-init, or a session the backend renewed after an hour, had no emp id,
 *   so the bot answered "we could not fetch response".
 */
class SessionContextTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private var server: MockWebServer? = null

    @After
    fun tearDown() { server?.shutdown() }

    private fun repository(meta: Map<String, String>? = null, sessionStore: SessionStore? = null, baseUrl: String = "https://example.invalid") =
        ChatRepository(baseUrl = baseUrl, botId = "bot-${System.nanoTime()}", meta = meta, sessionStore = sessionStore)

    private fun field(repo: ChatRepository, name: String) = ChatRepository::class.java.getDeclaredField(name).apply { isAccessible = true }

    /** Puts [repo] in the state of a connected room, with a fake socket recording every frame it sends. */
    private fun connected(repo: ChatRepository, room: String = "room-1", sessionEverStarted: Boolean): MutableList<String> {
        field(repo, "roomId").set(repo, room)
        field(repo, "ownerId").set(repo, "owner-1")
        field(repo, "sessionEverStarted").setBoolean(repo, sessionEverStarted)
        field(repo, "isSocketOpen").setBoolean(repo, true)
        val sent = mutableListOf<String>()
        val wsClient = field(repo, "wsClient").get(repo) as Chat360WebSocketClient
        val fakeSocket = object : WebSocket {
            override fun request(): Request = throw UnsupportedOperationException()
            override fun queueSize(): Long = 0
            override fun send(text: String): Boolean { sent += text; return true }
            override fun send(bytes: ByteString): Boolean = true
            override fun close(code: Int, reason: String?): Boolean = true
            override fun cancel() = Unit
        }
        Chat360WebSocketClient::class.java.getDeclaredField("webSocket").apply { isAccessible = true }.set(wsClient, fakeSocket)
        return sent
    }

    private fun events(repo: ChatRepository): MutableList<IncomingSocketEvent> {
        val received = mutableListOf<IncomingSocketEvent>()
        field(repo, "onEvent").set(repo, { e: IncomingSocketEvent -> received += e })
        return received
    }

    private var frameCounter = 0

    /** A live bot reply from [room], delivered the way the socket would. */
    private fun botReply(repo: ChatRepository, room: String = "room-1") {
        frameCounter++
        val raw = """{"user":"bot","room_id":"$room","timestamp_int":"${1_700_000_000 + frameCounter}","data":{"nodeType":"TEXT","nodeId":"n$frameCounter","questionText":"reply $frameCounter"}}"""
        ChatRepository::class.java.getDeclaredMethod("handleIncoming", String::class.java, Function1::class.java)
            .apply { isAccessible = true }
            .invoke(repo, raw, field(repo, "onRawIncoming").get(repo))
    }

    private fun kickoff(repo: ChatRepository) {
        ChatRepository::class.java.getDeclaredMethod("performPostOpenKickoff").apply { isAccessible = true }.invoke(repo)
    }

    private fun List<String>.sessionTimeRequests() = count { it.contains("session_time") }

    // --- the timer must not start by itself ---

    @Test
    fun `the bot's opening message in a new room does not start the timer`() {
        val repo = repository()
        val sent = connected(repo, sessionEverStarted = false)
        kickoff(repo) // the socket just opened on a brand new room

        botReply(repo) // the bot's own welcome message - the user has typed nothing

        assertEquals("the countdown was started by the welcome message: $sent", 0, sent.sessionTimeRequests())
    }

    @Test
    fun `the timer starts from the bot's reply to the user's first message`() {
        val repo = repository()
        val sent = connected(repo, sessionEverStarted = false)
        kickoff(repo)
        botReply(repo) // welcome message

        repo.sendFreeText("Tell me about the Venue")
        botReply(repo)

        assertEquals(1, sent.sessionTimeRequests())
    }

    @Test
    fun `a welcome message that arrives after the socket reconnects still does not start the timer`() {
        val repo = repository()
        val sent = connected(repo, sessionEverStarted = false)
        kickoff(repo)
        kickoff(repo) // a reconnect of the same untouched room

        botReply(repo)

        assertEquals(0, sent.sessionTimeRequests())
    }

    // --- coming back after the hour ---

    @Test
    fun `sending after the hour has run out restarts the countdown from the bot's reply`() {
        val repo = repository()
        connected(repo, sessionEverStarted = true)
        val received = events(repo)
        field(repo, "lastSessionCreatedAtMs").set(repo, System.currentTimeMillis() - 3_700_000L)

        repo.sendFreeText("Another question")
        assertTrue("the timer must not restart before the bot has replied", received.none { it is IncomingSocketEvent.SessionTime })
        botReply(repo)

        val restarts = received.filterIsInstance<IncomingSocketEvent.SessionTime>()
        assertEquals(1, restarts.size)
        assertTrue("restarted at 'now', not at the old start", System.currentTimeMillis() - restarts.single().createdAtMs < 5_000)
    }

    @Test
    fun `sending within the hour leaves the running countdown alone`() {
        val repo = repository()
        connected(repo, sessionEverStarted = true)
        val received = events(repo)
        field(repo, "lastSessionCreatedAtMs").set(repo, System.currentTimeMillis() - 60_000L)

        repo.sendFreeText("Another question")
        botReply(repo)

        assertTrue(received.none { it is IncomingSocketEvent.SessionTime })
    }

    // --- the host's variables ---

    @Test
    fun `the message after an hour of silence carries the host's variables again`() {
        val repo = repository(meta = mapOf("emp_id" to "EMP1101", "dealer_id" to "W4300"))
        val sent = connected(repo, sessionEverStarted = true)
        field(repo, "lastSessionCreatedAtMs").set(repo, System.currentTimeMillis() - 3_700_000L)

        repo.sendFreeText("Another question")

        assertTrue("emp_id missing from: ${sent.first()}", sent.first().contains(""""emp_id":"EMP1101""""))
        assertTrue(sent.first().contains(""""dealer_id":"W4300""""))
    }

    @Test
    fun `the host's variables go out once, not with every message`() {
        val repo = repository(meta = mapOf("emp_id" to "EMP1101"))
        val sent = connected(repo, sessionEverStarted = true)
        field(repo, "lastSessionCreatedAtMs").set(repo, System.currentTimeMillis() - 3_700_000L)

        repo.sendFreeText("first")
        botReply(repo) // the restart is applied - the session is fresh again
        repo.sendFreeText("second")

        assertTrue(sent[0].contains("EMP1101"))
        assertFalse("sent again with the next message: ${sent[1]}", sent[1].contains("EMP1101"))
    }

    @Test
    fun `an ordinary message carries no extra variables`() {
        val repo = repository(meta = mapOf("emp_id" to "EMP1101"))
        val sent = connected(repo, sessionEverStarted = true)

        repo.sendFreeText("hello")

        assertFalse(sent.single().contains("EMP1101"))
    }

    @Test
    fun `a room joined without session-init still gets the host's variables with the first message`() {
        server = MockWebServer().apply { dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = MockResponse().setResponseCode(404)
        }; start() }
        val store = object : SessionStore {
            override fun load(botId: String) = PersistedSession("room-last", "tok", "owner-1")
            override fun loadForRoom(botId: String, roomId: String): PersistedSession? = null // never connected to this room here
            override fun save(botId: String, session: PersistedSession) = Unit
        }
        val repo = repository(meta = mapOf("emp_id" to "EMP1101"), sessionStore = store, baseUrl = server!!.url("/").toString().trimEnd('/'))

        val joined = runBlocking { repo.switchToRoom("room-elsewhere") }
        assertTrue(joined)
        val sent = connected(repo, room = "room-elsewhere", sessionEverStarted = true)
        repo.sendFreeText("hello from the old room")

        assertTrue("emp_id missing from: ${sent.first()}", sent.first().contains(""""emp_id":"EMP1101""""))
        repo.disconnect()
    }
}
