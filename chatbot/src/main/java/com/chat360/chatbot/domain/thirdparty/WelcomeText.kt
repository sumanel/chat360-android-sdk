package com.chat360.chatbot.domain.thirdparty

import android.content.Context
import android.util.Log
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService

/**
 * The welcome screen copy configured for a client on the server (`third-party-tasks/welcome-text`).
 * Either field can be null: a blank one means "nothing configured", not "show an empty heading".
 */
data class WelcomeText(val heading: String?, val text: String?) {
    val isEmpty: Boolean get() = heading == null && text == null
}

/** Where the last good [WelcomeText] is kept between launches, so the welcome screen shows it instantly. */
interface WelcomeTextStore {
    fun load(clientId: String): WelcomeText?
    fun save(clientId: String, welcomeText: WelcomeText?)
}

class SharedPreferencesWelcomeTextStore(context: Context) : WelcomeTextStore {
    private val prefs = context.applicationContext.getSharedPreferences("chat360_welcome_text", Context.MODE_PRIVATE)

    override fun load(clientId: String): WelcomeText? {
        val heading = prefs.getString("$clientId.heading", null)
        val text = prefs.getString("$clientId.text", null)
        return WelcomeText(heading, text).takeUnless { it.isEmpty }
    }

    override fun save(clientId: String, welcomeText: WelcomeText?) {
        val editor = prefs.edit()
        if (welcomeText == null) {
            editor.remove("$clientId.heading").remove("$clientId.text")
        } else {
            editor.putString("$clientId.heading", welcomeText.heading).putString("$clientId.text", welcomeText.text)
        }
        editor.apply()
    }
}

/**
 * Best-effort source of the server-configured welcome copy. Never throws to the caller and never
 * affects the chat itself: when nothing usable comes back, the welcome screen simply keeps the text the
 * host app supplied (or the theme's default).
 */
class WelcomeTextRepository(
    private val apiService: ThirdPartyTasksApiService,
    private val clientId: String,
    private val store: WelcomeTextStore,
) {
    /** The last good response, for an instant first paint. */
    fun cached(): WelcomeText? = store.load(clientId)

    /**
     * Asks the server. Returns the fresh copy (null when the server has none configured) and updates the
     * cache to match; returns [Result.failure] for anything else - offline, 404 while the endpoint isn't
     * deployed, a malformed reply - in which case the cache is deliberately left alone, so a flaky
     * connection never wipes a welcome that was working.
     */
    suspend fun refresh(): Result<WelcomeText?> = runCatching { apiService.fetchWelcomeText(clientId) }
        .onSuccess { store.save(clientId, it) }
        .onFailure { Log.w("Chat360", "third-party-tasks welcome-text unavailable: ${it.message}") }
}
