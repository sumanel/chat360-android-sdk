package com.chat360.chatbot.domain.thirdparty

import android.util.Log
import kotlinx.coroutines.flow.first
import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomDto
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
    // How many rooms the server has handed over so far (the next page's offset), and whether it says
    // there are more. Counted as returned by the server, not as shown: empty rooms are dropped from the
    // list, and a server may cap a page below the size asked for.
    @Volatile private var loadedCount = 0
    @Volatile var hasMoreRooms = false
        private set

    /** Fetches the newest rooms and merges them into the cache. Returns the refreshed conversations
     * list on success, or null on any failure (caller should keep showing whatever it already has).
     *
     * One request, from the top of the list: a page, or as many rooms as were already loaded so that a
     * refresh doesn't collapse a list the user had scrolled through. Older rooms come in through
     * [loadMoreRooms] instead of being fetched up front. */
    suspend fun refreshRooms(): List<CachedConversationEntity>? {
        val page = runCatching {
            val limit = maxOf(ROOMS_PAGE_SIZE, loadedCount)
            withAuthRetry { token -> apiService.fetchRoomsList(token, agentId = endUserId, limit = limit, offset = 0) }
        }
            .onFailure { error -> Log.e("Chat360", "third-party-tasks rooms/list failed: ${error.message}", error) }
            .getOrNull() ?: return null
        loadedCount = page.rooms.size
        hasMoreRooms = page.hasMore && page.rooms.isNotEmpty()
        cache.syncLocalConversations(botId, page.rooms)
        // Replaces the synced rooms: any cached one missing from the top of the list is dropped, and
        // reappears once its page is loaded again.
        cache.syncAgentRooms(botId, cache.thirdPartyRoomConversations(botId, page.rooms))
        // Read back from the DB (already ORDER BY updatedAt DESC) rather than returning the raw
        // server mapping: it carries the merged newest-wins timestamps, so a chat just sent from
        // this device isn't demoted by a response that hasn't caught up with it yet.
        return cache.conversations(botId).first()
    }

    /** Fetches the next page of older rooms and adds them to the cache. Returns false on failure (the
     * list is left as it was and the caller can offer a retry); true otherwise, after which
     * [hasMoreRooms] says whether another page is available. */
    suspend fun loadMoreRooms(): Boolean {
        if (!hasMoreRooms) return true
        val page = runCatching {
            withAuthRetry { token -> apiService.fetchRoomsList(token, agentId = endUserId, limit = ROOMS_PAGE_SIZE, offset = loadedCount) }
        }
            .onFailure { error -> Log.e("Chat360", "third-party-tasks rooms/list (more) failed: ${error.message}", error) }
            .getOrNull() ?: return false
        loadedCount += page.rooms.size
        // A page with nothing in it ends the list even if the server still claims more, so a server
        // that misreports has_more can't keep the button alive forever.
        hasMoreRooms = page.hasMore && page.rooms.isNotEmpty()
        cache.syncLocalConversations(botId, page.rooms)
        cache.mergeAgentRooms(botId, cache.thirdPartyRoomConversations(botId, page.rooms))
        return true
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

    internal companion object {
        const val ROOMS_PAGE_SIZE = 50
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
