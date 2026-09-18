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
    /** Fetches the rooms list and merges it into the cache. Returns the refreshed conversations
     * list on success, or null on any failure (caller should keep showing whatever it already has). */
    suspend fun refreshRooms(): List<CachedConversationEntity>? {
        // Every page or nothing: the sync below deletes cached rooms that are missing from what it is
        // given, so a fetch that got only the first pages must fail outright rather than hand over a
        // partial list that would wipe the rooms on the pages that never loaded.
        val rooms = runCatching { fetchAllRooms() }
            .onFailure { error -> Log.e("Chat360", "third-party-tasks rooms/list failed: ${error.message}", error) }
            .getOrNull() ?: return null
        val conversations = cache.thirdPartyRoomConversations(botId, rooms)
        cache.syncAgentRooms(botId, conversations)
        // Read back from the DB (already ORDER BY updatedAt DESC) rather than returning the raw
        // server mapping: it carries the merged newest-wins timestamps, so a chat just sent from
        // this device isn't demoted by a response that hasn't caught up with it yet.
        return cache.conversations(botId).first()
    }

    /**
     * Walks `rooms/list` page by page until the server reports no more. Called with no `limit` the
     * server returns only its default page (20 rooms) and says `has_more`; the list is newest-first
     * and soft-deleted rooms count toward the page, so as deleted and abandoned rooms piled up the
     * real, older chats fell off the end of the history list.
     *
     * The next offset is the number of rooms the server actually returned, not [ROOMS_PAGE_SIZE], in
     * case it caps a page lower than asked. Stops early on a page that adds nothing new, so a server
     * that ignores `offset` and repeats one page can't loop forever, and after [ROOMS_MAX_PAGES] as a
     * hard ceiling.
     */
    private suspend fun fetchAllRooms(): List<RoomDto> {
        val rooms = mutableListOf<RoomDto>()
        val seen = mutableSetOf<String>()
        var offset = 0
        repeat(ROOMS_MAX_PAGES) {
            val page = withAuthRetry { token ->
                apiService.fetchRoomsList(clientId, token, agentId = endUserId, limit = ROOMS_PAGE_SIZE, offset = offset)
            }
            val fresh = page.rooms.filter { seen.add(it.roomId) }
            rooms += fresh
            if (!page.hasMore || fresh.isEmpty()) return rooms
            offset += page.rooms.size
        }
        Log.w("Chat360", "third-party-tasks rooms/list hit the $ROOMS_MAX_PAGES-page ceiling with more still available")
        return rooms
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
        const val ROOMS_PAGE_SIZE = 100
        const val ROOMS_MAX_PAGES = 50
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
