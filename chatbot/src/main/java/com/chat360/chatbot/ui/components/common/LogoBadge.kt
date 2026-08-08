package com.chat360.chatbot.ui.components.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chat360.chatbot.ui.theme.Chat360Logo
import com.chat360.chatbot.ui.theme.LocalChat360Branding
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import com.chat360.chatbot.ui.theme.LocalChat360ThemeController

/** Displays the configured brand artwork as-is, without an avatar background or border. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier) {
    val branding = LocalChat360Branding.current
    val isDark = LocalChat360ThemeController.current?.isDarkTheme ?: isSystemInDarkTheme()

    when (val logo = branding.logo) {
        is Chat360Logo.Resource -> Image(
            painter = painterResource(if (isDark) logo.darkResId else logo.lightResId),
            contentDescription = "${branding.botTitle} logo",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
        is Chat360Logo.Remote -> AsyncImage(
            model = logo.url,
            contentDescription = "${branding.botTitle} logo",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
        null -> Unit
    }
}

/**
 * The bot's avatar, used in the welcome splash and as the row icon on every bot message. Reads
 * [LocalChat360Branding] rather than taking a logo directly - a brand with no logo configured
 * (the generic default) falls back to a plain initial-letter badge instead of a broken image.
 *
 * [overrideName]/[overrideAvatarUrl] let a live-chat message show the assigned human agent's
 * avatar/initial instead of the bot's, reusing the exact same badge styling.
 */
@Composable
fun LogoBadge(size: Dp, cornerRadius: Dp = 6.dp, overrideName: String? = null, overrideAvatarUrl: String? = null) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val branding = LocalChat360Branding.current
    val isDark = LocalChat360ThemeController.current?.isDarkTheme ?: isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .size(size)
            .background(colors.background, RoundedCornerShape(cornerRadius))
            .border(1.dp, colors.line, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !overrideAvatarUrl.isNullOrBlank() -> AsyncImage(
                model = overrideAvatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(size / 6),
            )
            !overrideName.isNullOrBlank() -> Text(
                text = overrideName.trim().firstOrNull()?.uppercase() ?: "?",
                fontFamily = typography.headFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value / 2).sp,
                color = colors.accent,
            )
            else -> when (val logo = branding.logo) {
                is Chat360Logo.Resource -> Image(
                    painter = painterResource(if (isDark) logo.darkResId else logo.lightResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(size / 6),
                )
                is Chat360Logo.Remote -> AsyncImage(
                    model = logo.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(size / 6),
                )
                null -> Text(
                    text = branding.botTitle.trim().firstOrNull()?.uppercase() ?: "?",
                    fontFamily = typography.headFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value / 2).sp,
                    color = colors.accent,
                )
            }
        }
    }
}
