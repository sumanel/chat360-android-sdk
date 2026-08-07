package com.chat360.chatbot.network.rest.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DealerRoomsResponse(
    val count: Int = 0,
    val results: List<DealerRoomDto> = emptyList(),
)

@Serializable
data class DealerRoomDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("room_name") val roomName: String = "",
    @SerialName("agent_uuid") val agentUuid: String,
    @SerialName("dealer_code") val dealerCode: String = "",
    @SerialName("employee_code") val employeeCode: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("first_message_time") val firstMessageTime: String? = null,
    @SerialName("last_message_time") val lastMessageTime: String? = null,
)
