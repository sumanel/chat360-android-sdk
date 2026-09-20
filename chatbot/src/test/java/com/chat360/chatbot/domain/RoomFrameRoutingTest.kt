package com.chat360.chatbot.domain

import com.chat360.chatbot.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Only one room is ever connected, but a slow bot reply can land after the user has moved to another
 * room. Such a frame carries its own `room_id` and must be dropped, or two conversations end up in one chat.
 */
class RoomFrameRoutingTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private var nextRoom = AtomicInteger(0)

    private fun withRepository(block: (ChatRepository, MutableList<String>) -> Unit) {
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.requestUrl?.encodedPath.orEmpty().contains("/session/")) {
                        MockResponse().setBody("""{"room_id":"room-${nextRoom.incrementAndGet()}","owner_id":"owner-1","session_token":"tok","nodeType":"INIT","targetId":"t1"}""")
                    } else {
                        MockResponse().setResponseCode(404)
                    }
            }
            start()
        }
        try {
            val repo = ChatRepository(baseUrl = server.url("/").toString().trimEnd('/'), botId = "routing-${System.nanoTime()}", sessionStore = null)
            thread { runBlocking { repo.connect(onEvent = {}, onConnected = {}, onError = {}) } }.join(10_000)
            block(repo, mutableListOf())
            repo.disconnect()
        } finally {
            server.shutdown()
        }
    }

    private var frameCounter = 0

    /** Feeds a bot message for [room]; returns true when it got past the room guard (i.e. would reach the chat). */
    private fun ChatRepository.deliver(room: String, sink: MutableList<String>): Boolean {
        val text = "msg-${++frameCounter}-for-$room"
        val raw = """{"user":"bot","room_id":"$room","data":{"nodeType":"TEXT","nodeId":"n$frameCounter","questionText":"$text"}}"""
        val method = ChatRepository::class.java.getDeclaredMethod("handleIncoming", String::class.java, Function1::class.java)
        method.isAccessible = true
        val before = sink.size
        method.invoke(this, raw, { r: String -> sink += r })
        return sink.size > before
    }

    private fun switchTo(repo: ChatRepository) = runBlocking { repo.startNewSession() }

    @Test
    fun `a frame for the connected room is delivered and one for another room is dropped`() = withRepository { repo, seen ->
        assertEquals(true, repo.deliver("room-1", seen))
        assertEquals(false, repo.deliver("room-9", seen))
        assertEquals(1, seen.size)
    }

    @Test
    fun `a late reply from the room the user just left never reaches the new room`() = withRepository { repo, seen ->
        switchTo(repo) // room-2 is now connected
        assertEquals("the old room's reply leaked", false, repo.deliver("room-1", seen))
        assertEquals("the current room's reply was lost", true, repo.deliver("room-2", seen))
    }

    @Test
    fun `switching through several rooms in a row only ever accepts the last room`() = withRepository { repo, seen ->
        switchTo(repo) // room-2
        switchTo(repo) // room-3
        switchTo(repo) // room-4

        val accepted = listOf("room-1", "room-2", "room-3", "room-4").filter { repo.deliver(it, seen) }

        assertEquals(listOf("room-4"), accepted)
    }

    @Test
    fun `replies from two rooms interleaved while switching land only in the room that is connected`() = withRepository { repo, seen ->
        val results = mutableListOf<Pair<String, Boolean>>()
        // A reply for the old room, the switch, another old reply, a reply for the new room, one more old reply.
        results += "room-1" to repo.deliver("room-1", seen)
        switchTo(repo) // room-2
        results += "room-1" to repo.deliver("room-1", seen)
        results += "room-2" to repo.deliver("room-2", seen)
        results += "room-1" to repo.deliver("room-1", seen)

        assertEquals(
            listOf("room-1" to true, "room-1" to false, "room-2" to true, "room-1" to false),
            results,
        )
    }
}
