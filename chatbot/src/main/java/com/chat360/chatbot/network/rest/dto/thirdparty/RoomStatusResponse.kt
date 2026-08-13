package com.chat360.chatbot.network.rest.dto.thirdparty

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomStatusEnvelope(
    val success: Boolean = false,
    val data: RoomStatusResponse? = null,
)

@Serializable
data class RoomStatusResponse(
    @SerialName("room_id") val roomId: String,
    val status: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
)
