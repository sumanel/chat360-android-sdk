package com.chat360.chatbot.ui.theme

import androidx.compose.ui.graphics.Color

data class Chat360Colors(
    val accent: Color,
    val accentContrast: Color,
    val background: Color,
    val backgroundElevated: Color,
    val backgroundSunken: Color,
    val line: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val bubbleUserBackground: Color,
    val bubbleUserText: Color,
    val bubbleAiBackground: Color,
    val bubbleAiText: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val inputBackground: Color,
    val inputBorder: Color,
    val statusBar: Color? = null,
)

val Chat360DefaultStatusBarColor = Color(0xFF002C5F)

val Chat360Colors.resolvedStatusBar: Color
    get() = statusBar ?: Chat360DefaultStatusBarColor

data class Chat360ColorOverrides(
    val accent: Color? = null,
    val accentContrast: Color? = null,
    val background: Color? = null,
    val backgroundElevated: Color? = null,
    val backgroundSunken: Color? = null,
    val line: Color? = null,
    val textPrimary: Color? = null,
    val textSecondary: Color? = null,
    val textDisabled: Color? = null,
    val bubbleUserBackground: Color? = null,
    val bubbleUserText: Color? = null,
    val bubbleAiBackground: Color? = null,
    val bubbleAiText: Color? = null,
    val cardBackground: Color? = null,
    val cardBorder: Color? = null,
    val inputBackground: Color? = null,
    val inputBorder: Color? = null,
    val statusBar: Color? = null,
)

fun Chat360Colors.applyOverrides(overrides: Chat360ColorOverrides?): Chat360Colors {
    if (overrides == null) return this
    return copy(
        accent = overrides.accent ?: accent,
        accentContrast = overrides.accentContrast ?: accentContrast,
        background = overrides.background ?: background,
        backgroundElevated = overrides.backgroundElevated ?: backgroundElevated,
        backgroundSunken = overrides.backgroundSunken ?: backgroundSunken,
        line = overrides.line ?: line,
        textPrimary = overrides.textPrimary ?: textPrimary,
        textSecondary = overrides.textSecondary ?: textSecondary,
        textDisabled = overrides.textDisabled ?: textDisabled,
        bubbleUserBackground = overrides.bubbleUserBackground ?: bubbleUserBackground,
        bubbleUserText = overrides.bubbleUserText ?: bubbleUserText,
        bubbleAiBackground = overrides.bubbleAiBackground ?: bubbleAiBackground,
        bubbleAiText = overrides.bubbleAiText ?: bubbleAiText,
        cardBackground = overrides.cardBackground ?: cardBackground,
        cardBorder = overrides.cardBorder ?: cardBorder,
        inputBackground = overrides.inputBackground ?: inputBackground,
        inputBorder = overrides.inputBorder ?: inputBorder,
        statusBar = overrides.statusBar ?: statusBar,
    )
}

/**
 * The library's own brand-neutral default (a simple blue/gray palette) - used whenever a host
 * app doesn't request a specific preset and hasn't supplied its own colors.
 */
val DefaultLightColors = Chat360Colors(
    accent = Color(0xFF2563EB),
    accentContrast = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FA),
    backgroundElevated = Color(0xFFFFFFFF),
    backgroundSunken = Color(0xFFF0F1F4),
    line = Color(0xFFE2E5EA),
    textPrimary = Color(0xFF1A1D21),
    textSecondary = Color(0xFF6B7280),
    textDisabled = Color(0xFFC7CBD1),
    bubbleUserBackground = Color(0xFF2563EB),
    bubbleUserText = Color(0xFFFFFFFF),
    bubbleAiBackground = Color(0xFFFFFFFF),
    bubbleAiText = Color(0xFF1A1D21),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE2E5EA),
    inputBackground = Color(0xFFFFFFFF),
    inputBorder = Color(0xFFD1D5DB),
    statusBar = Color(0xFF2563EB),
)

val DefaultDarkColors = Chat360Colors(
    accent = Color(0xFF6D9BFF),
    accentContrast = Color(0xFF0B0D10),
    background = Color(0xFF0F1115),
    backgroundElevated = Color(0xFF181B20),
    backgroundSunken = Color(0xFF14161A),
    line = Color(0xFF2A2E35),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFF9AA1AC),
    textDisabled = Color(0xFF4B5058),
    bubbleUserBackground = Color(0xFF2563EB),
    bubbleUserText = Color(0xFFFFFFFF),
    bubbleAiBackground = Color(0xFF1E2127),
    bubbleAiText = Color(0xFFF2F3F5),
    cardBackground = Color(0xFF181B20),
    cardBorder = Color(0xFF2A2E35),
    inputBackground = Color(0xFF181B20),
    inputBorder = Color(0xFF32363E),
    statusBar = Color(0xFF0B0D10),
)
