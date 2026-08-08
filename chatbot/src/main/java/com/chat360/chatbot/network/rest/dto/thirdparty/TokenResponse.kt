package com.chat360.chatbot.network.rest.dto.thirdparty

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenEnvelope(
    val success: Boolean = false,
    val data: TokenResponse? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("bearer_token") val bearerToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long = 3600,
)
