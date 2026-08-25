package com.chat360.chatbot.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.chat360.chatbot.ui.theme.Chat360Logo

/** Immutable, client-owned presentation and behavior configuration for the Compose chat SDK. */
data class Chat360UIConfig(
    val branding: BrandingConfig = BrandingConfig(),
    val theme: ThemeConfig = ThemeConfig(),
    val features: FeatureConfig = FeatureConfig(),
    val behavior: BehaviorConfig = BehaviorConfig(),
    val ui: UIConfig = UIConfig(),
    val callbacks: ChatCallbacks = ChatCallbacks(),
)

data class BrandingConfig(
    val logo: Chat360Logo? = null,
    val botName: String? = null,
    val welcomeTitle: String? = null,
    val welcomeSubtitle: String? = null,
    val primaryColor: Color? = null,
    val secondaryColor: Color? = null,
    val avatar: Chat360Logo? = null,
    val fontFamily: FontFamily? = null,
    val inputPlaceholder: String? = null,
    val headerTitle: String? = null,
    val companyName: String? = null,
)

enum class DefaultTheme { LIGHT, DARK, SYSTEM }

data class ThemeConfig(
    val defaultTheme: DefaultTheme = DefaultTheme.SYSTEM,
    val allowThemeSwitch: Boolean = false,
    val followSystemTheme: Boolean = true,
)

data class FeatureConfig(
    val showMenu: Boolean = false,
    val showHistorySidebar: Boolean = true,
    val showNewChat: Boolean = false,
    val showFeedback: Boolean = true,
    val showCopyMessage: Boolean = true,
    val showRegenerate: Boolean = true,
    val showLike: Boolean = true,
    val showDislike: Boolean = true,
    val showEmoji: Boolean = false,
    val showAttachment: Boolean = false,
    val showVoiceInput: Boolean = true,
    /** Shows speech-to-text beside the input. Defaults to the legacy voice-input setting. */
    val showSpeechToText: Boolean = showVoiceInput,
    val showCamera: Boolean = true,
    val showSend: Boolean = true,
    val showAssistantMode: Boolean = false,
    /** When showAssistantMode is on, controls whether the Training option can be selected.
     * Set to false to show it as a disabled/greyed-out choice (e.g. Hyundai: customer-only). */
    val enableTrainingMode: Boolean = true,
    val showAppearanceSwitcher: Boolean = false,
    val showTypingIndicator: Boolean = true,
    val enableVoicePreview: Boolean = false,
    /** Bot avatar shown next to each message row and the typing indicator - the welcome splash's
     * centered logo is unaffected, this only covers the active-conversation chrome. */
    val showBotAvatar: Boolean = true,
    val showSessionTimer: Boolean = false,
)

data class BehaviorConfig(
    val suppressInitialBotMessages: Boolean = false,
    /** Periodically prompts the user for feedback after every 3-5 bot responses. Only takes
     * effect when the `third-party-tasks` feedback backend is configured (clientId/apiKey set). */
    val enablePeriodicFeedback: Boolean = false,
)

/** Optional full replacements for default Compose chrome. Slots own their interaction handling. */
data class UIConfig(
    val header: (@Composable () -> Unit)? = null,
    val footer: (@Composable () -> Unit)? = null,
    val messageToolbar: (@Composable () -> Unit)? = null,
    val welcomeScreen: (@Composable () -> Unit)? = null,
)

data class ChatCallbacks(
    val onMenuClicked: () -> Unit = {},
    val onNewChatClicked: () -> Unit = {},
    val onCopyClicked: (messageId: String, text: String) -> Unit = { _, _ -> },
    val onRegenerateClicked: (messageId: String) -> Unit = {},
    val onFeedback: (messageId: String, helpful: Boolean) -> Unit = { _, _ -> },
    val onHistorySelected: (historyId: String) -> Unit = {},
)
