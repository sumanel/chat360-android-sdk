package com.chat360.chatbot.domain

import android.content.Context

/** A session worth resuming - the same trio the web widget persists to localStorage (see
 * new-client-widget's initSession.ts). The backend requires [sessionToken] to resume [roomId] at
 * all (confirmed against the live API: room_id alone is silently ignored, allocating a fresh
 * room instead) - so every room this device has ever connected to is persisted individually
 * here, not just the most recent one, or switching back to an older conversation and sending
 * would have no token to resume it with and would silently land in whatever room is currently
 * connected instead (see ChatRepository.switchToRoom / ChatViewModel.sendMessage). */
data class PersistedSession(val roomId: String, val sessionToken: String?, val ownerId: String)

interface SessionStore {
    /** The most recently connected room - what reopening the chat screen resumes into, as long
     * as the host app's process is still the one that connected it (see
     * ChatRepository.botsConnectedThisProcess; a genuine cold app launch never consults this and
     * always starts a fresh conversation instead). */
    fun load(botId: String): PersistedSession?
    /** Any room previously connected on this device, regardless of how long ago - what
     * switching back to an older browsed conversation resumes into. */
    fun loadForRoom(botId: String, roomId: String): PersistedSession?
    /** Updates both [load]'s "most recent" slot and this room's own [loadForRoom] entry. */
    fun save(botId: String, session: PersistedSession)
}

/** One "most recent" row per botId plus one row per (botId, roomId) ever connected - a device
 * can run multiple bots (see the demo app's several activities), each independently resumable,
 * and each bot can have many rooms a user has browsed back into. */
class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences("chat360_session_store", Context.MODE_PRIVATE)

    override fun load(botId: String): PersistedSession? = read(lastKeyPrefix(botId))

    override fun loadForRoom(botId: String, roomId: String): PersistedSession? = read(roomKeyPrefix(botId, roomId))

    override fun save(botId: String, session: PersistedSession) {
        val editor = prefs.edit()
        write(editor, lastKeyPrefix(botId), session)
        write(editor, roomKeyPrefix(botId, session.roomId), session)
        editor.apply()
    }

    private fun read(prefix: String): PersistedSession? {
        val roomId = prefs.getString("$prefix.roomId", null) ?: return null
        val ownerId = prefs.getString("$prefix.ownerId", null) ?: return null
        val sessionToken = prefs.getString("$prefix.sessionToken", null)
        return PersistedSession(roomId, sessionToken, ownerId)
    }

    private fun write(editor: android.content.SharedPreferences.Editor, prefix: String, session: PersistedSession) {
        editor.putString("$prefix.roomId", session.roomId)
        editor.putString("$prefix.sessionToken", session.sessionToken)
        editor.putString("$prefix.ownerId", session.ownerId)
    }

    private fun lastKeyPrefix(botId: String) = "$botId.last"
    private fun roomKeyPrefix(botId: String, roomId: String) = "$botId.room.$roomId"
}
