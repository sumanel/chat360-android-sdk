package com.chat360.chatbot.ui

import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.cache.FakeChatCacheDao
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
import org.junit.Assert.assertNotNull
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
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
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
    private fun makeViewModel(): ChatViewModel {
        val repository = ChatRepository(
            baseUrl = server.url("/").toString().trimEnd('/'),
            botId = botId,
            sessionStore = sessionStore,
        )
        return ChatViewModel(repository = repository, botId = botId, cache = ChatCacheRepository(dao))
    }

    @After
    fun tearDown() {
        ChatViewModel.missedReplyPollIntervalMs = 3_000L
        server.shutdown()
        Dispatchers.resetMain()
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
}
