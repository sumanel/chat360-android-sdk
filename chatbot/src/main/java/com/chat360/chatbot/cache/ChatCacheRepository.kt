package com.chat360.chatbot.cache

import kotlinx.coroutines.flow.Flow
import com.chat360.chatbot.network.rest.dto.DealerRoomDto
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class ConversationSummary(val id: String, val title: String, val updatedAt: Long)

class ChatCacheRepository(private val dao: ChatCacheDao) {
    private val json = Json { ignoreUnknownKeys = true }
    fun conversations(botId: String): Flow<List<CachedConversationEntity>> = dao.observeConversations(botId)

    suspend fun createConversation(botId: String, id: String = UUID.randomUUID().toString()): String {
        val now = System.currentTimeMillis()
        dao.upsertConversation(CachedConversationEntity(id, botId, createdAt = now, updatedAt = now))
        return id
    }

    /** Returns true when this server room already has locally cached messages to replay. */
    suspend fun activateForRoom(botId: String, roomId: String, pendingId: String?): Pair<String, Boolean> {
        val existing = dao.findConversation(botId, roomId)
        val id = existing?.id ?: pendingId ?: createConversation(botId)
        dao.setRoom(id, roomId, System.currentTimeMillis())
        return id to dao.messages(id).isNotEmpty()
    }

    suspend fun messages(conversationId: String): List<CachedMessageEntity> = dao.messages(conversationId)

    /** Adds server rooms to the same observable list as local chats without replacing cached messages. */
    fun dealerRoomConversations(botId: String, rooms: List<DealerRoomDto>): List<CachedConversationEntity> {
        val fetchedAt = System.currentTimeMillis()
        return rooms.mapIndexed { index, room ->
            CachedConversationEntity(
                id = "dealer-room:${room.agentUuid}",
                botId = botId,
                roomId = room.roomId,
                title = room.roomName.trim().ifEmpty { "Conversation" },
                createdAt = fetchedAt - index,
                updatedAt = fetchedAt - index,
            )
        }
    }

    suspend fun syncDealerRooms(botId: String, conversations: List<CachedConversationEntity>) {
        dao.replaceDealerRoomConversations(botId, conversations)
    }

    suspend fun replaceRawHistory(conversationId: String, history: List<RawSocketEnvelope>) {
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
        dao.updateTitle(conversationId, title, System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversationId: String) {
        dao.deleteConversation(conversationId)
    }

    suspend fun cacheRaw(conversationId: String, rawEnvelope: String) {
        val now = System.currentTimeMillis()
        dao.insertMessage(CachedMessageEntity(conversationId = conversationId, kind = "RAW", payload = rawEnvelope, createdAt = now))
        dao.touch(conversationId, now)
    }

    suspend fun cacheUserMessage(conversationId: String, text: String, chatMsgId: String?) {
        val now = System.currentTimeMillis()
        dao.insertMessage(CachedMessageEntity(conversationId = conversationId, kind = "USER", payload = text, chatMsgId = chatMsgId, createdAt = now))
        val title = text.trim().replace(Regex("\\s+"), " ").take(80)
        if (title.isNotBlank()) dao.updateTitle(conversationId, title, now) else dao.touch(conversationId, now)
    }
}
