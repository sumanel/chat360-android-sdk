package com.chat360.chatbot.network.rest.dto.thirdparty

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomUpdateEnvelope(
    val success: Boolean = false,
    val data: RoomUpdateResponse? = null,
)

@Serializable
data class RoomUpdateResponse(
    @SerialName("room_id") val roomId: String,
    @SerialName("room_name") val roomName: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
)
