package com.chat360.chatbot.domain

import com.chat360.chatbot.MainDispatcherRule
import com.chat360.chatbot.network.ws.Chat360WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.coroutines.ContinuationInterceptor

/**
 * The heartbeat, reconnect backoff and ack-retry timers used to run on `Dispatchers.Default` while
 * the rest of [ChatRepository] (socket callbacks, the view model) touched the same unsynchronised
 * state from the main thread - `ensureReconnecting()` and `openSocket()` ran on a pool thread on
 * every resend/reconnect. They now share the main thread with everything else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryThreadingTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun timerDispatcherOf(repo: ChatRepository) = ChatRepository::class.java.getDeclaredField("repoScope").let {
        it.isAccessible = true
        (it.get(repo) as CoroutineScope).coroutineContext[ContinuationInterceptor]
    }

    @Test
    fun `timers are confined to the main dispatcher by default, never the Default pool`() {
        val dispatcher = timerDispatcherOf(ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1"))

        assertSame(Dispatchers.Main.immediate, dispatcher)
        assertNotEquals(Dispatchers.Default, dispatcher)
    }

    @Test
    fun `a resend timer is driven by the injected dispatcher`() {
        val scheduler = TestCoroutineScheduler()
        val repo = ChatRepository(baseUrl = "https://example.invalid", botId = "bot-1", timerDispatcher = StandardTestDispatcher(scheduler))
        val sent = captureSentFrames(repo)

        repo.sendFreeText("hello")
        assertEquals("the first attempt goes out immediately", 1, sent.size)

        scheduler.advanceTimeBy(15_001) // AckTracker's first retry delay, in virtual time
        scheduler.runCurrent()

        assertEquals("the unacked message is resent by the timer", 2, sent.size)
    }

    private fun captureSentFrames(repo: ChatRepository): MutableList<String> {
        val sent = mutableListOf<String>()
        val wsClient = ChatRepository::class.java.getDeclaredField("wsClient").apply { isAccessible = true }.get(repo) as Chat360WebSocketClient
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

    // --- disconnect() during session setup ---

    /** Serves session-init after a delay, and records every path requested (a socket open shows up as /ws/...). */
    private fun serverRecording(paths: CopyOnWriteArrayList<String>): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                paths += path
                return if (path.contains("/session/")) {
                    MockResponse().setBody("""{"room_id":"room-1","owner_id":"owner-1","session_token":"tok","nodeType":"INIT","targetId":"t1"}""")
                        .setBodyDelay(700, java.util.concurrent.TimeUnit.MILLISECONDS)
                } else {
                    MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        return server
    }

    private fun connectInBackground(repo: ChatRepository) = thread {
        runBlocking { repo.connect(onEvent = {}, onConnected = {}, onError = {}) }
    }

    @Test
    fun `disconnecting while the session is still being established never opens a socket`() {
        val paths = CopyOnWriteArrayList<String>()
        val server = serverRecording(paths)
        try {
            val repo = ChatRepository(baseUrl = server.url("/").toString().trimEnd('/'), botId = "disconnect-race-${System.nanoTime()}")
            val connecting = connectInBackground(repo)
            Thread.sleep(250) // session-init is now in flight

            repo.disconnect()
            connecting.join(10_000)
            Thread.sleep(300)

            assertTrue("a socket was opened after disconnect(): $paths", paths.none { it.contains("/ws/") })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `an undisturbed connect still opens its socket - the guard does not block a normal session`() {
        val paths = CopyOnWriteArrayList<String>()
        val server = serverRecording(paths)
        try {
            val repo = ChatRepository(baseUrl = server.url("/").toString().trimEnd('/'), botId = "connect-control-${System.nanoTime()}")
            connectInBackground(repo).join(10_000)
            Thread.sleep(300)

            assertTrue("no socket was opened for a normal connect: $paths", paths.any { it.contains("/ws/") })
            repo.disconnect()
        } finally {
            server.shutdown()
        }
    }
}
