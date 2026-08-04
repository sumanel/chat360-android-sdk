package com.chat360.chatbot.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.chat360.chatbot.ui.ChatViewModel
import com.chat360.chatbot.ui.screens.ChatScreen
import com.chat360.chatbot.ui.theme.Chat360Theme
import com.chat360.chatbot.ui.theme.Chat360ThemePreset
import com.chat360.chatbot.ui.theme.LocalChat360Colors

/**
 * Native chat host, reachable two ways: `Chat360.startBot()` (reads config already set on
 * [com.chat360.chatbot.common.models.ConfigService] via `CoreConfigs`) or a direct [launch] call
 * for standalone testing. See [resolveChatConfig] for how the two are told apart.
 */
class ChatComposeActivity : ComponentActivity() {

    private val config: ResolvedChatConfig by lazy { resolveChatConfig(intent) }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(context = applicationContext, baseUrl = config.baseUrl, botId = config.botId, historyEnabled = config.historyEnabled, suppressInitialBotMessages = config.Chat360UIConfig.behavior.suppressInitialBotMessages)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Chat360Theme(
                preset = config.themePreset,
                customLightColors = config.customLightColors,
                customDarkColors = config.customDarkColors,
                customTypography = config.customTypography,
                customBranding = config.customBranding,
                config = config.Chat360UIConfig,
            ) {
                val statusBar = LocalChat360Colors.current.statusBar
                SideEffect {
                    window.statusBarColor = statusBar.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = false
                }
                ChatScreen(viewModel)
            }
        }
    }

    companion object {
        /** Standalone-testing entry point; the public API path (`Chat360.startBot`) doesn't use this. */
        fun launch(
            context: Context,
            botId: String,
            baseUrl: String = "https://app.chat360.io",
            themePreset: Chat360ThemePreset = Chat360ThemePreset.DEFAULT,
            historyEnabled: Boolean = true,
        ) {
            val intent = Intent(context, ChatComposeActivity::class.java)
                .putExtra(EXTRA_BOT_ID, botId)
                .putExtra(EXTRA_BASE_URL, baseUrl)
                .putExtra(EXTRA_THEME_PRESET, themePreset.name)
                .putExtra(EXTRA_HISTORY_ENABLED, historyEnabled)
            context.startActivity(intent)
        }
    }
}
