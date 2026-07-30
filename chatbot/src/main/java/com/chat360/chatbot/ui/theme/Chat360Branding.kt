package com.chat360.chatbot.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the bot's avatar/logo image comes from. A brand with no logo at all (the generic
 * default) is `null` on [Chat360Branding.logo] - [com.chat360.chatbot.ui.components.common.LogoBadge]
 * falls back to a plain initial-letter badge in that case, never a broken image.
 */
sealed interface Chat360Logo {
    /** A bundled drawable resource, optionally with a separate dark-theme variant. */
    data class Resource(val lightResId: Int, val darkResId: Int = lightResId) : Chat360Logo

    /** A remote image (e.g. a client's own logo returned by the bot appearance API). */
    data class Remote(val url: String) : Chat360Logo
}

data class Chat360Branding(
    val botTitle: String,
    val logo: Chat360Logo?,
    val welcomeHeading: String = botTitle,
    val disclaimerText: String = "$botTitle can make mistakes. Verify important information.",
    val inputPlaceholder: String = "Type a message…",
)

/** The library's own brand-neutral default: no logo image, a generic assistant title. */
val DefaultBranding = Chat360Branding(
    botTitle = "Chat360 Assistant",
    logo = null,
)

val LocalChat360Branding = staticCompositionLocalOf { DefaultBranding }
