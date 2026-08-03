package com.chat360.chatbot.android

import android.content.Intent
import android.os.Bundle
import com.chat360.chatbot.common.models.ConfigService
import com.chat360.chatbot.config.Chat360Config
import com.chat360.chatbot.ui.theme.Chat360Branding
import com.chat360.chatbot.ui.theme.Chat360Colors
import com.chat360.chatbot.ui.theme.Chat360ThemePreset
import com.chat360.chatbot.ui.theme.Chat360Typography

private const val DEFAULT_BASE_URL = "https://app.chat360.io"

const val EXTRA_BOT_ID = "extra_bot_id"
const val EXTRA_BASE_URL = "extra_base_url"
const val EXTRA_THEME_PRESET = "extra_theme_preset"
const val EXTRA_HISTORY_ENABLED = "extra_history_enabled"

data class ResolvedChatConfig(
    val botId: String,
    val baseUrl: String,
    val themePreset: Chat360ThemePreset,
    val customLightColors: Chat360Colors?,
    val customDarkColors: Chat360Colors?,
    val customTypography: Chat360Typography?,
    val customBranding: Chat360Branding?,
    val historyEnabled: Boolean,
    val chat360Config: Chat360Config,
)

/**
 * Two ways a screen ends up here: through the public API (`Chat360.startBot`/`getChatBotView`,
 * which populate [ConfigService] via `setConfigData` the same way the old WebView screens read
 * it), or launched directly with Intent extras (our own demo buttons / test harness, which never
 * touch CoreConfigs). The former takes priority whenever a real botId has actually been set.
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
            chat360Config = config.chat360Config ?: Chat360Config(),
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
        chat360Config = Chat360Config(),
    )
}

fun resolveChatConfig(intent: Intent?): ResolvedChatConfig = resolveChatConfig(intent?.extras)
