package com.chat360.chat360demoapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.chat360.chatbot.config.BrandingConfig
import com.chat360.chatbot.config.BehaviorConfig
import com.chat360.chatbot.config.Chat360Config
import com.chat360.chatbot.config.DefaultTheme
import com.chat360.chatbot.config.FeatureConfig
import com.chat360.chatbot.config.ThemeConfig
import com.chat360.chatbot.ui.theme.Chat360Branding
import com.chat360.chatbot.ui.theme.Chat360Colors
import com.chat360.chatbot.ui.theme.Chat360Typography

val MarutiSuzukiLightColors = Chat360Colors(
    accent = Color(0xFF005BAC), accentContrast = Color.White,
    background = Color(0xFFF6F8FB), backgroundElevated = Color.White, backgroundSunken = Color(0xFFF0F4F8),
    line = Color(0xFFD7E0E9), textPrimary = Color(0xFF10243A), textSecondary = Color(0xFF5E7185), textDisabled = Color(0xFF9BA8B5),
    bubbleUserBackground = Color(0xFF005BAC), bubbleUserText = Color.White,
    bubbleAiBackground = Color.White, bubbleAiText = Color(0xFF10243A), cardBackground = Color.White, cardBorder = Color(0xFFD7E0E9),
    inputBackground = Color.White, inputBorder = Color(0xFFBAC8D6), statusBar = Color(0xFF003D73),
)

val MarutiSuzukiDarkColors = MarutiSuzukiLightColors.copy(
    background = Color(0xFF0E1721), backgroundElevated = Color(0xFF162331), backgroundSunken = Color(0xFF111C28),
    line = Color(0xFF2B3C4D), textPrimary = Color(0xFFF2F7FB), textSecondary = Color(0xFFB2C0CE), textDisabled = Color(0xFF607080),
    bubbleAiBackground = Color(0xFF162331), bubbleAiText = Color(0xFFF2F7FB), cardBackground = Color(0xFF162331), cardBorder = Color(0xFF2B3C4D),
    inputBackground = Color(0xFF162331), inputBorder = Color(0xFF3B4D60),
)

val MarutiSuzukiBranding = Chat360Branding(
    botTitle = "Maruti Suzuki Assist",
    logo = null,
    welcomeHeading = "Maruti Suzuki Assist",
    inputPlaceholder = "Ask about cars, service, or offers...",
)

val MarutiSuzukiTypography = Chat360Typography(FontFamily.SansSerif, FontFamily.SansSerif)

val MarutiSuzukiConfig = Chat360Config(
    branding = BrandingConfig(
        botName = "Maruti Suzuki Assist",
        welcomeTitle = "Maruti Suzuki Assist",
        inputPlaceholder = "Ask about cars, service, or offers...",
        primaryColor = Color(0xFF005BAC),
        secondaryColor = Color(0xFF5E7185),
        companyName = "Maruti Suzuki",
    ),
    theme = ThemeConfig(defaultTheme = DefaultTheme.LIGHT, allowThemeSwitch = false),
    features = FeatureConfig(
        showMenu = false, showHistorySidebar = false, showNewChat = false,
        showFeedback = true, showCopyMessage = true, showRegenerate = false,
        showLike = true, showDislike = true, showEmoji = true, showAttachment = true,
        showVoiceInput = false, showAssistantMode = false, showAppearanceSwitcher = false,
    ),
    behavior = BehaviorConfig(suppressInitialBotMessages = false),
)
