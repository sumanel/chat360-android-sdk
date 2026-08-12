package com.chat360.chatbot.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomDto
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class ConversationSummary(val id: String, val title: String, val updatedAt: Long)

class ChatCacheRepository(private val dao: ChatCacheDao) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Decision: chat state stays live-only, always fetched fresh from the server, with no
         * local fallback - every write below becomes a no-op and every read reports "nothing
         * cached" while this is false. The DAO/schema/methods are left fully intact and
         * functional (not deleted) so this can be flipped back to true later. */
        const val ENABLED = false
    }

    fun conversations(botId: String): Flow<List<CachedConversationEntity>> =
        if (ENABLED) dao.observeConversations(botId) else flowOf(emptyList())

    /** Returns true when this server room already has locally cached messages to replay.
     * A brand-new room (no existing row) is deliberately *not* written to the DB here - an
     * opened-but-untouched chat would otherwise leave an empty entry behind in history forever.
     * The id handed back is purely in-memory until [ensureConversationPersisted] commits it,
     * which only happens once the user actually sends a message (see ChatViewModel.sendMessage). */
    suspend fun activateForRoom(botId: String, roomId: String, pendingId: String?): Pair<String, Boolean> {
        if (!ENABLED) return (pendingId ?: UUID.randomUUID().toString()) to false
        val existing = dao.findConversation(botId, roomId)
        if (existing != null) {
            dao.setRoom(existing.id, roomId, System.currentTimeMillis())
            return existing.id to dao.messages(existing.id).isNotEmpty()
        }
        return (pendingId ?: UUID.randomUUID().toString()) to false
    }

    /** Commits an in-memory conversation id (from [activateForRoom] or a fresh [startNewChat])
     * as a real, visible row - called once, the moment the user sends their first message in
     * it. Idempotent: a conversation that already exists (e.g. the user is replying in one
     * still being flushed) is left alone bar its room mapping. */
    suspend fun ensureConversationPersisted(botId: String, conversationId: String, roomId: String?) {
        if (!ENABLED) return
        val now = System.currentTimeMillis()
        dao.insertConversationIfMissing(CachedConversationEntity(conversationId, botId, roomId = roomId, createdAt = now, updatedAt = now))
        if (roomId != null) dao.setRoom(conversationId, roomId, now)
    }

    suspend fun messages(conversationId: String): List<CachedMessageEntity> =
        if (ENABLED) dao.messages(conversationId) else emptyList()

    suspend fun syncAgentRooms(botId: String, conversations: List<CachedConversationEntity>) {
        if (!ENABLED) return
        dao.replaceAgentRoomConversations(botId, conversations)
    }

    /** Maps the third-party-tasks `rooms/list` result onto the same observable list as local
     * chats, without replacing cached messages. Rooms already marked inactive (soft-deleted via
     * `room/update/status`) are dropped so a background refresh can't resurrect a conversation
     * the user just deleted. Pure mapping, no DB access - unaffected by [ENABLED]. */
    fun thirdPartyRoomConversations(botId: String, rooms: List<RoomDto>): List<CachedConversationEntity> {
        val fetchedAt = System.currentTimeMillis()
        return rooms
            .filterNot { it.status.equals("inactive", ignoreCase = true) }
            .mapIndexed { index, room ->
                CachedConversationEntity(
                    id = "agent-room:${room.roomId}",
                    botId = botId,
                    roomId = room.roomId,
                    title = room.roomName.trim().ifEmpty { "Conversation" },
                    createdAt = fetchedAt - index,
                    updatedAt = fetchedAt - index,
                )
            }
    }

    suspend fun replaceRawHistory(conversationId: String, history: List<RawSocketEnvelope>) {
        if (!ENABLED) return
        val fetchedAt = System.currentTimeMillis()
        dao.replaceMessages(
            conversationId,
            history.mapIndexed { index, envelope ->
                CachedMessageEntity(
                    conversationId = conversationId,
                    kind = "RAW",
                    payload = json.encodeToString(envelope),
                    createdAt = fetchedAt + index,
                )
            },
        )
    }

    suspend fun renameConversation(conversationId: String, title: String) {
        if (!ENABLED) return
        dao.updateTitle(conversationId, title, System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversationId: String) {
        if (!ENABLED) return
        dao.deleteConversation(conversationId)
    }

    suspend fun cacheRaw(conversationId: String, rawEnvelope: String) {
        if (!ENABLED) return
        val now = System.currentTimeMillis()
        dao.insertMessage(CachedMessageEntity(conversationId = conversationId, kind = "RAW", payload = rawEnvelope, createdAt = now))
        dao.touch(conversationId, now)
    }

    suspend fun cacheUserMessage(conversationId: String, text: String, chatMsgId: String?) {
        if (!ENABLED) return
        val now = System.currentTimeMillis()
        dao.insertMessage(CachedMessageEntity(conversationId = conversationId, kind = "USER", payload = text, chatMsgId = chatMsgId, createdAt = now))
        val title = text.trim().replace(Regex("\\s+"), " ").take(80)
        if (title.isNotBlank()) dao.touchAndSetTitleIfUnset(conversationId, title, now) else dao.touch(conversationId, now)
    }
}
