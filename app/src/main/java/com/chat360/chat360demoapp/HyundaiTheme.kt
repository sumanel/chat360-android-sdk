package com.chat360.chat360demoapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    accent = Color(0xFF7EB3FF),
    accentContrast = Color(0xFF0C1015),
    background = Color(0xFF0C1015),
    backgroundElevated = Color(0xFF161B22),
    backgroundSunken = Color(0xFF11151B),
    line = Color(0xFF242B35),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9099A6),
    textDisabled = Color(0xFF4B5563),
    bubbleUserBackground = Color(0xFF002C5F),
    bubbleUserText = Color(0xFFFFFFFF),
    bubbleAiBackground = Color(0xFF1B212B),
    bubbleAiText = Color(0xFFF3F4F6),
    cardBackground = Color(0xFF161B22),
    cardBorder = Color(0xFF242B35),
    inputBackground = Color(0xFF161B22),
    inputBorder = Color(0xFF2C333E),
    statusBar = Color(0xFF05070A),
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
    welcomeHeading = "Hyundai Sales AI Assistant",
    disclaimerText = "H-Smart AI can make mistakes. Verify important information with your dealer.",
    inputPlaceholder = "Ask about vehicles, pricing, offers…",
)
