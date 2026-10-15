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

/**
 * One button of the Assistant Mode switcher. [variables] are merged into the session-init `meta`
 * (the bot flow's `@`-variables) while this option is selected, e.g. `mapOf("agent_role" to "trainer")`.
 * Selecting a different option starts a new session so the new variables take effect.
 */
data class AssistantModeOption(
    val label: String,
    val variables: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)

/** What to draw on a history row's chat bubble for a room's `agent_role`. */
data class AssistantRoleBadge(
    /** The Assistant Mode button whose role this is (0 = training icon, else person icon); null = no button sends it (generic tag icon). */
    val modeIndex: Int?,
    /** The matched button's label, or the raw role text when nothing matches. */
    val label: String,
)

/** The index of the option whose `agent_role` variable equals [role], ignoring case and surrounding spaces. */
fun List<AssistantModeOption>.indexForRole(role: String?): Int? {
    val wanted = role?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return indexOfFirst { it.variables[FeatureConfig.ASSISTANT_ROLE_KEY]?.trim().equals(wanted, ignoreCase = true) }.takeIf { it >= 0 }
}

/** The badge for a room's [role]: its button when one sends it, a generic one when none does, and null when the room has no role. */
fun List<AssistantModeOption>.badgeForRole(role: String?): AssistantRoleBadge? {
    val trimmed = role?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val index = indexForRole(trimmed)
    return AssistantRoleBadge(modeIndex = index, label = index?.let { this[it].label } ?: trimmed)
}

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
    /** The Assistant Mode buttons, in display order - at most [MAX_ASSISTANT_MODES] (2); any extra are ignored.
     * The first uses the training icon, the second the person icon. */
    val assistantModes: List<AssistantModeOption> = listOf(
        AssistantModeOption("Training"),
        AssistantModeOption("Customer"),
    ),
    /** Index into [assistantModes] selected when the chat opens. */
    val defaultAssistantMode: Int = 1,
    val showAppearanceSwitcher: Boolean = false,
    val showTypingIndicator: Boolean = true,
    val enableVoicePreview: Boolean = false,
    /** Bot avatar shown next to each message row and the typing indicator - the welcome splash's
     * centered logo is unaffected, this only covers the active-conversation chrome. */
    val showBotAvatar: Boolean = true,
    val showSessionTimer: Boolean = false,
    /** A feedback icon in the drawer's header. It opens a popup, and its text is stored for the whole application
     * (by employee id), not for a single message. Needs history (clientId, apiKey, endUserId) to be configured. */
    val showAppFeedback: Boolean = false,
) {
    /** [assistantModes] capped at [MAX_ASSISTANT_MODES], with the legacy [enableTrainingMode] flag applied to the first option. */
    val effectiveAssistantModes: List<AssistantModeOption>
        get() = assistantModes.take(MAX_ASSISTANT_MODES)
            .mapIndexed { i, o -> if (i == 0 && !enableTrainingMode) o.copy(enabled = false) else o }

    /** The index of the button whose variables carry [role] as `agent_role` (ignoring case and surrounding spaces), or null when none does. */
    fun assistantModeIndexForRole(role: String?): Int? = effectiveAssistantModes.indexForRole(role)

    /** The selected option at chat open, coerced into range of the (capped) options. */
    val initialAssistantModeIndex: Int
        get() = defaultAssistantMode.coerceIn(0, (effectiveAssistantModes.size - 1).coerceAtLeast(0))

    /** Variables to seed session-init `meta` with at chat open; empty when the switcher is hidden. */
    val initialAssistantVariables: Map<String, String>
        get() = if (showAssistantMode) effectiveAssistantModes.getOrNull(initialAssistantModeIndex)?.variables.orEmpty() else emptyMap()

    companion object {
        /** The Assistant Mode switcher has room for two buttons. */
        const val MAX_ASSISTANT_MODES = 2

        /** The variable name that carries the role in session-init `meta` and comes back on `rooms/list`. */
        const val ASSISTANT_ROLE_KEY = "agent_role"
    }
}

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
