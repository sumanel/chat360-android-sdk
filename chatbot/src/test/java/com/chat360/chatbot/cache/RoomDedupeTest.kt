package com.chat360.chatbot.cache

import com.chat360.chatbot.network.rest.dto.thirdparty.RoomDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression tests for the same chat listed twice after a refresh: the local conversation plus a
 * synced `agent-room:` twin titled "Conversation" - which then reshuffled the list and let a room
 * lookup land on the empty twin, so the real chat's history seemed to vanish. */
class RoomDedupeTest {

    private fun room(id: String, name: String, updatedAt: String) =
        RoomDto(roomId = id, roomName = name, updatedAt = updatedAt)

    private suspend fun ChatCacheRepository.list() = conversations("bot").first()

    @Test
    fun `a room this device already has is not listed a second time after a refresh`() = runTest {
        val dao = FakeChatCacheDao()
        val repo = ChatCacheRepository(dao)
        dao.upsertConversation(CachedConversationEntity("local-1", "bot", "room-1", "creta is good car", createdAt = 1, updatedAt = 5))

        repo.syncAgentRooms("bot", repo.thirdPartyRoomConversations("bot", listOf(room("room-1", "", "2026-09-17T10:00:00Z"))))

        val list = repo.list()
        assertEquals(listOf("local-1"), list.map { it.id })
        assertEquals("creta is good car", list.single().title)
    }

    @Test
    fun `an existing twin from an earlier refresh is cleaned up`() = runTest {
        val dao = FakeChatCacheDao()
        val repo = ChatCacheRepository(dao)
        dao.upsertConversation(CachedConversationEntity("local-1", "bot", "room-1", "creta is good car", 1, 5))
        dao.upsertConversation(CachedConversationEntity("agent-room:room-1", "bot", "room-1", "Conversation", 1, 9_999_999_999_999))

        repo.syncAgentRooms("bot", repo.thirdPartyRoomConversations("bot", listOf(room("room-1", "", "2026-09-17T10:00:00Z"))))

        assertEquals(listOf("local-1"), repo.list().map { it.id })
        // ...and the room now resolves to the row that owns the cached messages.
        assertEquals("local-1", dao.findConversation("bot", "room-1")?.id)
    }

    @Test
    fun `a nameless server-only room is an abandoned empty room and is not listed`() = runTest {
        val dao = FakeChatCacheDao()
        val repo = ChatCacheRepository(dao)

        repo.syncAgentRooms(
            "bot",
            repo.thirdPartyRoomConversations("bot", listOf(room("ghost", "", "2026-09-17T10:00:00Z"), room("named", "Loan query", "2026-09-16T10:00:00Z"))),
        )

        assertEquals(listOf("Loan query"), repo.list().map { it.title })
    }

    @Test
    fun `the room's recency comes from the server when it is newer than the local one`() = runTest {
        val dao = FakeChatCacheDao()
        val repo = ChatCacheRepository(dao)
        dao.upsertConversation(CachedConversationEntity("local-old", "bot", "room-old", "old", 1, 10))
        dao.upsertConversation(CachedConversationEntity("local-new", "bot", "room-new", "new", 1, 20))

        // Another device talked in room-old more recently.
        repo.syncAgentRooms(
            "bot",
            repo.thirdPartyRoomConversations("bot", listOf(room("room-old", "old", "2026-09-17T10:00:00Z"), room("room-new", "new", "2026-09-01T10:00:00Z"))),
        )

        assertEquals(listOf("local-old", "local-new"), repo.list().map { it.id })
    }
}
