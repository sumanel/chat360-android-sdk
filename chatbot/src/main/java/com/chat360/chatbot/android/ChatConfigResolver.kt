package com.chat360.chatbot.android

import android.content.Intent
import android.os.Bundle
import com.chat360.chatbot.common.models.ConfigService
import com.chat360.chatbot.config.Chat360UIConfig
import com.chat360.chatbot.ui.theme.Chat360Branding
import com.chat360.chatbot.ui.theme.Chat360Colors
import com.chat360.chatbot.ui.theme.Chat360ThemePreset
import com.chat360.chatbot.ui.theme.Chat360Typography
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private const val DEFAULT_BASE_URL = "https://app.chat360.io"

const val EXTRA_BOT_ID = "extra_bot_id"
const val EXTRA_BASE_URL = "extra_base_url"
const val EXTRA_THEME_PRESET = "extra_theme_preset"
const val EXTRA_HISTORY_ENABLED = "extra_history_enabled"
const val EXTRA_CLIENT_ID = "extra_client_id"
const val EXTRA_API_KEY = "extra_api_key"
const val EXTRA_END_USER_ID = "extra_end_user_id"
const val EXTRA_META = "extra_meta"

data class ResolvedChatConfig(
    val botId: String,
    val baseUrl: String,
    val themePreset: Chat360ThemePreset,
    val customLightColors: Chat360Colors?,
    val customDarkColors: Chat360Colors?,
    val customTypography: Chat360Typography?,
    val customBranding: Chat360Branding?,
    val historyEnabled: Boolean,
    val clientId: String?,
    val apiKey: String?,
    val endUserId: String?,
    val Chat360UIConfig: Chat360UIConfig,
    /** Host-supplied key/value pairs seeded into the session's own `@`-variables at creation
     * time (e.g. `{"dealer_id": "..."}` -> `@dealer_id` in the flow) - see
     * [com.chat360.chatbot.network.rest.Chat360ApiService.getSession]. */
    val meta: Map<String, String>?,
)

/**
 * Two ways a screen ends up here: through the public API (`Chat360.startBot`/`getChatBotView`,
 * which populate [ConfigService] via `setConfigData`), or launched directly with Intent extras
 * (which never touch [ConfigService]). The former takes priority whenever a real botId has
 * actually been set.
 */
fun resolveChatConfig(extras: Bundle?): ResolvedChatConfig {
    val config = ConfigService.getInstance()?.getConfig()
    val configuredBotId = config?.botId?.takeIf { it.isNotBlank() }
    if (config != null && configuredBotId != null) {
        return ResolvedChatConfig(
            botId = configuredBotId,
            baseUrl = ConfigService.getInstance()?.getBaseUrl()?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
            themePreset = config.themePreset,
            customLightColors = config.customLightColors,
            customDarkColors = config.customDarkColors,
            customTypography = config.customTypography,
            customBranding = config.customBranding,
            historyEnabled = config.historyEnabled,
            clientId = config.clientId?.trim()?.takeIf { it.isNotEmpty() },
            apiKey = config.apiKey?.trim()?.takeIf { it.isNotEmpty() },
            endUserId = config.endUserId?.trim()?.takeIf { it.isNotEmpty() },
            Chat360UIConfig = config.Chat360UIConfig ?: Chat360UIConfig(),
            meta = config.meta,
        )
    }
    return ResolvedChatConfig(
        botId = extras?.getString(EXTRA_BOT_ID).orEmpty(),
        baseUrl = extras?.getString(EXTRA_BASE_URL) ?: DEFAULT_BASE_URL,
        themePreset = Chat360ThemePreset.entries.find { it.name == extras?.getString(EXTRA_THEME_PRESET) }
            ?: Chat360ThemePreset.DEFAULT,
        customLightColors = null,
        customDarkColors = null,
        customTypography = null,
        customBranding = null,
        historyEnabled = extras?.getBoolean(EXTRA_HISTORY_ENABLED, true) ?: true,
        clientId = extras?.getString(EXTRA_CLIENT_ID)?.trim()?.takeIf { it.isNotEmpty() },
        apiKey = extras?.getString(EXTRA_API_KEY)?.trim()?.takeIf { it.isNotEmpty() },
        endUserId = extras?.getString(EXTRA_END_USER_ID)?.trim()?.takeIf { it.isNotEmpty() },
        Chat360UIConfig = Chat360UIConfig(),
        meta = extras?.getString(EXTRA_META)?.let {
            runCatching { Json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull()
        },
    )
}

fun resolveChatConfig(intent: Intent?): ResolvedChatConfig = resolveChatConfig(intent?.extras)
