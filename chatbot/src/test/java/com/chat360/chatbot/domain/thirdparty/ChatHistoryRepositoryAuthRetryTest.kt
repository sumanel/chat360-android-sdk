package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.cache.FakeChatCacheDao
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** End-to-end coverage of [ChatHistoryRepository]'s 401-retry-once logic against a real
 * [ThirdPartyTasksApiService] (backed by [MockWebServer]) - the point being to exercise the
 * actual HTTP status-code check in [ThirdPartyTasksApiService]'s exceptions, not a mock of it. */
class ChatHistoryRepositoryAuthRetryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ChatHistoryRepository
    private lateinit var cacheDao: FakeChatCacheDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDao = FakeChatCacheDao()
        val apiService = ThirdPartyTasksApiService(server.url("/").toString())
        repository = ChatHistoryRepository(
            apiService = apiService,
            tokenManager = ThirdPartyTokenManager(apiService, clientId = "client-1", apiKey = "api-key-1"),
            cache = ChatCacheRepository(cacheDao),
            clientId = "client-1",
            botId = "bot-1",
            endUserId = "agent-1",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refreshRooms retries once after a 401 and succeeds with the refreshed token`() = runTest {
        server.enqueue(tokenResponse("token-1"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(tokenResponse("token-2"))
        server.enqueue(roomsListResponse())

        val result = repository.refreshRooms()

        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals(4, server.requestCount)
        // First rooms/list attempt used the pre-refresh token; the retry used the new one.
        server.takeRequest() // auth/token
        val firstAttempt = server.takeRequest() // rooms/list (401)
        assertEquals("Bearer token-1", firstAttempt.getHeader("Authorization"))
        server.takeRequest() // auth/token (refresh)
        val retryAttempt = server.takeRequest() // rooms/list (success)
        assertEquals("Bearer token-2", retryAttempt.getHeader("Authorization"))
    }

    @Test
    fun `refreshRooms does not retry on a non-401 failure`() = runTest {
        server.enqueue(tokenResponse("token-1"))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository.refreshRooms()

        assertNull(result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `renameRoom is best-effort and never throws out to the caller`() = runTest {
        server.enqueue(tokenResponse("token-1"))
        server.enqueue(MockResponse().setResponseCode(500))

        // Must not throw - this is the whole point of the best-effort contract.
        repository.renameRoom(roomId = "room-1", roomName = "New name")

        assertEquals(2, server.requestCount)
    }

    private fun tokenResponse(bearerToken: String) = MockResponse().setBody(
        """{"success":true,"data":{"bearer_token":"$bearerToken","token_type":"Bearer","expires_in":3600}}""",
    )

    private fun roomsListResponse() = MockResponse().setBody(
        """
        {"success":true,"data":{"client_id":"client-1","bot_id":"bot-1","rooms":[
            {"room_id":"room-1","room_name":"Test Room","agent_id":"agent-1","status":"active",
             "session_ids":[],"session_count":0}
        ],"total_count":1,"has_more":false}}
        """.trimIndent(),
    )
}
