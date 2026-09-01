package com.chat360.chatbot.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow

/** In-memory [ChatCacheDao] for tests - avoids needing a real Room database/Robolectric. Only
 * implements what [ChatCacheRepository]'s third-party-tasks paths actually touch; the
 * `@Transaction`-annotated default methods on [ChatCacheDao] run unmodified against these. */
class FakeChatCacheDao : ChatCacheDao {
    private val conversations = linkedMapOf<String, CachedConversationEntity>()
    private val flows = mutableMapOf<String, MutableStateFlow<List<CachedConversationEntity>>>()

    private fun flowFor(botId: String) = flows.getOrPut(botId) { MutableStateFlow(emptyList()) }

    private fun publish(botId: String) {
        flowFor(botId).value = conversations.values.filter { it.botId == botId }.sortedByDescending { it.updatedAt }
    }

    override suspend fun findConversation(botId: String, roomId: String): CachedConversationEntity? =
        conversations.values.firstOrNull { it.botId == botId && it.roomId == roomId }

    override fun observeConversations(botId: String): Flow<List<CachedConversationEntity>> {
        publish(botId)
        return flowFor(botId).asStateFlow()
    }

    override suspend fun messages(conversationId: String): List<CachedMessageEntity> = emptyList()

    override suspend fun agentRoomConversationIds(botId: String): List<String> =
        conversations.values.filter { it.botId == botId && it.id.startsWith("agent-room:") }.map { it.id }

    override suspend fun upsertConversation(conversation: CachedConversationEntity) {
        conversations[conversation.id] = conversation
        publish(conversation.botId)
    }

    override suspend fun insertConversationIfMissing(conversation: CachedConversationEntity) {
        if (!conversations.containsKey(conversation.id)) {
            conversations[conversation.id] = conversation
            publish(conversation.botId)
        }
    }

    override suspend fun updateRemoteConversation(conversationId: String, roomId: String, title: String, updatedAt: Long) {
        val existing = conversations[conversationId] ?: return
        conversations[conversationId] = existing.copy(roomId = roomId, title = title, updatedAt = updatedAt)
        publish(existing.botId)
    }

    override suspend fun insertMessage(message: CachedMessageEntity): Long = 0L

    override suspend fun insertMessages(messages: List<CachedMessageEntity>) = Unit

    override suspend fun setLiked(messageRowId: Long, liked: Boolean?) = Unit

    override suspend fun deleteMessages(conversationId: String) = Unit

    override suspend fun setRoom(conversationId: String, roomId: String, updatedAt: Long) {
        val existing = conversations[conversationId] ?: return
        conversations[conversationId] = existing.copy(roomId = roomId, updatedAt = updatedAt)
        publish(existing.botId)
    }

    override suspend fun updateTitle(conversationId: String, title: String, updatedAt: Long) {
        val existing = conversations[conversationId] ?: return
        conversations[conversationId] = existing.copy(title = title, updatedAt = updatedAt)
        publish(existing.botId)
    }

    override suspend fun touchAndSetTitleIfUnset(conversationId: String, title: String, updatedAt: Long) {
        val existing = conversations[conversationId] ?: return
        val newTitle = if (existing.title == "New conversation") title else existing.title
        conversations[conversationId] = existing.copy(title = newTitle, updatedAt = updatedAt)
        publish(existing.botId)
    }

    override suspend fun deleteConversation(conversationId: String) {
        val botId = conversations.remove(conversationId)?.botId ?: return
        publish(botId)
    }

    override suspend fun touch(conversationId: String, updatedAt: Long) {
        val existing = conversations[conversationId] ?: return
        conversations[conversationId] = existing.copy(updatedAt = updatedAt)
        publish(existing.botId)
    }

    fun snapshot(botId: String): List<CachedConversationEntity> =
        conversations.values.filter { it.botId == botId }.sortedByDescending { it.updatedAt }
}
