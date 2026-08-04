package com.chat360.chatbot.cache

import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class ConversationSummary(val id: String, val title: String, val updatedAt: Long)

class ChatCacheRepository(private val dao: ChatCacheDao) {
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
