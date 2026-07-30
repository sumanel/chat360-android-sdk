package com.chat360.chatbot.network.rest.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionInitResponse(
    val id: String? = null,
    val nodeType: String? = null,
    val targetId: String? = null,
    val room_id: String,
    val owner_id: String,
    val session_token: String? = null,
    val session_id: String? = null,
    // Live-chat resume state (initSession.ts:407-410) - lets a killed-and-reopened app pick up
    // mid-live-chat state correctly instead of assuming a fresh bot-flow session.
    val takeover: Boolean = false,
    val assigned_user: SessionAssignedUser? = null,
    val configs: SessionConfigs? = null,
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
