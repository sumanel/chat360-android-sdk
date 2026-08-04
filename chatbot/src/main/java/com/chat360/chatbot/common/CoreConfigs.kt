package com.chat360.chatbot.common

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.NonNull
import com.chat360.chatbot.R
import com.chat360.chatbot.config.Chat360UIConfig
import com.chat360.chatbot.ui.theme.Chat360Branding
import com.chat360.chatbot.ui.theme.Chat360Colors
import com.chat360.chatbot.ui.theme.Chat360ThemePreset
import com.chat360.chatbot.ui.theme.Chat360Typography

public class CoreConfigs(botId: String, applicationContext: Context, flutter: Boolean, meta: Map<String, String>?, isDebug: Boolean?, useNewUI: Boolean) {
    var botId: String? = botId
    @Deprecated("not currently in use") var deviceToken = ""
    var statusBarColor = -1
    var isDebug = isDebug
    var statusBarColorFromHex = ""
    var showCloseButton = true
    var closeButtonColor = -1
    var closeButtonColorFromHex = ""
    var version = 1
    var notificationSmallIcon = -1
    var notificationLargeIcon = -1
    var flutter: Boolean = flutter
    var meta: Map<String, String>? = meta
    val useNewUI: Boolean = useNewUI ?: false

    /** Which built-in look to start from; CUSTOM defers entirely to [customLightColors]/[customDarkColors]/[customBranding]. */
    var themePreset: Chat360ThemePreset = Chat360ThemePreset.DEFAULT
    var customLightColors: Chat360Colors? = null
    var customDarkColors: Chat360Colors? = null
    var customTypography: Chat360Typography? = null
    var customBranding: Chat360Branding? = null
    var historyEnabled: Boolean = true
    /** Optional modern, immutable Compose UI configuration. Legacy fields remain supported. */
    var Chat360UIConfig: Chat360UIConfig? = null
}
