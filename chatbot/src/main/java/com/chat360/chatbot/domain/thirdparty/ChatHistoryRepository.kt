package com.chat360.chatbot.domain.thirdparty

import android.util.Log
import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyHttpException
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService

/**
 * Backs the rooms/conversations list with the third-party-tasks API family. Every method is
 * best-effort: on failure the existing [ChatCacheRepository] contents are left untouched and the
 * error is only logged - these calls must never surface an error to the user or affect the live
 * chat/bot flow.
 *
 * [clientId], [botId], and [endUserId] must all be non-blank - there is intentionally no
 * partially-configured mode: history is either fully identified or fully off. [botId] itself is
 * only used to key the local cache, never sent to `rooms/list` - see [ThirdPartyTasksApiService].
 */
class ChatHistoryRepository(
    private val apiService: ThirdPartyTasksApiService,
    private val tokenManager: ThirdPartyTokenManager,
    private val cache: ChatCacheRepository,
    private val clientId: String,
    private val botId: String,
    /** The current end user's identity - scopes `rooms/list` to their rooms. Sent on the wire as
     * the `agent_id` query parameter (the backend's own naming - see [ThirdPartyTasksApiService]).
     * Required - see class doc. */
    private val endUserId: String,
) {
    /** Fetches the rooms list and merges it into the cache. Returns the refreshed conversations
     * list on success, or null on any failure (caller should keep showing whatever it already has). */
    suspend fun refreshRooms(): List<CachedConversationEntity>? {
        val response = runCatching {
            withAuthRetry { token -> apiService.fetchRoomsList(clientId, token, agentId = endUserId) }
        }.onFailure { error -> Log.e("Chat360", "third-party-tasks rooms/list failed: ${error.message}", error) }
            .getOrNull() ?: return null
        val conversations = cache.thirdPartyRoomConversations(botId, response.rooms)
        cache.syncAgentRooms(botId, conversations)
        return conversations
    }

    /** Best-effort remote rename - failure never blocks the local rename the caller already applied. */
    suspend fun renameRoom(roomId: String, roomName: String) {
        runCatching { withAuthRetry { token -> apiService.updateRoom(roomId, clientId, roomName, token) } }
            .onFailure { error -> Log.e("Chat360", "third-party-tasks room/update failed: ${error.message}", error) }
    }

    /** Best-effort remote "mark inactive" - the counterpart of deleting a conversation from history. */
    suspend fun markRoomInactive(roomId: String) {
        runCatching { withAuthRetry { token -> apiService.updateRoomStatus(roomId, clientId, token) } }
            .onFailure { error -> Log.e("Chat360", "third-party-tasks room/update/status failed: ${error.message}", error) }
    }

    private suspend fun <T> withAuthRetry(block: suspend (token: String) -> T): T {
        val token = tokenManager.validToken()
        return try {
            block(token)
        } catch (e: ThirdPartyHttpException) {
            if (e.httpCode != 401) throw e
            tokenManager.invalidate()
            block(tokenManager.validToken())
        }
    }
}
