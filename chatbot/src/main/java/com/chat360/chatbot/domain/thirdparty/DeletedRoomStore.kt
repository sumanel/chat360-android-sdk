package com.chat360.chatbot.domain.thirdparty

import android.content.Context

/**
 * Remembers which rooms the user deleted from the history list on this device.
 *
 * Deleting only asks the server to mark the room inactive, and `rooms/list` keeps returning inactive rooms - so
 * without this record a deleted room came straight back the next time the list was refreshed (opening the
 * sidebar). Anything recorded here is filtered out of every `rooms/list` page.
 */
interface DeletedRoomStore {
    fun load(botId: String): Set<String>
    fun add(botId: String, roomId: String)
}

class SharedPreferencesDeletedRoomStore(context: Context) : DeletedRoomStore {
    private val prefs = context.applicationContext.getSharedPreferences("chat360_deleted_rooms", Context.MODE_PRIVATE)

    override fun load(botId: String): Set<String> = prefs.getStringSet(botId, null)?.toSet().orEmpty()

    override fun add(botId: String, roomId: String) {
        // getStringSet's result must not be modified in place - copy, then write back.
        val updated = load(botId) + roomId
        prefs.edit().putStringSet(botId, updated).apply()
    }

    fun clear(botId: String) {
        prefs.edit().remove(botId).apply()
    }
}
