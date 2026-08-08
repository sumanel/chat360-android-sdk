package com.chat360.chatbot.network.rest.dto.thirdparty

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoomsListEnvelope(
    val success: Boolean = false,
    val data: RoomsListResponse? = null,
)

@Serializable
data class RoomsListResponse(
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("bot_id") val botId: String? = null,
    val rooms: List<RoomDto> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class RoomDto(
    @SerialName("room_id") val roomId: String,
    @SerialName("room_name") val roomName: String = "",
    @SerialName("agent_id") val agentId: String? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("session_ids") val sessionIds: List<String> = emptyList(),
    @SerialName("session_count") val sessionCount: Int = 0,
)
