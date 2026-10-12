package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.cache.FakeChatCacheDao
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "Room deletion is not working - the deleted room reappears as soon as the sidebar is opened again": deleting only
 * asks the server to mark the room inactive, and `rooms/list` keeps returning inactive rooms, so the next refresh
 * put it straight back. A room deleted on this device now stays out of the list whatever the server returns.
 */
class DeletedRoomsTest {

    private class MemoryDeletedStore(var saved: Set<String> = emptySet()) : DeletedRoomStore {
        override fun load(botId: String) = saved
        override fun add(botId: String, roomId: String) { saved = saved + roomId }
    }

    private lateinit var http: MockWebServer
    /** What the server keeps returning - including the rooms it has been told to mark inactive. */
    private var serverRooms = listOf("a", "b", "c")
    /** Set true to make the mark-inactive request fail, like an unreachable server. */
    private var statusUpdateFails = false

    @Before
    fun setUp() {
        http = MockWebServer()
        http.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                if (path.endsWith("/auth/token")) {
                    return MockResponse().setBody("""{"success":true,"data":{"bearer_token":"token-1","token_type":"Bearer","expires_in":3600}}""")
                }
                if (path.endsWith("/room/update/status")) {
                    return if (statusUpdateFails) MockResponse().setResponseCode(500) else MockResponse().setBody("""{"success":true,"data":{"room_id":"x","status":"INACTIVE"}}""")
                }
                val json = serverRooms.joinToString(",") { id ->
                    """{"room_id":"$id","room_name":"Chat $id","status":"ACTIVE","updated_at":"2026-09-18T10:00:00Z","session_count":1}"""
                }
                return MockResponse().setBody("""{"success":true,"data":{"rooms":[$json],"total_count":${serverRooms.size},"has_more":false}}""")
            }
        }
        http.start()
    }

    @After
    fun tearDown() = http.shutdown()

    private fun repository(store: DeletedRoomStore?, cache: ChatCacheRepository = ChatCacheRepository(FakeChatCacheDao())): ChatHistoryRepository {
        val api = ThirdPartyTasksApiService(http.url("/").toString())
        return ChatHistoryRepository(api, ThirdPartyTokenManager(api, "client-1", "api-key-1"), cache, "client-1", "bot-1", "agent-1", deletedRoomStore = store)
    }

    @Test
    fun `a deleted room stays out of the list even though the server still returns it`() = runTest {
        val repo = repository(MemoryDeletedStore())
        assertEquals(listOf("a", "b", "c"), repo.refreshRooms()!!.mapNotNull { it.roomId }.sorted())

        repo.markRoomInactive("b")

        assertEquals(listOf("a", "c"), repo.refreshRooms()!!.mapNotNull { it.roomId }.sorted())
    }

    @Test
    fun `the room stays deleted when the server request fails`() = runTest {
        val repo = repository(MemoryDeletedStore())
        repo.refreshRooms()
        statusUpdateFails = true

        repo.markRoomInactive("b")

        assertEquals(listOf("a", "c"), repo.refreshRooms()!!.mapNotNull { it.roomId }.sorted())
    }

    @Test
    fun `a delete is remembered across launches`() = runTest {
        val store = MemoryDeletedStore()
        repository(store).markRoomInactive("b")

        // A new repository, as after the app restarts.
        val afterRestart = repository(store)

        assertEquals(listOf("a", "c"), afterRestart.refreshRooms()!!.mapNotNull { it.roomId }.sorted())
    }

    @Test
    fun `forgetting a room takes effect immediately and without any network call`() = runTest {
        val repo = repository(MemoryDeletedStore())
        repo.refreshRooms()
        val requestsBefore = http.requestCount

        repo.forgetRoom("a")

        assertEquals(requestsBefore, http.requestCount)
        assertTrue("a" !in repo.refreshRooms()!!.mapNotNull { it.roomId })
    }

    @Test
    fun `a deleted room already in the cache is dropped by the next refresh`() = runTest {
        val cache = ChatCacheRepository(FakeChatCacheDao())
        val repo = repository(MemoryDeletedStore(), cache)
        repo.refreshRooms()
        assertEquals(3, cache.conversations("bot-1").first().size)

        repo.forgetRoom("c")
        repo.refreshRooms()

        assertEquals(listOf("a", "b"), cache.conversations("bot-1").first().mapNotNull { it.roomId }.sorted())
    }
}
