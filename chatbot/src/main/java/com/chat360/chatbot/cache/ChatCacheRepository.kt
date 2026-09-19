package com.chat360.chatbot.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomDto
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import com.chat360.chatbot.model.wire.serverTimestampMs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class ConversationSummary(val id: String, val title: String, val updatedAt: Long)

class ChatCacheRepository(private val dao: ChatCacheDao) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val ENABLED = true
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
            // Nobody typed in it: a server session_count of 0 is an empty room. A room the server gives
            // neither a name nor a count for is treated the same (an abandoned one), as before. An
            // unnamed room that does have sessions is a real chat and is listed as "Conversation".
            .filterNot { it.sessionCount == 0 || (it.sessionCount == null && it.roomName.isBlank()) }
            .mapIndexed { index, room ->
                // The server's own timestamps drive the sidebar order - falling back to the
                // response position only when a room carries none, so the order can never depend
                // on whichever way the API happens to return its rooms.
                val positional = fetchedAt - index
                val created = parseServerTimestampMs(room.createdAt)
                val updated = parseServerTimestampMs(room.updatedAt) ?: created
                CachedConversationEntity(
                    id = "agent-room:${room.roomId}",
                    botId = botId,
                    roomId = room.roomId,
                    title = room.roomName.trim().ifEmpty { UNNAMED_ROOM_TITLE },
                    createdAt = created ?: positional,
                    updatedAt = updated ?: positional,
                )
            }
            .sortedByDescending { it.updatedAt }
    }

    /** Applies what the server says about rooms this device already has a local conversation for:
     * a room the server marks inactive (deleted elsewhere) is removed here too, and a name the
     * server holds replaces a differing local title. Without this the local row - which owns the
     * room and so shields it from the `agent-room:` sync - never changed after it was created.
     * Call only with a complete rooms list. */
    suspend fun syncLocalConversations(botId: String, rooms: List<RoomDto>) {
        if (!ENABLED) return
        rooms.forEach { room ->
            val local = dao.findConversation(botId, room.roomId)?.takeUnless { it.id.startsWith("agent-room:") } ?: return@forEach
            if (room.status.equals("inactive", ignoreCase = true)) {
                dao.deleteMessages(local.id)
                dao.deleteConversation(local.id)
                return@forEach
            }
            val name = room.roomName.trim()
            if (name.isNotEmpty() && name != local.title) dao.updateTitle(local.id, name, local.updatedAt)
        }
    }

    /** Replaces a conversation's server-sourced messages wholesale - the caller re-reads
     * [messages] afterward for the inserted rows' ids (and any [CachedMessageEntity.liked]
     * carried over below) to attach to what it renders. Any locally-sent ("USER") message the
     * backend hasn't indexed into its own history endpoint yet is carried forward rather than
     * deleted - see [notYetIndexedUserMessages]'s doc; the caller re-reads [messages] afterward,
     * so it renders these the same way it renders everything else here. */
    suspend fun replaceRawHistory(conversationId: String, history: List<RawSocketEnvelope>) {
        if (!ENABLED) return
        val fetchedAt = System.currentTimeMillis()
        val existing = dao.messages(conversationId)
        // A bot envelope carries no stable id at this decode layer - matching the freshly
        // re-serialized payload against what's already cached is the only way to tell "this is
        // still the same message" and carry its thumbs up/down forward. A message whose content
        // genuinely changed server-side just loses its rating, same as any content decoded fresh
        // from history for the first time.
        val likedByPayload = existing
            .filter { it.kind == "RAW" && it.liked != null }
            .associate { it.payload to it.liked }
        val historyChatMsgIds = history.mapNotNullTo(mutableSetOf()) { it.chat_msg_id }
        // A message this device just sent (or snapped back to send while browsing elsewhere -
        // see ChatViewModel.appendMessage) can easily lose the race against this same history
        // fetch: the websocket send and the backend indexing it into its *own* history endpoint
        // aren't the same write, so a refresh that lands in between would otherwise silently wipe
        // it the instant the wholesale replace below runs - exactly what made a just-sent message
        // vanish on a fast room switch. Carrying it forward here instead lets it survive until a
        // later refresh's history page genuinely includes it, at which point its chatMsgId starts
        // matching one in historyChatMsgIds and it naturally drops out of this set on its own -
        // no separate cleanup needed.
        val notYetIndexedUserMessages = existing.filter {
            it.kind == "USER" && (it.chatMsgId == null || it.chatMsgId !in historyChatMsgIds)
        }
        val historyRows = history.mapIndexed { index, envelope ->
            val payload = json.encodeToString(envelope)
            CachedMessageEntity(
                conversationId = conversationId,
                kind = "RAW",
                payload = payload,
                createdAt = fetchedAt + index,
                liked = likedByPayload[payload],
            )
        }
        // Appended after the fetched history (not merged by timestamp) so a not-yet-indexed send
        // always renders as the most recent message - the only case this preserves is exactly
        // that: something sent after everything the server just returned. id reset to 0 since
        // these are reinserted as new rows once dao.replaceMessages deletes the old ones.
        dao.replaceMessages(conversationId, mergeByTime(historyRows, history, notYetIndexedUserMessages.map { it.copy(id = 0) }))
    }

    /** Places each carried-forward local send at its real position among [historyRows] (by the
     * server timestamp each history frame carries) instead of always after them: a send the
     * backend never matched by id, from earlier in the chat, otherwise rendered below newer
     * replies - "10:06" above "10:01". A send newer than everything in history still lands last.
     * A history frame with no timestamp inherits the previous one's, so relative order is kept. */
    private fun mergeByTime(
        historyRows: List<CachedMessageEntity>,
        history: List<RawSocketEnvelope>,
        carried: List<CachedMessageEntity>,
    ): List<CachedMessageEntity> {
        if (carried.isEmpty()) return historyRows
        var last = Long.MIN_VALUE
        val times = history.map { envelope ->
            val t = envelope.serverTimestampMs()
            if (t != null) last = maxOf(last, t)
            last
        }
        val result = historyRows.toMutableList()
        val resultTimes = times.toMutableList()
        carried.sortedBy { it.createdAt }.forEach { row ->
            val at = resultTimes.indexOfFirst { it > row.createdAt }.let { if (it == -1) result.size else it }
            result.add(at, row)
            resultTimes.add(at, row.createdAt)
        }
        return result
    }

    suspend fun renameConversation(conversationId: String, title: String) {
        if (!ENABLED) return
        dao.updateTitle(conversationId, title, System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversationId: String) {
        if (!ENABLED) return
        dao.deleteConversation(conversationId)
    }

    /** Returns the inserted row's id (null when disabled) - callers use it to later attach a
     * thumbs up/down to this exact bot message via [setMessageFeedback]. */
    suspend fun cacheRaw(conversationId: String, rawEnvelope: String): Long? {
        if (!ENABLED) return null
        val now = System.currentTimeMillis()
        val rowId = dao.insertMessage(CachedMessageEntity(conversationId = conversationId, kind = "RAW", payload = rawEnvelope, createdAt = now))
        dao.touch(conversationId, now)
        return rowId
    }

    /** Persists (or clears, when [liked] is null) a thumbs up/down against the cache row backing
     * one bot message, so it survives leaving and reopening the conversation. */
    suspend fun setMessageFeedback(messageRowId: Long, liked: Boolean?) {
        if (!ENABLED) return
        dao.setLiked(messageRowId, liked)
    }

    suspend fun cacheUserMessage(conversationId: String, text: String, chatMsgId: String?) {
        if (!ENABLED) return
        val now = System.currentTimeMillis()
        dao.insertMessage(CachedMessageEntity(conversationId = conversationId, kind = "USER", payload = text, chatMsgId = chatMsgId, createdAt = now))
        val title = text.trim().replace(Regex("\\s+"), " ").take(80)
        if (title.isNotBlank()) dao.touchAndSetTitleIfUnset(conversationId, title, now) else dao.touch(conversationId, now)
    }
}

/** Parses a `rooms/list` timestamp - epoch seconds/millis or ISO-8601 (with or without
 * fractional seconds / zone) - to epoch millis; null when absent or unrecognised. */
internal fun parseServerTimestampMs(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    value.toDoubleOrNull()?.let { n ->
        // Below ~1e11 it can only be seconds (that's year 5138 in millis).
        return if (n < 1e11) (n * 1000).toLong() else n.toLong()
    }
    val normalized = value.replace(Regex("(\\.\\d{3})\\d+"), "$1")
        .replace(Regex("Z$"), "+0000")
        .replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss.SSSZ", "yyyy-MM-dd HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss",
    )
    for (pattern in patterns) {
        val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
        // Zone-less values are treated as UTC, the usual server default.
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val parsed = runCatching { format.parse(normalized) }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}
