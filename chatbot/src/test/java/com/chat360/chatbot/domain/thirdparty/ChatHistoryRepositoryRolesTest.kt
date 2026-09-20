package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.cache.FakeChatCacheDao
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** `rooms/list` returns each room's `agent_role`; the history list badges rooms from it. */
class ChatHistoryRepositoryRolesTest {

    private class MemoryRoleStore(var saved: Map<String, String> = emptyMap(), var savedLocal: Map<String, String> = emptyMap()) : RoomRoleStore {
        var saves = 0
        override fun load(botId: String) = saved
        override fun save(botId: String, roles: Map<String, String>) { saved = roles; saves++ }
        override fun loadLocal(botId: String) = savedLocal
        override fun saveLocal(botId: String, roles: Map<String, String>) { savedLocal = roles }
    }

    private lateinit var http: MockWebServer
    /** roomId -> role json fragment ("" = the field is absent) */
    private var rooms: List<Pair<String, String?>> = emptyList()

    @Before
    fun setUp() {
        http = MockWebServer()
        http.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath.orEmpty().endsWith("/auth/token")) {
                    return MockResponse().setBody("""{"success":true,"data":{"bearer_token":"token-1","token_type":"Bearer","expires_in":3600}}""")
                }
                val json = rooms.joinToString(",") { (id, role) ->
                    val roleField = role?.let { ""","agent_role":"$it"""" } ?: ""
                    """{"room_id":"$id","room_name":"Chat $id","status":"active","updated_at":"2026-09-18T10:00:00Z","session_count":1$roleField}"""
                }
                return MockResponse().setBody("""{"success":true,"data":{"rooms":[$json],"total_count":${rooms.size},"has_more":false}}""")
            }
        }
        http.start()
    }

    @After
    fun tearDown() = http.shutdown()

    private fun repository(store: RoomRoleStore? = null): ChatHistoryRepository {
        val api = ThirdPartyTasksApiService(http.url("/").toString())
        return ChatHistoryRepository(api, ThirdPartyTokenManager(api, "client-1", "api-key-1"), ChatCacheRepository(FakeChatCacheDao()), "client-1", "bot-1", "agent-1", store)
    }

    @Test
    fun `the roles the server returns are kept per room`() = runTest {
        rooms = listOf("room-a" to "training", "room-b" to "customer")
        val repo = repository()
        repo.refreshRooms()
        assertEquals(mapOf("room-a" to "training", "room-b" to "customer"), repo.roomRoles.value)
    }

    @Test
    fun `a room the server gives no role for has none`() = runTest {
        rooms = listOf("room-a" to "training", "room-b" to null)
        val repo = repository()
        repo.refreshRooms()
        assertEquals(mapOf("room-a" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `a blank role is ignored`() = runTest {
        rooms = listOf("room-a" to "  ")
        val repo = repository()
        repo.refreshRooms()
        assertEquals(emptyMap<String, String>(), repo.roomRoles.value)
    }

    @Test
    fun `roles are saved and come back before the next fetch`() = runTest {
        rooms = listOf("room-a" to "training")
        val store = MemoryRoleStore()
        repository(store).refreshRooms()
        assertEquals(mapOf("room-a" to "training"), store.saved)

        val reopened = repository(store) // a new launch, offline: nothing fetched yet
        assertEquals(mapOf("room-a" to "training"), reopened.roomRoles.value)
    }

    @Test
    fun `a later fetch adds rooms and keeps the ones not in this page`() = runTest {
        val store = MemoryRoleStore(mapOf("room-old" to "customer"))
        val repo = repository(store)
        rooms = listOf("room-new" to "training")
        repo.refreshRooms()
        assertEquals(mapOf("room-old" to "customer", "room-new" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `the role is trimmed`() = runTest {
        rooms = listOf("room-a" to "  training ")
        val repo = repository()
        repo.refreshRooms()
        assertEquals(mapOf("room-a" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `the server is the source of truth - a room that comes back without a role loses the one kept`() = runTest {
        val store = MemoryRoleStore(mapOf("room-a" to "training", "room-b" to "customer"))
        val repo = repository(store)
        rooms = listOf("room-a" to null, "room-b" to "customer")
        repo.refreshRooms()
        assertEquals(mapOf("room-b" to "customer"), repo.roomRoles.value)
        assertEquals(mapOf("room-b" to "customer"), store.saved)
    }

    @Test
    fun `the server is the source of truth - a changed role replaces the old one`() = runTest {
        val repo = repository(MemoryRoleStore(mapOf("room-a" to "customer")))
        rooms = listOf("room-a" to "training")
        repo.refreshRooms()
        assertEquals(mapOf("room-a" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `a blank role from the server also clears the one kept`() = runTest {
        val repo = repository(MemoryRoleStore(mapOf("room-a" to "training")))
        rooms = listOf("room-a" to "  ")
        repo.refreshRooms()
        assertEquals(emptyMap<String, String>(), repo.roomRoles.value)
    }

    @Test
    fun `nothing is written when nothing changed`() = runTest {
        rooms = listOf("room-a" to "training")
        val store = MemoryRoleStore()
        val repo = repository(store)
        repo.refreshRooms()
        repo.refreshRooms()
        assertEquals(1, store.saves)
    }

    // --- the role this device sent when it created a room, until the server says otherwise ---

    @Test
    fun `a room this device just created shows its role before the server lists it`() = runTest {
        val repo = repository()
        repo.rememberLocalRole("room-new", "training")
        assertEquals(mapOf("room-new" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `the local role is trimmed and a blank or missing one is ignored`() = runTest {
        val repo = repository()
        repo.rememberLocalRole("room-a", "  training ")
        repo.rememberLocalRole("room-b", "  ")
        repo.rememberLocalRole("room-c", null)
        assertEquals(mapOf("room-a" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `a server role beats the local one`() = runTest {
        val repo = repository()
        repo.rememberLocalRole("room-a", "customer")
        rooms = listOf("room-a" to "training")
        repo.refreshRooms()
        assertEquals(mapOf("room-a" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `a room the server returns without a role keeps the local one`() = runTest {
        val repo = repository()
        repo.rememberLocalRole("room-a", "training")
        rooms = listOf("room-a" to null)
        repo.refreshRooms()
        assertEquals(mapOf("room-a" to "training"), repo.roomRoles.value)
    }

    @Test
    fun `a room the server does not list yet keeps the local one`() = runTest {
        val repo = repository()
        repo.rememberLocalRole("room-new", "training")
        rooms = listOf("room-old" to "customer")
        repo.refreshRooms()
        assertEquals(mapOf("room-new" to "training", "room-old" to "customer"), repo.roomRoles.value)
    }

    @Test
    fun `once the server has a role for a room a later local role does not override it`() = runTest {
        val repo = repository()
        rooms = listOf("room-a" to "customer")
        repo.refreshRooms()
        repo.rememberLocalRole("room-a", "training")
        assertEquals(mapOf("room-a" to "customer"), repo.roomRoles.value)
    }

    @Test
    fun `local roles are saved and come back before the next fetch`() = runTest {
        val store = MemoryRoleStore()
        repository(store).rememberLocalRole("room-new", "training")
        assertEquals(mapOf("room-new" to "training"), store.savedLocal)

        val reopened = repository(store) // a new launch, offline: nothing fetched yet
        assertEquals(mapOf("room-new" to "training"), reopened.roomRoles.value)
    }

    @Test
    fun `a local role is dropped from the store once the server has taken over`() = runTest {
        val store = MemoryRoleStore()
        val repo = repository(store)
        repo.rememberLocalRole("room-a", "training")
        rooms = listOf("room-a" to "training")
        repo.refreshRooms()
        assertEquals(emptyMap<String, String>(), store.savedLocal)
        assertEquals(mapOf("room-a" to "training"), store.saved)
    }
}
