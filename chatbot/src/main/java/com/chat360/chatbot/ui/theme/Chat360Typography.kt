package com.chat360.chatbot.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/** The two font roles every component reads through [LocalChat360Typography] - never a font name directly. */
data class Chat360Typography(
    val headFamily: FontFamily,
    val textFamily: FontFamily,
)

/** The library's own default: the platform's system sans-serif, no bundled font files required. */
val DefaultChat360Typography = Chat360Typography(
    headFamily = FontFamily.SansSerif,
    textFamily = FontFamily.SansSerif,
)

val LocalChat360Typography = staticCompositionLocalOf { DefaultChat360Typography }
