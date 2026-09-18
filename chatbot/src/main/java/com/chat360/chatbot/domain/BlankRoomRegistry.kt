package com.chat360.chatbot.domain

import java.util.concurrent.ConcurrentHashMap

/**
 * The room, per bot, that the server created but no user message has been sent in yet.
 *
 * Process-lifetime only (deliberately never persisted): reopening the chat screen while the host
 * app is still running goes back to this room instead of the last-connected one (or a new empty
 * one), while a genuine app restart starts empty and so gets a brand-new room, as intended.
 */
internal object BlankRoomRegistry {
    private val rooms = ConcurrentHashMap<String, String>()

    fun roomId(botId: String): String? = rooms[botId]

    fun set(botId: String, roomId: String) {
        rooms[botId] = roomId
    }

    /** Only clears when [roomId] is still the registered one - a different room turning non-blank
     * says nothing about the blank one. A null [roomId] clears unconditionally. */
    fun clear(botId: String, roomId: String?) {
        if (roomId == null) rooms.remove(botId) else rooms.remove(botId, roomId)
    }
}
