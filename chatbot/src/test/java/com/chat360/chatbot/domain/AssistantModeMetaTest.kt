package com.chat360.chatbot.domain

import com.chat360.chatbot.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** The selected Assistant Mode option's variables (e.g. `agent_role`) ride along in session-init's `meta`. */
class AssistantModeMetaTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** The decoded `meta` query param of each session-init request, in order (null = param absent). */
    private class Sessions : Dispatcher() {
        val metas = CopyOnWriteArrayList<Map<String, String>?>()
        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(404)
            if (!url.encodedPath.contains("/session/")) return MockResponse().setResponseCode(404)
            metas += url.queryParameter("meta")?.let {
                Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it)
            }
            return MockResponse().setBody("""{"room_id":"room-${metas.size}","owner_id":"owner-1","session_token":"tok","nodeType":"INIT","targetId":"t1"}""")
        }
    }

    private fun withServer(block: (MockWebServer, Sessions) -> Unit) {
        val sessions = Sessions()
        val server = MockWebServer().apply { dispatcher = sessions; start() }
        try { block(server, sessions) } finally { server.shutdown() }
    }

    private class MemorySessionStore : SessionStore {
        private val rooms = mutableMapOf<String, PersistedSession>()
        private var last: PersistedSession? = null
        override fun load(botId: String) = last
        override fun loadForRoom(botId: String, roomId: String) = rooms[roomId]
        override fun save(botId: String, session: PersistedSession) { rooms[session.roomId] = session; last = session }
    }

    private fun repo(server: MockWebServer, meta: Map<String, String>?, vars: Map<String, String>, store: SessionStore? = null) =
        ChatRepository(
            baseUrl = server.url("/").toString().trimEnd('/'),
            botId = "assistant-${System.nanoTime()}",
            sessionStore = store,
            meta = meta,
            assistantVariables = vars,
        )

    private fun connect(repo: ChatRepository) =
        thread { runBlocking { repo.connect(onEvent = {}, onConnected = {}, onError = {}) } }.join(10_000)

    @Test
    fun `initial variables are merged into the host meta at session-init`() = withServer { server, sessions ->
        val repo = repo(server, mapOf("dealer_id" to "W4300"), mapOf("agent_role" to "customer"))
        connect(repo)
        assertEquals(mapOf("dealer_id" to "W4300", "agent_role" to "customer"), sessions.metas.first())
        repo.disconnect()
    }

    @Test
    fun `variables alone are sent when the host set no meta`() = withServer { server, sessions ->
        val repo = repo(server, null, mapOf("agent_role" to "training"))
        connect(repo)
        assertEquals(mapOf("agent_role" to "training"), sessions.metas.first())
        repo.disconnect()
    }

    @Test
    fun `no meta param at all when there is neither`() = withServer { server, sessions ->
        val repo = repo(server, null, emptyMap())
        connect(repo)
        assertNull(sessions.metas.first())
        repo.disconnect()
    }

    @Test
    fun `an assistant variable wins over a host meta key of the same name`() = withServer { server, sessions ->
        val repo = repo(server, mapOf("agent_role" to "host"), mapOf("agent_role" to "customer"))
        connect(repo)
        assertEquals(mapOf("agent_role" to "customer"), sessions.metas.first())
        repo.disconnect()
    }

    @Test
    fun `switching the option starts a new session carrying the new variables`() = withServer { server, sessions ->
        val repo = repo(server, mapOf("dealer_id" to "W4300"), mapOf("agent_role" to "customer"))
        connect(repo)

        repo.setAssistantVariables(mapOf("agent_role" to "training"))
        runBlocking { repo.startNewSession() }

        assertEquals(2, sessions.metas.size)
        assertEquals(mapOf("dealer_id" to "W4300", "agent_role" to "customer"), sessions.metas[0])
        assertEquals(mapOf("dealer_id" to "W4300", "agent_role" to "training"), sessions.metas[1])
        repo.disconnect()
    }

    @Test
    fun `resuming a session sends only the host meta - never the variables`() {
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1", meta = mapOf("dealer_id" to "W4300"), assistantVariables = mapOf("agent_role" to "customer"))
        assertEquals(mapOf("dealer_id" to "W4300"), repo.sessionMeta(resuming = true))
        assertEquals(mapOf("dealer_id" to "W4300", "agent_role" to "customer"), repo.sessionMeta(resuming = false))
        assertNull(ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1", assistantVariables = mapOf("agent_role" to "customer")).sessionMeta(resuming = true))
    }

    @Test
    fun `switching back to an older room resumes it without re-sending the role`() = withServer { server, sessions ->
        val repo = repo(server, mapOf("dealer_id" to "W4300"), mapOf("agent_role" to "customer"), MemorySessionStore())
        connect(repo) // room-1, created as customer

        repo.setAssistantVariables(mapOf("agent_role" to "training"))
        runBlocking { repo.startNewSession() } // room-2, created as training
        runBlocking { repo.switchToRoom("room-1") } // resumes room-1: must not flip it to training

        assertEquals(3, sessions.metas.size)
        assertEquals(mapOf("dealer_id" to "W4300", "agent_role" to "training"), sessions.metas[1])
        assertEquals(mapOf("dealer_id" to "W4300"), sessions.metas[2])
        repo.disconnect()
    }

    // --- onFreshSession: the role a newly created room was made with, before the server lists it ---

    @Test
    fun `a brand-new session reports the role it was created with`() = withServer { server, _ ->
        val created = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        val repo = repo(server, mapOf("dealer_id" to "W4300"), mapOf("agent_role" to "customer"))
        repo.onFreshSession = { roomId, variables -> created += roomId to variables }
        connect(repo)
        assertEquals(listOf("room-1" to mapOf("agent_role" to "customer")), created.toList())
        repo.disconnect()
    }

    @Test
    fun `switching the button reports the new role for the new room`() = withServer { server, _ ->
        val created = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        val repo = repo(server, null, mapOf("agent_role" to "customer"))
        repo.onFreshSession = { roomId, variables -> created += roomId to variables }
        connect(repo)
        repo.setAssistantVariables(mapOf("agent_role" to "training"))
        runBlocking { repo.startNewSession() }
        assertEquals(
            listOf("room-1" to mapOf("agent_role" to "customer"), "room-2" to mapOf("agent_role" to "training")),
            created.toList(),
        )
        repo.disconnect()
    }

    @Test
    fun `resuming a room does not report a role for it`() = withServer { server, _ ->
        val created = CopyOnWriteArrayList<String>()
        val repo = repo(server, null, mapOf("agent_role" to "customer"), MemorySessionStore())
        repo.onFreshSession = { roomId, _ -> created += roomId }
        connect(repo) // room-1, fresh
        runBlocking { repo.startNewSession() } // room-2, fresh
        runBlocking { repo.switchToRoom("room-1") } // resumed - the role it was made with must not be overwritten
        assertEquals(listOf("room-1", "room-2"), created.toList())
        repo.disconnect()
    }
}
