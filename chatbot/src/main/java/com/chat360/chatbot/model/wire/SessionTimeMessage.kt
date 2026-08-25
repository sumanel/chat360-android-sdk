package com.chat360.chatbot.model.wire

import kotlinx.serialization.Serializable

@Serializable
data class SessionTimeMessage(
    val data_type: String = "session_time_hyundai",
    val room_id: String? = null,
)
