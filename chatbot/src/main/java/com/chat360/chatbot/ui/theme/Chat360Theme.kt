package com.chat360.chatbot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import com.chat360.chatbot.config.Chat360UIConfig
import com.chat360.chatbot.config.LocalChat360UIConfig

/**
 * Named starting points a host app (or our own demo) can pick from. `CUSTOM` means "use the
 * colors/typography/branding I'm supplying" - this is how any brand-specific look (a client's
 * own palette, logo, fonts) is configured; the library itself never bundles a named brand
 * preset, only the brand-neutral `DEFAULT`.
 */
enum class Chat360ThemePreset { DEFAULT, CUSTOM }

val LocalChat360Colors = staticCompositionLocalOf { DefaultLightColors }

class Chat360ThemeController internal constructor(state: MutableState<Boolean>) {
    var isDarkTheme by state
        private set

    fun selectDarkTheme(isDark: Boolean) {
        isDarkTheme = isDark
    }
}

val LocalChat360ThemeController = staticCompositionLocalOf<Chat360ThemeController?> { null }

/**
 * Resolves a preset (or a client's own custom colors/typography/branding) to the
 * Colors/Typography/Branding every component reads via CompositionLocal. [colorOverrides]
 * applies on *top* of whichever base preset is chosen - this is the seam the bot's own
 * server-side appearance API customizes through (see ChatRepository.fetchAppearance),
 * independent of which preset a host app picked.
 */
@Composable
fun Chat360Theme(
    preset: Chat360ThemePreset = Chat360ThemePreset.DEFAULT,
    customLightColors: Chat360Colors? = null,
    customDarkColors: Chat360Colors? = null,
    customTypography: Chat360Typography? = null,
    customBranding: Chat360Branding? = null,
    colorOverrides: Chat360ColorOverrides? = null,
    config: Chat360UIConfig = Chat360UIConfig(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val initialDarkTheme = when (config.theme.defaultTheme) {
        com.chat360.chatbot.config.DefaultTheme.LIGHT -> false
        com.chat360.chatbot.config.DefaultTheme.DARK -> true
        com.chat360.chatbot.config.DefaultTheme.SYSTEM -> darkTheme
    }
    val darkThemeState = rememberSaveable(config.theme) { mutableStateOf(initialDarkTheme) }
    val themeController = remember(darkThemeState) { Chat360ThemeController(darkThemeState) }
    val (lightColors, darkColors, typography, branding) = when (preset) {
        Chat360ThemePreset.CUSTOM ->
            Chat360ThemeBundle(
                customLightColors ?: DefaultLightColors,
                customDarkColors ?: DefaultDarkColors,
                customTypography ?: DefaultChat360Typography,
                customBranding ?: DefaultBranding,
            )
        Chat360ThemePreset.DEFAULT ->
            Chat360ThemeBundle(DefaultLightColors, DefaultDarkColors, DefaultChat360Typography, DefaultBranding)
    }

    val selectedColors = if (themeController.isDarkTheme) darkColors else lightColors

    val base = selectedColors.applyOverrides(colorOverrides)

    val resolvedColors = base.copy(
        accent = config.branding.primaryColor ?: base.accent,
        textSecondary = config.branding.secondaryColor ?: base.textSecondary,
    )
    val resolvedBranding = branding.copy(
        botTitle = config.branding.botName ?: branding.botTitle,
        logo = config.branding.logo ?: config.branding.avatar ?: branding.logo,
        welcomeHeading = config.branding.welcomeTitle ?: branding.welcomeHeading,
        disclaimerText = config.branding.welcomeSubtitle ?: branding.disclaimerText,
        inputPlaceholder = config.branding.inputPlaceholder ?: branding.inputPlaceholder,
    )
    val resolvedTypography = config.branding.fontFamily?.let { Chat360Typography(it, it) } ?: typography

    CompositionLocalProvider(
        LocalChat360Colors provides resolvedColors,
        LocalChat360Typography provides resolvedTypography,
        LocalChat360Branding provides resolvedBranding,
        LocalChat360ThemeController provides themeController,
        LocalChat360UIConfig provides config,
        content = content,
    )
}

private data class Chat360ThemeBundle(
    val light: Chat360Colors,
    val dark: Chat360Colors,
    val typography: Chat360Typography,
    val branding: Chat360Branding,
)
