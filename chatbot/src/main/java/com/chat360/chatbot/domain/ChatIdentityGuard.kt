package com.chat360.chatbot.domain

import android.content.Context
import com.chat360.chatbot.cache.ChatCacheDatabase
import com.chat360.chatbot.domain.thirdparty.SharedPreferencesRoomRoleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * The local cache, the saved session and the room roles are all kept per bot, not per user - so when a
 * different user (`endUserId`) opens the same bot on this device they would otherwise see the previous
 * user's conversations in the history menu and resume their room. This remembers which user each bot's
 * local data belongs to and wipes it when that changes.
 */
class ChatIdentityGuard(
    private val lastUser: LastUserStore,
    private val wipe: (botId: String) -> Unit,
) {
    interface LastUserStore {
        fun get(botId: String): String?
        fun set(botId: String, endUserId: String?)
    }

    /** Call before the chat screen reads any local data. Returns true if it cleared something. */
    fun apply(botId: String, endUserId: String?): Boolean {
        if (botId.isBlank()) return false
        val current = endUserId?.trim().orEmpty()
        val stored = lastUser.get(botId)
        if (stored == current) return false
        lastUser.set(botId, current)
        // Never recorded and no user now: there is nothing to tell apart, so keep what is there.
        if (stored == null && current.isEmpty()) return false
        wipe(botId)
        return true
    }

    /** For a host's logout: forgets the local conversations, session and roles of [botId] outright. */
    fun reset(botId: String) {
        lastUser.set(botId, null)
        wipe(botId)
    }

    companion object {
        fun forContext(context: Context): ChatIdentityGuard {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("chat360_identity", Context.MODE_PRIVATE)
            return ChatIdentityGuard(
                lastUser = object : LastUserStore {
                    override fun get(botId: String): String? = prefs.getString(botId, null)
                    override fun set(botId: String, endUserId: String?) {
                        prefs.edit().apply { if (endUserId == null) remove(botId) else putString(botId, endUserId) }.apply()
                    }
                },
                wipe = { botId ->
                    // Blocking on purpose: it has to be finished before the chat screen starts reading the cache.
                    runBlocking(Dispatchers.IO) { ChatCacheDatabase.get(app).dao().deleteBotConversations(botId) }
                    SharedPreferencesSessionStore(app).clear(botId)
                    SharedPreferencesRoomRoleStore(app).clear(botId)
                },
            )
        }
    }
}
