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
    /** Enables the SDK's rooms/history list (`ChatDrawer`'s "conversations" section), backed by
     * the `third-party-tasks` backend API family (rooms list, rename, soft-delete). This and
     * [apiKey], [endUserId] must **all three** be set together (in addition to [botId], which is
     * already required for everything else) - if only some are set, the SDK logs a loud warning
     * and leaves history disabled; leaving all three unset is a valid choice and simply means the
     * host app isn't using history. Identifies this host app/integration to the backend - ask
     * Chat360 for this value, it is not the same as [botId]. */
    var clientId: String? = null
    /** `x-api-key` header used once to exchange [clientId] for a short-lived bearer token (see
     * [clientId] for the all-or-nothing rule with [endUserId]). Sent from the device on every
     * token refresh - treat it like any other client-side API key (fine for a mobile client
     * calling a backend you control, not a substitute for a server-side secret). */
    var apiKey: String? = null
    /** Identifies the current end user - the identity the `third-party-tasks` backend scopes
     * `rooms/list` results to, so only this user's rooms come back. Sent on the wire as the
     * `agent_id` query parameter (matching the `agent_id` field the backend returns per room) -
     * that's the backend's own naming for this identity, not a claim that it's a *support* agent;
     * it is **not** the same "agent" as [Chat360UIConfig]'s live-chat handoff/assigned-agent
     * concept elsewhere in this SDK. Use whatever identifier your backend integration already
     * uses to key a user's rooms. See [clientId] for the all-or-nothing rule. */
    var endUserId: String? = null
    /** Optional modern, immutable Compose UI configuration. Legacy fields remain supported. */
    var Chat360UIConfig: Chat360UIConfig? = null
}
