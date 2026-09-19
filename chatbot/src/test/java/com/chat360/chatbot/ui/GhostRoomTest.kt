package com.chat360.chatbot.ui

import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.cache.FakeChatCacheDao
import com.chat360.chatbot.domain.thirdparty.SalesExecutiveGate
import com.chat360.chatbot.domain.thirdparty.WelcomeText
import com.chat360.chatbot.domain.thirdparty.WelcomeTextRepository
import com.chat360.chatbot.domain.thirdparty.WelcomeTextStore
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import com.chat360.chatbot.domain.ChatRepository
import com.chat360.chatbot.domain.PersistedSession
import com.chat360.chatbot.domain.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression test for ghost rooms: every session-init the app makes allocates a real room on the
 * server, and "New chat" used to make one per tap even when the previous new chat was still
 * empty. Scenario from the bug report: open the SDK (room 1, no message sent), open another
 * room, then tap "New chat" twice - that must land back on room 1 instead of creating rooms 2
 * and 3. Runs the real [ChatViewModel] + [ChatRepository] against a [MockWebServer] that counts
 * session-init calls.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GhostRoomTest {

    private class InMemorySessionStore : SessionStore {
        private val rooms = mutableMapOf<String, PersistedSession>()
        private var last: PersistedSession? = null
        override fun load(botId: String) = last
        override fun loadForRoom(botId: String, roomId: String) = rooms[roomId]
        override fun save(botId: String, session: PersistedSession) {
            rooms[session.roomId] = session
            last = session
        }
    }

    /** Drives virtual time for the view model's `delay`s (the missed-reply polling); advanced while a test waits. */
    private val scheduler = TestCoroutineScheduler()
    private val historyRequestsByRoom = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile private var historyWithoutReply = 0
    @Volatile private var userMessageAgeSeconds = 0L
    private val roomUnderTest = "room-a"
    private fun historyFetches() = historyRequestsByRoom[roomUnderTest] ?: 0

    /** History as the server returns it: only the room under test has any (the user's message, then the reply). */
    private fun historyJson(room: String, includeReply: Boolean): String {
        if (room != roomUnderTest) return """{"history":[],"previous_cursor":null}"""
        val json = Json { explicitNulls = false }
        val now = System.currentTimeMillis() / 1000
        val user = RawSocketEnvelope(user = "end_user", message = JsonPrimitive("Tell me about Hyundai Venue features"), chat_msg_id = "user-1", timestamp_int = (now - userMessageAgeSeconds).toString())
        val bot = RawSocketEnvelope(
            user = "bot",
            data = buildJsonObject { put("nodeType", "TEXT"); put("id", "reply-1"); put("questionText", "Venue features reply") },
            timestamp_int = now.toString(),
        )
        val rows = (listOf(user) + if (includeReply) listOf(bot) else emptyList()).joinToString(",") { json.encodeToString(RawSocketEnvelope.serializer(), it) }
        return """{"history":[$rows],"previous_cursor":null}"""
    }

    // Server-configured welcome copy: what the stub returns, and the client_id header of every request.
    @Volatile private var welcomeStatus = 200
    @Volatile private var welcomeBody = """{"heading":"Server heading","text":"Server subtitle","client_id":"client-1"}"""
    @Volatile private var welcomeDelayMs = 0L
    private val welcomeClientIds = CopyOnWriteArrayList<String?>()

    private class MemoryWelcomeStore(var saved: WelcomeText? = null) : WelcomeTextStore {
        override fun load(clientId: String) = saved
        override fun save(clientId: String, welcomeText: WelcomeText?) { saved = welcomeText }
    }

    // The sales-executive check and the maintenance flag: what each stub returns, and what the app sent.
    @Volatile private var salesStatus = 200
    @Volatile private var salesBody = ACTIVE_EXECUTIVE
    @Volatile private var salesDelayMs = 0L
    private val salesRequests = CopyOnWriteArrayList<Pair<String?, String>>() // Client-Id header to JSON body
    @Volatile private var maintenanceBody = """{"is_active":false}"""

    private lateinit var server: MockWebServer
    private lateinit var dao: FakeChatCacheDao
    private lateinit var sessionStore: InMemorySessionStore
    private lateinit var viewModel: ChatViewModel

    /** room_id query param of every session-init request, null when the app asked for a new room. */
    private val sessionRequests = CopyOnWriteArrayList<String?>()
    private val roomCounter = AtomicInteger(0)
    private val botId = "ghost-room-bot-${System.nanoTime()}"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
        // awaitUntil runs virtual time ~10x faster than real time, so the real-world 20s wait would expire in ~2s here.
        ChatViewModel.newSessionSendTimeoutMs = 10 * 60_000L
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                if (path.endsWith("/api/third-party-tasks/sales-exectives")) {
                    salesRequests += request.getHeader("Client-Id") to request.body.readUtf8()
                    return MockResponse().setResponseCode(salesStatus).setBody(salesBody)
                        .setBodyDelay(salesDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
                if (path.endsWith("/api/third-party-tasks/maintainance")) return MockResponse().setBody(maintenanceBody)
                if (path.endsWith("/api/third-party-tasks/welcome-text")) {
                    welcomeClientIds += request.getHeader("Client-Id")
                    return MockResponse().setResponseCode(welcomeStatus).setBody(welcomeBody)
                        .setBodyDelay(welcomeDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
                if (path.contains("/chatbox/messages/")) {
                    val room = path.substringAfterLast('/')
                    val count = historyRequestsByRoom.merge(room, 1, Int::plus)!!
                    return MockResponse().setBody(historyJson(room, includeReply = room == roomUnderTest && count > historyWithoutReply))
                }
                if (!path.contains("/session/")) return MockResponse().setResponseCode(404)
                val requested = request.requestUrl?.queryParameter("room_id")
                sessionRequests += requested
                // Like the real backend: a known room_id resumes, anything else allocates a new one.
                val room = requested ?: "room-${roomCounter.incrementAndGet()}"
                return MockResponse().setBody(
                    """{"room_id":"$room","owner_id":"owner-1","session_token":"tok-$room","nodeType":"INIT","targetId":"t1"}""",
                )
            }
        }
        server.start()
        dao = FakeChatCacheDao()
        sessionStore = InMemorySessionStore()
        viewModel = makeViewModel()
    }

    /** A fresh view model over the same cache/session store - what reopening the chat screen builds. */
    private fun makeViewModel(welcome: WelcomeTextRepository? = null, gate: SalesExecutiveGate? = null): ChatViewModel {
        val repository = ChatRepository(
            baseUrl = server.url("/").toString().trimEnd('/'),
            botId = botId,
            sessionStore = sessionStore,
        )
        return ChatViewModel(repository = repository, botId = botId, cache = ChatCacheRepository(dao), welcomeTextRepository = welcome, salesExecutiveGate = gate)
    }

    @After
    fun tearDown() {
        ChatViewModel.missedReplyPollIntervalMs = 3_000L
        server.shutdown()
        Dispatchers.resetMain()
        ChatViewModel.newSessionSendTimeoutMs = 20_000L
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: $what (session requests=$sessionRequests)")
            scheduler.advanceTimeBy(200)
            Thread.sleep(20)
        }
    }

    private fun settle() {
        repeat(20) { scheduler.advanceTimeBy(200); Thread.sleep(20) }
    }

    @Test
    fun `tapping New chat repeatedly after visiting another room reuses the blank room instead of creating ghosts`() {
        // 1. Opening the SDK creates room 1 (the "first screen"); the user sends nothing.
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        val firstScreenId = viewModel.uiState.value.activeConversationId
        assertNotNull(firstScreenId)
        assertEquals(listOf<String?>(null), sessionRequests.toList())

        // 2. The user opens a different, older room this device has connected to before.
        sessionStore.save(botId, PersistedSession("room-old", "tok-room-old", "owner-1"))
        kotlinx.coroutines.runBlocking {
            dao.upsertConversation(
                CachedConversationEntity(id = "conv-old", botId = botId, roomId = "room-old", title = "Old chat", createdAt = 1, updatedAt = 1),
            )
        }
        awaitUntil("old conversation listed") { viewModel.conversations.value.any { it.id == "conv-old" } }
        viewModel.openConversation("conv-old")
        awaitUntil("old room resumed") { "room-old" in sessionRequests }
        settle()

        // 3. New chat, twice. Neither may ask the server for a new room.
        viewModel.startNewChat()
        awaitUntil("blank room resumed") { "room-1" in sessionRequests }
        settle()
        viewModel.startNewChat()
        settle()

        val freshRoomsRequested = sessionRequests.count { it == null }
        assertEquals("ghost rooms were created: $sessionRequests", 1, freshRoomsRequested)
        assertEquals("New chat must show the original first screen again", firstScreenId, viewModel.uiState.value.activeConversationId)
    }

    @Test
    fun `New chat creates a real new room once the current one has a user message`() {
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }

        // The test socket never opens (no Looper in unit tests), so a real send can't complete;
        // flip the same flag ensureConversationPersisted() sets on the first user message.
        ChatViewModel::class.java.getDeclaredField("conversationPersisted").apply {
            isAccessible = true
            setBoolean(viewModel, true)
        }
        viewModel.startNewChat()
        awaitUntil("second room requested") { sessionRequests.size == 2 }

        // A used chat is not blank, so the second request is a genuinely new room.
        assertEquals(listOf<String?>(null, null), sessionRequests.toList())
    }

    @Test
    fun `reopening the chat screen in the same process resumes the blank room instead of the last connected one`() {
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        val firstScreenId = viewModel.uiState.value.activeConversationId

        // The user browses to another room (making it the last connected), leaving room 1 empty.
        sessionStore.save(botId, PersistedSession("room-old", "tok-room-old", "owner-1"))
        kotlinx.coroutines.runBlocking {
            dao.upsertConversation(
                CachedConversationEntity(id = "conv-old", botId = botId, roomId = "room-old", title = "Old chat", createdAt = 1, updatedAt = 1),
            )
        }
        awaitUntil("old conversation listed") { viewModel.conversations.value.any { it.id == "conv-old" } }
        viewModel.openConversation("conv-old")
        awaitUntil("old room resumed") { "room-old" in sessionRequests }
        settle()

        // The host closes and reopens the chat screen while the app keeps running.
        viewModel = makeViewModel()
        awaitUntil("reopened") { sessionRequests.size >= 3 }
        settle()

        assertEquals("reopening created a ghost room: $sessionRequests", 1, sessionRequests.count { it == null })
        assertEquals("reopening should resume the empty room", "room-1", sessionRequests.last())
        assertNotNull(firstScreenId)
    }

    @Test
    fun `unsent text stays with the room it was typed in when switching rooms`() {
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        viewModel.onInputChange("half typed in the first room")

        sessionStore.save(botId, PersistedSession("room-old", "tok-room-old", "owner-1"))
        kotlinx.coroutines.runBlocking {
            dao.upsertConversation(
                CachedConversationEntity(id = "conv-old", botId = botId, roomId = "room-old", title = "Old chat", createdAt = 1, updatedAt = 1),
            )
        }
        awaitUntil("old conversation listed") { viewModel.conversations.value.any { it.id == "conv-old" } }
        viewModel.openConversation("conv-old")
        awaitUntil("old room resumed") { "room-old" in sessionRequests }
        settle()
        assertEquals("the other room must not inherit the text", "", viewModel.uiState.value.inputText)

        viewModel.onInputChange("typed in the old room")
        viewModel.startNewChat()
        settle()
        assertEquals("returning restores that room's own text", "half typed in the first room", viewModel.uiState.value.inputText)

        viewModel.openConversation("conv-old")
        settle()
        assertEquals("typed in the old room", viewModel.uiState.value.inputText)
    }

    // --- an older room this device can't reconnect to ---
    // Sending from one used to be routed into whichever room was connected, so chats done in two different
    // older rooms ended up together in a single room. It now starts a fresh session instead.

    private fun seedOtherDeviceRoom() {
        kotlinx.coroutines.runBlocking {
            dao.upsertConversation(CachedConversationEntity(id = "conv-other", botId = botId, roomId = "room-other", title = "From elsewhere", createdAt = 1, updatedAt = 1))
        }
        awaitUntil("conversation listed") { viewModel.conversations.value.any { it.id == "conv-other" } }
    }

    // The mock server has no socket, so whether the fresh session reports connected varies; both outcomes are
    // accepted: the text is sent there, or handed back to the input for a retry. It must never be filed under the
    // room that was connected, and the old room can't be joined by id.
    @Test
    fun `sending from a room with no saved session starts a fresh session and never files the text under the connected room`() {
        ChatViewModel.newSessionSendTimeoutMs = 3_000L // virtual time here runs ~10x real time
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        val connectedConversationId = viewModel.uiState.value.activeConversationId!!
        // Make the connected room a real chat, so a new chat can't just reuse it as a blank room.
        viewModel.onInputChange("first chat message")
        viewModel.sendMessage()
        settle()
        val before = sessionRequests.size
        seedOtherDeviceRoom()

        viewModel.openConversation("conv-other")
        awaitUntil("marked as needing a new session") { viewModel.uiState.value.needsNewSession }

        val text = "hello from the old room"
        viewModel.onInputChange(text)
        viewModel.sendMessage()

        awaitUntil("a fresh session to be created") { sessionRequests.size == before + 1 }
        assertNotEquals("the old room can't be rejoined by id", "room-other", sessionRequests.last())
        awaitUntil("the text to be sent in the fresh session or handed back") {
            viewModel.uiState.value.inputText == text || transcript().contains(text)
        }
        val cachedInConnectedRoom = kotlinx.coroutines.runBlocking { dao.messages(connectedConversationId) }.map { it.payload }
        assertEquals("not filed under the room that was connected", false, cachedInConnectedRoom.contains(text))
        assertEquals("the flag clears once on a real session", false, viewModel.uiState.value.needsNewSession)
    }

    @Test
    fun `needing a new session ends when moving to a room that can be resumed, or to a new chat`() {
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        seedOtherDeviceRoom()
        viewModel.openConversation("conv-other")
        awaitUntil("marked as needing a new session") { viewModel.uiState.value.needsNewSession }

        sessionStore.save(botId, PersistedSession("room-old", "tok-room-old", "owner-1"))
        kotlinx.coroutines.runBlocking {
            dao.upsertConversation(CachedConversationEntity(id = "conv-old", botId = botId, roomId = "room-old", title = "Old chat", createdAt = 1, updatedAt = 1))
        }
        awaitUntil("old conversation listed") { viewModel.conversations.value.any { it.id == "conv-old" } }
        viewModel.openConversation("conv-old")
        awaitUntil("old room resumed") { "room-old" in sessionRequests }
        settle()
        assertEquals(false, viewModel.uiState.value.needsNewSession)

        viewModel.openConversation("conv-other")
        awaitUntil("marked again") { viewModel.uiState.value.needsNewSession }
        viewModel.startNewChat()
        settle()
        assertEquals(false, viewModel.uiState.value.needsNewSession)
    }

    // --- a reply generated while away ---
    // The server stores a reply ~14s after the send whether or not a socket is connected, but only pushes it
    // to a socket connected to that room at that moment. Returning sooner found nothing on the one history
    // fetch and nothing ever looked again.

    private fun seedRoomWithPendingReply() {
        kotlinx.coroutines.runBlocking {
            dao.upsertConversation(CachedConversationEntity(id = "conv-a", botId = botId, roomId = roomUnderTest, title = "Venue", createdAt = 1, updatedAt = 1))
            dao.upsertConversation(CachedConversationEntity(id = "conv-b", botId = botId, roomId = "room-b", title = "Other", createdAt = 1, updatedAt = 1))
        }
        awaitUntil("conversations listed") { viewModel.conversations.value.any { it.id == "conv-a" } && viewModel.conversations.value.any { it.id == "conv-b" } }
    }

    private fun transcript() = viewModel.uiState.value.messages.map { it.text }

    @Test
    fun `a reply stored after the first check on return still appears`() {
        ChatViewModel.missedReplyPollIntervalMs = 30
        historyWithoutReply = 2 // the first two checks find only the user's message
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        seedRoomWithPendingReply()

        viewModel.openConversation("conv-a")

        awaitUntil("the reply to appear") { transcript().contains("Venue features reply") }
        assertTrue("it never looked again after the first check: ${historyFetches()} fetches", historyFetches() >= 3)
        assertEquals("the typing indicator was left on after the reply arrived", false, viewModel.uiState.value.isAgentTyping)
    }

    @Test
    fun `polling stops once the reply has been found`() {
        ChatViewModel.missedReplyPollIntervalMs = 30
        historyWithoutReply = 1
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        seedRoomWithPendingReply()

        viewModel.openConversation("conv-a")
        awaitUntil("the reply to appear") { transcript().contains("Venue features reply") }
        settle()
        val once = historyFetches()
        settle()

        assertEquals("kept polling after the reply was found", once, historyFetches())
    }

    @Test
    fun `it gives up after the ceiling instead of polling forever, and clears the typing indicator`() {
        ChatViewModel.missedReplyPollIntervalMs = 5
        historyWithoutReply = Int.MAX_VALUE // the reply never shows up
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        seedRoomWithPendingReply()

        viewModel.openConversation("conv-a")
        awaitUntil("polling to run its course") { historyFetches() >= 31 }
        settle()
        val fetches = historyFetches()
        settle()

        assertEquals("polled past the ceiling", fetches, historyFetches())
        assertTrue("unbounded polling: $fetches fetches", fetches <= 32)
        assertEquals(false, viewModel.uiState.value.isAgentTyping)
    }

    @Test
    fun `polling stops when the user moves to another room`() {
        ChatViewModel.missedReplyPollIntervalMs = 30
        historyWithoutReply = Int.MAX_VALUE
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        seedRoomWithPendingReply()

        viewModel.openConversation("conv-a")
        awaitUntil("polling under way") { historyFetches() >= 3 }
        viewModel.openConversation("conv-b")
        settle()
        val fetches = historyFetches()
        settle()

        assertEquals("kept polling room A after the user left it", fetches, historyFetches())
        assertEquals("room A's typing indicator leaked into room B", false, viewModel.uiState.value.isAgentTyping)
    }

    @Test
    fun `an old unanswered message is not polled for`() {
        ChatViewModel.missedReplyPollIntervalMs = 30
        historyWithoutReply = Int.MAX_VALUE
        userMessageAgeSeconds = 600 // sent ten minutes ago - the flow simply ended
        awaitUntil("initial room") { sessionRequests.size == 1 && viewModel.uiState.value.activeConversationId != null }
        seedRoomWithPendingReply()

        viewModel.openConversation("conv-a")
        awaitUntil("room A to load") { transcript().contains("Tell me about Hyundai Venue features") }
        settle()
        settle()

        assertEquals("polled for a message that was never going to be answered", 1, historyFetches())
    }

    // --- server-configured welcome text ---

    private fun welcomeRepository(store: WelcomeTextStore = MemoryWelcomeStore()) =
        WelcomeTextRepository(ThirdPartyTasksApiService(server.url("/").toString().trimEnd('/')), "client-1", store)

    @Test
    fun `the server's welcome text reaches the screen state, asked for with the client id`() {
        viewModel = makeViewModel(welcomeRepository())

        awaitUntil("the welcome text to load") { viewModel.uiState.value.welcomeOverride != null }

        assertEquals(WelcomeText("Server heading", "Server subtitle"), viewModel.uiState.value.welcomeOverride)
        assertEquals(listOf<String?>("client-1"), welcomeClientIds.toList())
    }

    @Test
    fun `the cached welcome text is there immediately, before the server has answered`() {
        welcomeDelayMs = 1_500
        viewModel = makeViewModel(welcomeRepository(MemoryWelcomeStore(WelcomeText("Cached heading", "Cached subtitle"))))

        // No waiting: this is the very first paint.
        assertEquals(WelcomeText("Cached heading", "Cached subtitle"), viewModel.uiState.value.welcomeOverride)

        awaitUntil("the fresh text to replace it") { viewModel.uiState.value.welcomeOverride?.heading == "Server heading" }
    }

    @Test
    fun `an endpoint that is not deployed leaves the defaults and does not get in the chat's way`() {
        welcomeStatus = 404
        welcomeBody = "<!DOCTYPE html><html>Page not found</html>"
        viewModel = makeViewModel(welcomeRepository())

        awaitUntil("the welcome request") { welcomeClientIds.isNotEmpty() }
        settle()

        assertNull(viewModel.uiState.value.welcomeOverride)
        awaitUntil("the chat to connect regardless") { sessionRequests.size >= 2 && viewModel.uiState.value.activeConversationId != null }
    }

    @Test
    fun `a failure keeps a previously cached welcome text on screen`() {
        welcomeStatus = 500
        viewModel = makeViewModel(welcomeRepository(MemoryWelcomeStore(WelcomeText("Cached heading", "Cached subtitle"))))

        awaitUntil("the welcome request") { welcomeClientIds.isNotEmpty() }
        settle()

        assertEquals(WelcomeText("Cached heading", "Cached subtitle"), viewModel.uiState.value.welcomeOverride)
    }

    @Test
    fun `the server clearing its welcome text removes the override`() {
        welcomeBody = """{"heading":"","text":""}"""
        viewModel = makeViewModel(welcomeRepository(MemoryWelcomeStore(WelcomeText("Old heading", "Old subtitle"))))

        awaitUntil("the empty reply to clear it") { viewModel.uiState.value.welcomeOverride == null }
    }

    @Test
    fun `a host without a client id never asks the server`() {
        viewModel = makeViewModel(null)
        awaitUntil("the chat to connect") { viewModel.uiState.value.activeConversationId != null }
        settle()

        assertTrue("asked anyway: $welcomeClientIds", welcomeClientIds.isEmpty())
        assertNull(viewModel.uiState.value.welcomeOverride)
    }

    // --- sales-executive gate ---
    // Closed like maintenance: no socket, the server's message in place of the input bar. Anything other than a
    // clear INACTIVE lets the chat run untouched.

    /** The gate under test. The timeout is long because the harness's virtual clock runs faster than real time. */
    private fun gate(timeoutMs: Long = 60_000) =
        SalesExecutiveGate(ThirdPartyTasksApiService(server.url("/").toString().trimEnd('/')), "client-1", mapOf("dealer_code" to "W4300", "emp_code" to "EMP1101"), timeoutMs)

    /** A view model started fresh with the gate, plus how many rooms had already been created before it. */
    private fun startGated(gate: SalesExecutiveGate? = gate()): Int {
        awaitUntil("the harness's own room") { sessionRequests.size == 1 }
        val before = sessionRequests.size
        viewModel = makeViewModel(gate = gate)
        return before
    }

    @Test
    fun `an INACTIVE executive gets the server's message and no socket is ever opened`() {
        salesBody = INACTIVE_EXECUTIVE
        val before = startGated()

        awaitUntil("the block to show") { viewModel.uiState.value.maintenanceMessage != null }
        settle()

        assertEquals("Sales Executive onboarded as INACTIVE.", viewModel.uiState.value.maintenanceMessage)
        assertEquals("a room was created for a blocked executive: $sessionRequests", before, sessionRequests.size)
        assertEquals("client-1", salesRequests.single().first)
        assertTrue(salesRequests.single().second.contains("\"emp_code\":\"EMP1101\""))
    }

    @Test
    fun `an ACTIVE executive connects normally with no block`() {
        val before = startGated()

        awaitUntil("the chat to connect") { sessionRequests.size == before + 1 }

        assertNull(viewModel.uiState.value.maintenanceMessage)
    }

    @Test
    fun `a failing check never blocks - errors, an undeployed endpoint and a slow server all let the chat connect`() {
        for ((status, body) in listOf(500 to "", 404 to "<html>Page not found</html>", 400 to """{"success":false,"errors":{"emp_code":["This field is required."]}}""", 200 to "<html>proxy</html>")) {
            salesStatus = status
            salesBody = body
            val before = sessionRequests.size.coerceAtLeast(1)
            viewModel = makeViewModel(gate = gate())

            awaitUntil("the chat to connect despite HTTP $status") { sessionRequests.size > before }
            assertNull("blocked on HTTP $status", viewModel.uiState.value.maintenanceMessage)
        }
    }

    @Test
    fun `a slow server does not hold the chat back`() {
        salesBody = INACTIVE_EXECUTIVE
        salesDelayMs = 2_000 // answers far too late (kept under MockWebServer's 5s shutdown wait)
        val before = startGated(gate(timeoutMs = 400))

        awaitUntil("the chat to connect without waiting for the check") { sessionRequests.size == before + 1 }

        assertNull(viewModel.uiState.value.maintenanceMessage)
    }

    @Test
    fun `once the executive is activated, returning to the app starts the chat that was never opened`() {
        salesBody = INACTIVE_EXECUTIVE
        val before = startGated()
        awaitUntil("the block to show") { viewModel.uiState.value.maintenanceMessage != null }
        assertEquals(before, sessionRequests.size)

        salesBody = ACTIVE_EXECUTIVE // an admin activates them
        viewModel.onAppForegrounded()

        awaitUntil("the chat to start") { sessionRequests.size == before + 1 }
        assertNull("the block should have cleared", viewModel.uiState.value.maintenanceMessage)
    }

    @Test
    fun `starting a new chat after being blocked at startup does the first connect properly`() {
        salesBody = INACTIVE_EXECUTIVE
        val before = startGated()
        awaitUntil("the block to show") { viewModel.uiState.value.maintenanceMessage != null }

        salesBody = ACTIVE_EXECUTIVE
        viewModel.startNewChat()

        awaitUntil("the chat to start") { sessionRequests.size == before + 1 }
        awaitUntil("the room to be shown") { viewModel.uiState.value.activeConversationId != null }
        assertNull(viewModel.uiState.value.maintenanceMessage)
    }

    @Test
    fun `an executive who is still INACTIVE stays blocked when returning to the app`() {
        salesBody = INACTIVE_EXECUTIVE
        val before = startGated()
        awaitUntil("the block to show") { viewModel.uiState.value.maintenanceMessage != null }

        viewModel.onAppForegrounded()
        settle()

        assertEquals("Sales Executive onboarded as INACTIVE.", viewModel.uiState.value.maintenanceMessage)
        assertEquals(before, sessionRequests.size)
    }

    @Test
    fun `once active the check is not repeated when returning to the app`() {
        val before = startGated()
        awaitUntil("the chat to connect") { sessionRequests.size == before + 1 }

        viewModel.onAppForegrounded()
        viewModel.onAppForegrounded()
        settle()

        assertEquals("re-checked on every foreground: ${salesRequests.size} requests", 1, salesRequests.size)
    }

    @Test
    fun `maintenance mode takes priority over an inactive executive`() {
        // Only after the harness's own view model has started - it would see maintenance too.
        awaitUntil("the harness's own room") { sessionRequests.size == 1 }
        maintenanceBody = """{"is_active":true,"message":"Down for maintenance"}"""
        salesBody = INACTIVE_EXECUTIVE
        val before = startGated()

        awaitUntil("the block to show") { viewModel.uiState.value.maintenanceMessage != null }
        settle()

        assertEquals("Down for maintenance", viewModel.uiState.value.maintenanceMessage)
        assertEquals(before, sessionRequests.size)
    }

    @Test
    fun `a host that configures no sales executive never calls the endpoint`() {
        val before = startGated(gate = null)

        awaitUntil("the chat to connect") { sessionRequests.size == before + 1 }
        settle()

        assertTrue("asked anyway: $salesRequests", salesRequests.isEmpty())
    }

    private companion object {
        const val INACTIVE_EXECUTIVE = """{"success":true,"message":"Sales Executive onboarded as INACTIVE.","is_new":true,"status_downgraded_to_inactive":false,"sales_executive":{"id":12,"emp_code":"EMP1101","name":"","role":null,"dealer_code":"W4300","dealer_name":"Hindustan Hyundai","status":"INACTIVE"}}"""
        const val ACTIVE_EXECUTIVE = """{"success":true,"message":"Sales Executive validated successfully.","is_new":false,"status_downgraded_to_inactive":false,"sales_executive":{"id":12,"emp_code":"EMP1101","name":"","role":"Trainer","dealer_code":"W4300","dealer_name":"Hindustan Hyundai","status":"ACTIVE"}}"""
    }
}
