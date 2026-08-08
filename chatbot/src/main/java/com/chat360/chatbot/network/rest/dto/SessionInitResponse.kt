package com.chat360.chatbot.network.rest.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SessionInitResponse(
    val id: String? = null,
    val nodeType: String? = null,
    val targetId: String? = null,
    val room_id: String,
    val owner_id: String,
    val session_token: String? = null,
    val session_id: String? = null,
    // Live-chat resume state - lets a killed-and-reopened app pick up mid-live-chat state
    // correctly instead of assuming a fresh bot-flow session.
    val takeover: Boolean = false,
    val assigned_user: SessionAssignedUser? = null,
    val configs: SessionConfigs? = null,
    // Kept as a raw JsonObject rather than a typed SessionBotSettings: this is a large,
    // loosely-specified server object with a dozen+ fields, most with inconsistent shapes
    // across bot configs - e.g. an empty `bot_shortcuts` can come back as `[]` instead of `{}` -
    // and this SDK only cares about a couple of fields inside it. Strict-typing the whole thing
    // risks a shape mismatch on a field we don't even use crashing session-init entirely;
    // extracting just what we need (see ChatRepository) is best-effort instead, matching how
    // fetchAppearance/loadHistory degrade gracefully.
    val bot_settings: JsonObject? = null,
)

@Serializable
data class SessionLanguage(
    val key: String,
    val value: String,
    val default: Boolean = false,
)

@Serializable
data class SessionAssignedUser(
    val avatar: String? = null,
    val user_designation: String? = null,
    val operator_name: String? = null,
)

@Serializable
data class SessionConfigs(
    val should_ask_feedback: Boolean = false,
)
