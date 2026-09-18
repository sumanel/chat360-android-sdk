package com.chat360.chatbot.cache

import com.chat360.chatbot.network.rest.dto.thirdparty.RoomDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression tests for "latest chat isn't on top of history": the sidebar used to be ordered by
 * the rooms/list *response position* (`fetchedAt - index`), ignoring the timestamps the server
 * sends, so its order was whatever order the API happened to return. */
class RoomOrderingTest {

    private fun room(id: String, updatedAt: String?, createdAt: String? = null) =
        RoomDto(roomId = id, roomName = id, createdAt = createdAt, updatedAt = updatedAt)

    @Test
    fun `rooms are ordered newest first regardless of the order the server returns them`() {
        val repo = ChatCacheRepository(FakeChatCacheDao())
        // Deliberately oldest-first, then shuffled - neither may leak into the result.
        val response = listOf(
            room("old", "2026-09-01T10:00:00Z"),
            room("newest", "2026-09-17T09:30:00.123456Z"),
            room("middle", "2026-09-10T12:00:00+00:00"),
        )
        assertEquals(listOf("newest", "middle", "old"), repo.thirdPartyRoomConversations("bot", response).map { it.roomId })
        assertEquals(
            listOf("newest", "middle", "old"),
            repo.thirdPartyRoomConversations("bot", response.reversed()).map { it.roomId },
        )
    }

    @Test
    fun `updated_at wins over created_at and created_at is the fallback`() {
        val repo = ChatCacheRepository(FakeChatCacheDao())
        val result = repo.thirdPartyRoomConversations(
            "bot",
            listOf(
                room("created-late-updated-early", updatedAt = "2026-09-02T00:00:00Z", createdAt = "2026-09-16T00:00:00Z"),
                room("only-created", updatedAt = null, createdAt = "2026-09-10T00:00:00Z"),
            ),
        )
        assertEquals(listOf("only-created", "created-late-updated-early"), result.map { it.roomId })
    }

    @Test
    fun `a locally newer send is not rolled back by a sync whose server timestamp lags`() = runTest {
        val dao = FakeChatCacheDao()
        val repo = ChatCacheRepository(dao)
        val server = listOf(room("a", "2026-09-01T00:00:00Z"), room("b", "2026-09-05T00:00:00Z"))
        repo.syncAgentRooms("bot", repo.thirdPartyRoomConversations("bot", server))

        // The user just messaged the older room "a" on this device.
        val a = dao.findConversation("bot", "a")!!
        val justNow = System.currentTimeMillis()
        dao.touch(a.id, justNow)

        // The next refresh still reports the stale server time for "a".
        repo.syncAgentRooms("bot", repo.thirdPartyRoomConversations("bot", server))

        assertEquals(listOf("a", "b"), repo.conversations("bot").first().map { it.roomId })
    }

    @Test
    fun `timestamp parser handles epoch seconds, epoch millis and ISO formats`() {
        val expected = 1_757_500_000_000L
        assertEquals(expected, parseServerTimestampMs("1757500000"))
        assertEquals(expected, parseServerTimestampMs("1757500000000"))
        assertEquals(1_757_500_000_000L, parseServerTimestampMs("2025-09-10T10:26:40Z"))
        assertEquals(1_757_500_000_000L, parseServerTimestampMs("2025-09-10T15:56:40+05:30"))
        assertEquals(1_757_500_000_000L, parseServerTimestampMs("2025-09-10 10:26:40"))
        assertEquals(1_757_500_000_123L, parseServerTimestampMs("2025-09-10T10:26:40.123456Z"))
        assertNull(parseServerTimestampMs(null))
        assertNull(parseServerTimestampMs("not a date"))
    }
}
