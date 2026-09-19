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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Paging of the history list. `rooms/list` is newest-first and soft-deleted rooms count toward a page,
 * so the real, older chats can sit behind many empty ones. The first page loads on refresh and older
 * pages come in one at a time through "Load more" while the server reports `has_more`.
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
    fun `refresh fetches only the first page and reports that more is available`() = runTest {
        roomsServer.rooms = named(120)

        val result = repository.refreshRooms()

        assertEquals(50, result!!.size)
        assertTrue(repository.hasMoreRooms)
        assertEquals(listOf<Pair<String?, String?>>("50" to "0"), roomsServer.requests.toList())
    }

    @Test
    fun `load more adds the next page until the server says there is no more`() = runTest {
        roomsServer.rooms = named(120)
        repository.refreshRooms()

        assertTrue(repository.loadMoreRooms())
        assertEquals(100, cache.conversations("bot-1").first().size)
        assertTrue(repository.hasMoreRooms)

        assertTrue(repository.loadMoreRooms())
        assertEquals(120, cache.conversations("bot-1").first().size)
        assertFalse(repository.hasMoreRooms)
        assertEquals(listOf<String?>("0", "50", "100"), roomsServer.requests.map { it.second })
    }

    @Test
    fun `a single page of rooms offers no load more`() = runTest {
        roomsServer.rooms = named(5)

        assertEquals(5, repository.refreshRooms()!!.size)

        assertFalse(repository.hasMoreRooms)
        assertEquals(1, roomsServer.requests.size)
    }

    @Test
    fun `an older chat behind many empty rooms is reached by loading more`() = runTest {
        // Newest-first, the front of the list is empty (never-used) rooms and the real conversation
        // sits past the first page.
        val empty = (1..110).map { Room("empty-$it", "Empty $it", sessions = 0) }
        roomsServer.rooms = empty + Room("old-real", "Creta is a good car")

        assertTrue(repository.refreshRooms()!!.isEmpty())
        repository.loadMoreRooms()
        repository.loadMoreRooms()

        assertEquals(listOf("Creta is a good car"), cache.conversations("bot-1").first().map { it.title })
    }

    @Test
    fun `a server that caps a page below the requested size is paged by what it actually returned`() = runTest {
        roomsServer.serverMaxPage = 3
        roomsServer.rooms = named(7)

        repository.refreshRooms()
        repository.loadMoreRooms()
        repository.loadMoreRooms()

        assertEquals(7, cache.conversations("bot-1").first().size)
        assertEquals(listOf<String?>("0", "3", "6"), roomsServer.requests.map { it.second })
    }

    @Test
    fun `a server that ignores offset and repeats one page cannot keep load more alive`() = runTest {
        roomsServer.ignoreOffset = true
        roomsServer.serverMaxPage = 3
        roomsServer.rooms = named(9)

        repository.refreshRooms()
        repository.loadMoreRooms()

        assertEquals("duplicates were not collapsed", 3, cache.conversations("bot-1").first().size)
    }

    @Test
    fun `refresh after loading more re-fetches what was already loaded so the list does not collapse`() = runTest {
        roomsServer.rooms = named(120)
        repository.refreshRooms()
        repository.loadMoreRooms() // 100 loaded

        repository.refreshRooms()

        assertEquals("100" to "0", roomsServer.requests.last())
        assertEquals(100, cache.conversations("bot-1").first().size)
    }

    @Test
    fun `a failed load more reports failure and leaves the list as it was`() = runTest {
        roomsServer.rooms = named(120)
        repository.refreshRooms()
        roomsServer.failFromOffset = 50

        assertFalse(repository.loadMoreRooms())

        assertEquals(50, cache.conversations("bot-1").first().size)
        assertTrue("retry must still be offered", repository.hasMoreRooms)
    }

    @Test
    fun `a failed refresh returns null and leaves the cached list untouched`() = runTest {
        roomsServer.rooms = named(4)
        assertEquals(4, repository.refreshRooms()!!.size)

        roomsServer.failFromOffset = 0

        assertNull(repository.refreshRooms())
        assertEquals(4, cache.conversations("bot-1").first().size)
    }

    @Test
    fun `inactive rooms are listed along with active ones`() = runTest {
        roomsServer.rooms = named(3) + (1..3).map { Room("gone-$it", "Deleted $it", status = "INACTIVE") }

        val result = repository.refreshRooms()

        assertEquals(listOf("Chat 1", "Chat 2", "Chat 3", "Deleted 1", "Deleted 2", "Deleted 3"), result!!.map { it.title }.sorted())
    }

    @Test
    fun `a room the server sends with a null name does not fail the whole list`() = runTest {
        // The server sends `"room_name": null` for a never-named room; that used to fail the decode of
        // the entire rooms/list response, so no server room ever loaded.
        roomsServer.rooms = listOf(Room("named", "Creta"), Room("unnamed", null))

        val result = repository.refreshRooms()

        assertNotNull("a null room_name failed the whole fetch", result)
        assertEquals(listOf("Conversation", "Creta"), result!!.map { it.title }.sorted())
    }
}
