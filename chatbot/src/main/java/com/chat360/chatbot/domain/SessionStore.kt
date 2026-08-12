package com.chat360.chatbot.domain

import android.content.Context

/** A session worth resuming on the next app launch - the same trio the web widget persists to
 * localStorage (see new-client-widget's initSession.ts) so returning to the same bot resumes the
 * same room/conversation instead of always allocating a new one. */
data class PersistedSession(val roomId: String, val sessionToken: String?, val ownerId: String)

interface SessionStore {
    fun load(botId: String): PersistedSession?
    fun save(botId: String, session: PersistedSession)
}

/** One row per botId - a device can run multiple bots (see the demo app's several activities),
 * each with its own independently resumable session. */
class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences("chat360_session_store", Context.MODE_PRIVATE)

    override fun load(botId: String): PersistedSession? {
        val roomId = prefs.getString(key(botId, "roomId"), null) ?: return null
        val ownerId = prefs.getString(key(botId, "ownerId"), null) ?: return null
        val sessionToken = prefs.getString(key(botId, "sessionToken"), null)
        return PersistedSession(roomId, sessionToken, ownerId)
    }

    override fun save(botId: String, session: PersistedSession) {
        prefs.edit()
            .putString(key(botId, "roomId"), session.roomId)
            .putString(key(botId, "sessionToken"), session.sessionToken)
            .putString(key(botId, "ownerId"), session.ownerId)
            .apply()
    }

    private fun key(botId: String, field: String) = "$botId.$field"
}
