package com.chat360.chatbot.model.wire

import kotlinx.serialization.Serializable

@Serializable
data class PingMessage(
    val type: String = "ping",
    val timestamp_int: Long,
)
