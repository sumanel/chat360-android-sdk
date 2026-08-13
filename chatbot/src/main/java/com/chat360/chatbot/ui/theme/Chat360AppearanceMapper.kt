package com.chat360.chatbot.ui.theme

import androidx.compose.ui.graphics.Color
import com.chat360.chatbot.network.rest.dto.BotAppearanceDetails

/**
 * Maps the bot's server-side appearance config onto [Chat360ColorOverrides]/logo - this is the
 * "other values fetched from the API as per customization" layer, applied on top of whichever
 * theme preset (default/custom) the host app picked.
 */
fun BotAppearanceDetails.toColorOverrides(): Chat360ColorOverrides = Chat360ColorOverrides(
    accent = chatboxHeader?.primarychatboxHeaderColor?.toColorOrNull(),
    statusBar = (chatboxHeader?.primarychatboxHeaderColor ?: chatboxHeader?.secondaryChatboxHeaderColor)?.toColorOrNull(),
    background = (botBackgroundColor ?: primaryBackgroundColor)?.toColorOrNull(),
    bubbleAiBackground = botMessageBoxColor?.toColorOrNull(),
    bubbleAiText = botTextColor?.toColorOrNull(),
    bubbleUserBackground = userMessageBoxColor?.toColorOrNull(),
    bubbleUserText = userTextColor?.toColorOrNull(),
)

/** A remote logo, only if the appearance response actually supplied one. */
fun BotAppearanceDetails.toLogoOverride(): Chat360Logo? =
    chatboxHeader?.chatboxAvatar?.takeIf { it.isNotBlank() }?.let { Chat360Logo.Remote(it) }

/** Parses a "#RRGGBB"/"#AARRGGBB" hex string - shared by appearance-API overrides and any bot-supplied color (e.g. TEXT_CAROUSEL card colors). */
fun String.toColorOrNull(): Color? {
    if (!startsWith("#")) return null
    return try {
        val hex = removePrefix("#")
        val argb = when (hex.length) {
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        Color(argb.toLong(16).toInt())
    } catch (e: NumberFormatException) {
        null
    }
}
