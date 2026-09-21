package com.chat360.chatbot.domain.thirdparty

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Remembers which agent role each room was created for (`rooms/list`'s `agent_role`), so the history list
 * can badge a room before the next fetch, including while offline.
 */
interface RoomRoleStore {
    /** Roles last returned by `rooms/list`. */
    fun load(botId: String): Map<String, String>
    fun save(botId: String, roles: Map<String, String>)

    /** Roles this device sent when it created a room - shown until `rooms/list` returns one for that room. */
    fun loadLocal(botId: String): Map<String, String>
    fun saveLocal(botId: String, roles: Map<String, String>)
}

class SharedPreferencesRoomRoleStore(context: Context) : RoomRoleStore {
    private val prefs = context.applicationContext.getSharedPreferences("chat360_room_roles", Context.MODE_PRIVATE)
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    override fun load(botId: String) = read(botId)
    override fun save(botId: String, roles: Map<String, String>) = write(botId, roles)
    override fun loadLocal(botId: String) = read("$botId#local")
    override fun saveLocal(botId: String, roles: Map<String, String>) = write("$botId#local", roles)

    fun clear(botId: String) {
        prefs.edit().remove(botId).remove("$botId#local").apply()
    }

    private fun read(key: String): Map<String, String> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return runCatching { Json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    private fun write(key: String, roles: Map<String, String>) {
        prefs.edit().putString(key, Json.encodeToString(serializer, roles)).apply()
    }
}
