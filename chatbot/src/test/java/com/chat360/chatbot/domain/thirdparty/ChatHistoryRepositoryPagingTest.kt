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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Regression tests for the history list silently dropping older chats. `rooms/list` called with no
 * `limit` returns only the server's default page (20 rooms, newest first) and sets `has_more`; the SDK
 * never asked for the rest. Soft-deleted rooms count toward that page, so as they piled up the real,
 * older chats fell off the end of the list.
 */
class ChatHistoryRepositoryPagingTest {

    private class Room(val id: String, val name: String?, val status: String = "active", val sessions: Int = 1)

    /** Behaves like the real endpoint: honours limit/offset, defaults to a page of 20, reports has_more. */
    private class RoomsServer {
        var rooms: List<Room> = emptyList()
        var serverMaxPage = 100
        var defaultPage = 20
        var ignoreOffset = false
        var failFromOffset: Int? = null
        /** (limit, offset) of every rooms/list request, as the server saw it. */
        val requests = CopyOnWriteArrayList<Pair<String?, String?>>()

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                if (path.endsWith("/auth/token")) {
                    return MockResponse().setBody("""{"success":true,"data":{"bearer_token":"token-1","token_type":"Bearer","expires_in":3600}}""")
                }
                val limit = request.requestUrl?.queryParameter("limit")
                val offset = request.requestUrl?.queryParameter("offset")
                requests += limit to offset
                val start = if (ignoreOffset) 0 else offset?.toInt() ?: 0
                if (failFromOffset != null && start >= failFromOffset!!) return MockResponse().setResponseCode(500)
                val size = minOf(limit?.toInt() ?: defaultPage, serverMaxPage)
                val page = rooms.drop(start).take(size)
                val json = page.joinToString(",") { r ->
                    val name = r.name?.let { "\"$it\"" } ?: "null"
                    """{"room_id":"${r.id}","room_name":$name,"status":"${r.status}","updated_at":"2026-09-18T10:00:00Z","session_count":${r.sessions}}"""
                }
                return MockResponse().setBody(
                    """{"success":true,"data":{"rooms":[$json],"total_count":${rooms.size},"has_more":${start + page.size < rooms.size}}}""",
                )
            }
        }
    }

    private lateinit var http: MockWebServer
    private val roomsServer = RoomsServer()
    private lateinit var repository: ChatHistoryRepository
    private lateinit var cache: ChatCacheRepository

    @Before
    fun setUp() {
        http = MockWebServer()
        http.dispatcher = roomsServer.dispatcher
        http.start()
        cache = ChatCacheRepository(FakeChatCacheDao())
        val api = ThirdPartyTasksApiService(http.url("/").toString())
        repository = ChatHistoryRepository(api, ThirdPartyTokenManager(api, "client-1", "api-key-1"), cache, "client-1", "bot-1", "agent-1")
    }

    @After
    fun tearDown() = http.shutdown()

    private fun named(count: Int, prefix: String = "Chat") = (1..count).map { Room("$prefix-$it", "$prefix $it") }

    @Test
    fun `every page is fetched, not just the server's default first page`() = runTest {
        roomsServer.rooms = named(250)

        val result = repository.refreshRooms()

        assertNotNull(result)
        assertEquals("only part of the list was fetched", 250, result!!.size)
        assertEquals(listOf<Pair<String?, String?>>("100" to "0", "100" to "100", "100" to "200"), roomsServer.requests.toList())
    }

    @Test
    fun `an older real chat behind many deleted rooms still shows up`() = runTest {
        // The reported shape: newest-first, the front of the list is soft-deleted/abandoned rooms and the
        // real conversation sits past the server's default page of 20.
        val deleted = (1..110).map { Room("gone-$it", "Deleted $it", status = "INACTIVE") }
        roomsServer.rooms = deleted + Room("old-real", "Creta is a good car")

        val result = repository.refreshRooms()

        assertEquals(listOf("Creta is a good car"), result!!.map { it.title })
    }

    @Test
    fun `a server that caps a page below the requested size is paged by what it actually returned`() = runTest {
        roomsServer.serverMaxPage = 3
        roomsServer.rooms = named(7)

        val result = repository.refreshRooms()

        assertEquals(7, result!!.size)
        assertEquals(listOf<String?>("0", "3", "6"), roomsServer.requests.map { it.second })
    }

    @Test
    fun `a single page makes a single request`() = runTest {
        roomsServer.rooms = named(5)

        val result = repository.refreshRooms()

        assertEquals(5, result!!.size)
        assertEquals(1, roomsServer.requests.size)
    }

    @Test
    fun `a server that ignores offset and repeats one page cannot loop forever`() = runTest {
        roomsServer.ignoreOffset = true
        roomsServer.serverMaxPage = 3
        roomsServer.rooms = named(9)

        val result = repository.refreshRooms()

        assertEquals("duplicates were not collapsed", 3, result!!.size)
        assertTrue("kept requesting: ${roomsServer.requests}", roomsServer.requests.size <= 2)
    }

    @Test
    fun `a failure on a later page fails the whole refresh and leaves the cached list untouched`() = runTest {
        roomsServer.rooms = named(4)
        assertEquals(4, repository.refreshRooms()!!.size) // the local list now holds all four

        roomsServer.rooms = named(250)
        roomsServer.failFromOffset = 100 // page 2 blows up

        assertNull("a partial list was returned as if it were complete", repository.refreshRooms())

        // The sync deletes cached rooms missing from the fetched list - a partial result would have
        // wiped everything on the pages that never loaded.
        assertEquals(4, cache.conversations("bot-1").first().size)
    }

    @Test
    fun `soft-deleted rooms are still dropped after paging`() = runTest {
        roomsServer.rooms = named(3) + (1..3).map { Room("gone-$it", "Deleted $it", status = "INACTIVE") }

        val result = repository.refreshRooms()

        assertEquals(listOf("Chat 1", "Chat 2", "Chat 3"), result!!.map { it.title }.sorted())
    }
}
