package com.chat360.chatbot.cache

import com.chat360.chatbot.network.rest.dto.thirdparty.RoomDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dashboard list must follow the server: no empty rooms, rooms with messages shown even when
 * unnamed, and rooms this device already had a local row for still synced (deleted / renamed). */
class RoomServerSyncTest {

    private fun room(id: String, name: String = "", status: String = "active", sessions: Int? = 1) =
        RoomDto(roomId = id, roomName = name, status = status, sessionCount = sessions, updatedAt = "2026-09-19T05:00:00Z")

    private suspend fun sync(dao: FakeChatCacheDao, rooms: List<RoomDto>): List<CachedConversationEntity> {
        val repo = ChatCacheRepository(dao)
        repo.syncLocalConversations("bot", rooms)
        repo.syncAgentRooms("bot", repo.thirdPartyRoomConversations("bot", rooms))
        return repo.conversations("bot").first()
    }

    @Test
    fun `an empty room is not listed even when it has a name`() = runTest {
        val list = sync(FakeChatCacheDao(), listOf(room("empty", name = "x", sessions = 0), room("used", name = "hello")))
        assertEquals(listOf("agent-room:used"), list.map { it.id })
    }

    @Test
    fun `an unnamed room that has messages is listed`() = runTest {
        val list = sync(FakeChatCacheDao(), listOf(room("r1", name = "", sessions = 2)))
        assertEquals(listOf(UNNAMED_ROOM_TITLE), list.map { it.title })
    }

    @Test
    fun `a room the server omits the count for and never named is treated as empty`() = runTest {
        val list = sync(FakeChatCacheDao(), listOf(room("r1", name = "", sessions = null)))
        assertEquals(emptyList<String>(), list.map { it.id })
    }

    @Test
    fun `a local chat the server marks inactive is kept`() = runTest {
        val dao = FakeChatCacheDao()
        dao.upsertConversation(CachedConversationEntity("local-1", "bot", "r1", "old chat", 1, 5))
        val list = sync(dao, listOf(room("r1", name = "old chat", status = "INACTIVE")))
        assertEquals(listOf("local-1"), list.map { it.id })
    }

    @Test
    fun `a local chat takes the name the server holds`() = runTest {
        val dao = FakeChatCacheDao()
        dao.upsertConversation(CachedConversationEntity("local-1", "bot", "r1", "hi", 1, 5))
        val list = sync(dao, listOf(room("r1", name = "Venue features")))
        assertEquals(listOf("local-1" to "Venue features"), list.map { it.id to it.title })
    }
}
