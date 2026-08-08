package com.chat360.chatbot.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.chat360.chatbot.ui.ChatViewModel
import com.chat360.chatbot.ui.screens.ChatScreen
import com.chat360.chatbot.ui.theme.Chat360Theme
import com.chat360.chatbot.ui.theme.Chat360ThemePreset

/**
 * Fragment counterpart of [ChatComposeActivity], returned by `Chat360.getChatBotView()` for host
 * apps that embed the bot inside their own screen instead of launching a standalone Activity.
 * Config is read from Fragment arguments (never the constructor - the system recreates fragments
 * via the no-arg constructor + saved arguments on config change/process death), resolved the same
 * way as the Activity via [resolveChatConfig]. Status bar styling is intentionally left to the
 * host, which owns the surrounding Window here.
 */
class ChatComposeFragment : Fragment() {

    private val config: ResolvedChatConfig by lazy { resolveChatConfig(arguments) }

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.Factory(context = requireContext().applicationContext, baseUrl = config.baseUrl, botId = config.botId, historyEnabled = config.historyEnabled, clientId = config.clientId, apiKey = config.apiKey, endUserId = config.endUserId, suppressInitialBotMessages = config.Chat360UIConfig.behavior.suppressInitialBotMessages)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            Chat360Theme(
                preset = config.themePreset,
                customLightColors = config.customLightColors,
                customDarkColors = config.customDarkColors,
                customTypography = config.customTypography,
                customBranding = config.customBranding,
                config = config.Chat360UIConfig,
            ) {
                ChatScreen(viewModel)
            }
        }
    }

    companion object {
        /** Standalone-testing entry point; the public API path (`Chat360.getChatBotView`) doesn't use this. */
        fun newInstance(
            botId: String,
            baseUrl: String = "https://app.chat360.io",
            themePreset: Chat360ThemePreset = Chat360ThemePreset.DEFAULT,
            historyEnabled: Boolean = true,
            clientId: String? = null,
            apiKey: String? = null,
            endUserId: String? = null,
        ): ChatComposeFragment = ChatComposeFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_BOT_ID, botId)
                putString(EXTRA_BASE_URL, baseUrl)
                putString(EXTRA_THEME_PRESET, themePreset.name)
                putBoolean(EXTRA_HISTORY_ENABLED, historyEnabled)
                putString(EXTRA_CLIENT_ID, clientId)
                putString(EXTRA_API_KEY, apiKey)
                putString(EXTRA_END_USER_ID, endUserId)
            }
        }
    }
}
