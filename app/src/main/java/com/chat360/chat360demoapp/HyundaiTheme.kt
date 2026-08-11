package com.chat360.chat360demoapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chat360.chatbot.config.BehaviorConfig
import com.chat360.chatbot.config.BrandingConfig
import com.chat360.chatbot.config.Chat360UIConfig
import com.chat360.chatbot.config.DefaultTheme
import com.chat360.chatbot.config.FeatureConfig
import com.chat360.chatbot.config.ThemeConfig
import com.chat360.chatbot.ui.theme.Chat360Branding
import com.chat360.chatbot.ui.theme.Chat360Colors
import com.chat360.chatbot.ui.theme.Chat360Logo
import com.chat360.chatbot.ui.theme.Chat360Typography

/**
 * Hyundai's look lives here, in the demo app, not in the `chatbot` library - it's one client's
 * own branding, assigned as [com.chat360.chatbot.ui.theme.Chat360ThemePreset.CUSTOM] details via
 * `CoreConfigs`, the same way any other host app would configure its own brand. The library ships
 * only the brand-neutral default preset.
 */
val HyundaiLightColors = Chat360Colors(
    accent = Color(0xFF002C5F),
    accentContrast = Color(0xFFFFFFFF),
    background = Color(0xFFF6F3F2),
    backgroundElevated = Color(0xFFFFFFFF),
    backgroundSunken = Color(0xFFF6F3F2),
    line = Color(0xFFE4DCD3),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF676767),
    textDisabled = Color(0xFFCCCCCC),
    bubbleUserBackground = Color(0xFF002C5F),
    bubbleUserText = Color(0xFFFFFFFF),
    bubbleAiBackground = Color(0xFFFFFFFF),
    bubbleAiText = Color(0xFF000000),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE4DCD3),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFCCCCCC),
    statusBar = Color(0xFF0E1F38),
)

val HyundaiDarkColors = Chat360Colors(
    // Same Hyundai navy hue as the light theme, lifted to a brighter shade - the light theme's
    // 0xFF002C5F is used directly as text/icon/border/link color throughout the UI (not just as
    // a button fill), and that near-black navy is essentially invisible against the near-black
    // dark background below. accentContrast flips to dark to match, so accent-filled buttons
    // (which use accent as background + accentContrast as content color) stay legible.
    accent = Color(0xFF5599E7),
    accentContrast = Color(0xFF0B0F14),

    background = Color(0xFF0B0F14),
    backgroundElevated = Color(0xFF12171E),
    backgroundSunken = Color(0xFF070A0E),

    line = Color(0xFF252B34),

    textPrimary = Color(0xFFF8F9FB),
    textSecondary = Color(0xFF8E98A6),
    textDisabled = Color(0xFF5F6772),

    // A lighter shade of the same navy so the bubble reads clearly against the near-black
    // background instead of blending into it; white bubble text keeps solid contrast on it.
    bubbleUserBackground = Color(0xFF2F6FB0),
    bubbleUserText = Color.White,

    bubbleAiBackground = Color(0xFF171C24),
    bubbleAiText = Color(0xFFF8F9FB),

    cardBackground = Color(0xFF12171E),
    cardBorder = Color(0xFF252B34),

    inputBackground = Color(0xFF141A22),
    inputBorder = Color(0xFF303844),

    statusBar = Color(0xFF050608),
)
private val HyundaiSansHead = FontFamily(
    Font(R.font.hyundai_sans_head_light, FontWeight.Light),
    Font(R.font.hyundai_sans_head_regular, FontWeight.Normal),
    Font(R.font.hyundai_sans_head_semibold, FontWeight.SemiBold),
)

private val HyundaiSansText = FontFamily(
    Font(R.font.hyundai_sans_text_regular, FontWeight.Normal),
    Font(R.font.hyundai_sans_text_medium, FontWeight.Medium),
    Font(R.font.hyundai_sans_text_bold, FontWeight.Bold),
)

val HyundaiTypography = Chat360Typography(
    headFamily = HyundaiSansHead,
    textFamily = HyundaiSansText,
)

val HyundaiBranding = Chat360Branding(
    botTitle = "H-Smart AI",
    logo = Chat360Logo.Resource(
        lightResId = R.drawable.chat360_hyundai_logo,
        darkResId = R.drawable.chat360_hyundai_logo_dark,
    ),
    welcomeHeading = "Sales AI Assistant",
    disclaimerText = "H-Smart AI can make mistakes. Verify important information with your dealer.",
    inputPlaceholder = "Ask about vehicles, pricing, offers…",
)

val HyundaiConfig = Chat360UIConfig(
    branding = BrandingConfig(
        botName = "H-Smart AI",
        welcomeTitle = "Sales AI Assistant",
        inputPlaceholder = "Ask about vehicles, pricing, offers...",
    ),
    theme = ThemeConfig(
        defaultTheme = DefaultTheme.LIGHT,
        allowThemeSwitch = true,
        followSystemTheme = false,
    ),
    features = FeatureConfig(
        showMenu = true, showHistorySidebar = true, showNewChat = true,
        showCopyMessage = true, showRegenerate = true, showLike = true, showDislike = true,
        showEmoji = false, showAttachment = false,
        showVoiceInput = false, showSpeechToText = true,
        showAssistantMode = true, showAppearanceSwitcher = true,
        // No Hyundai logo in the active conversation (message rows / typing indicator) - only
        // the pre-chat welcome splash still shows it.
        showBotAvatar = false,
    ),
    // The bot must not speak first - the conversation should open empty/blank, with the user's
    // own message as the first bubble, instead of an unsolicited greeting like "How can I help you".
    behavior = BehaviorConfig(suppressInitialBotMessages = true),
)
