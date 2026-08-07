package com.chat360.chatbot.network.rest.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RenameRoomRequest(
    @SerialName("room_id") val roomId: String,
    @SerialName("new_room_name") val newRoomName: String,
)
