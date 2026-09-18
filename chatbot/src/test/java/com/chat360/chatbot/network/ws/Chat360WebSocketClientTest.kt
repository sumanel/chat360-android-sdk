package com.chat360.chatbot.network.ws

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Regression tests for duplicate sockets: a replaced or intentionally closed socket's late
 * close/failure event used to be delivered as if the live socket had died, which drove a
 * reconnect cycle that kept closing healthy sockets. */
class Chat360WebSocketClientTest {

    private lateinit var server: MockWebServer
    private val serverSockets = CopyOnWriteArrayList<WebSocket>()
    private val serverSawClose = AtomicInteger(0)
    private lateinit var client: Chat360WebSocketClient

    private val opens = AtomicInteger(0)
    private val closes = CopyOnWriteArrayList<Int>()
    private val failures = CopyOnWriteArrayList<Throwable>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = Chat360WebSocketClient(postToMain = { it.run() })
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun enqueueSocket() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { serverSockets += webSocket }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    serverSawClose.incrementAndGet()
                    webSocket.close(code, reason)
                }
            }),
        )
    }

    private fun connect() = client.connect(
        wsUrl = server.url("/ws").toString().replace("http", "ws"),
        onOpen = { opens.incrementAndGet() },
        onMessage = {},
        onClosed = { code, _ -> closes += code },
        onFailure = { failures += it },
    )

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: $what")
            Thread.sleep(20)
        }
    }

    @Test
    fun `connecting again closes the previous socket and never reports its close as the live socket's`() {
        enqueueSocket(); enqueueSocket()
        connect()
        awaitUntil("first open") { opens.get() == 1 }

        connect()
        awaitUntil("second open") { opens.get() == 2 }
        awaitUntil("the replaced socket to actually be closed on the server") { serverSawClose.get() >= 1 }
        Thread.sleep(300)

        assertTrue("stale close was delivered for the live socket: $closes", closes.isEmpty())
        assertTrue("stale failure was delivered for the live socket: $failures", failures.isEmpty())
    }

    @Test
    fun `an intentional close is not reported back as a dropped connection`() {
        enqueueSocket()
        connect()
        awaitUntil("open") { opens.get() == 1 }

        client.close()
        Thread.sleep(400)

        assertTrue("close() was reported as a connection drop: $closes", closes.isEmpty())
    }

    @Test
    fun `a genuine server-side close of the live socket is still reported`() {
        enqueueSocket()
        connect()
        awaitUntil("open") { opens.get() == 1 }

        serverSockets.single().close(1001, "going away")

        awaitUntil("the live socket's own close to be reported") { closes.isNotEmpty() }
        assertEquals(1001, closes.first())
    }
}
