package com.chat360.chatbot.network.rest.thirdparty

import com.chat360.chatbot.network.rest.dto.thirdparty.RoomStatusEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomUpdateEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomsListEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomsListResponse
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomUpdateResponse
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomStatusResponse
import com.chat360.chatbot.network.rest.dto.thirdparty.TokenEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.TokenResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raw OkHttp + kotlinx.serialization REST client for the `third-party-tasks` API family that
 * backs the SDK's rooms/history list. */
class ThirdPartyTasksApiService(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaTypeOrNull()

    suspend fun fetchToken(clientId: String, apiKey: String): TokenResponse {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/auth/token"
        val body = buildJsonObject { put("client_id", clientId) }.toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .post(body)
            .build()
        val responseBody = execute(request)
        return json.decodeFromString(TokenEnvelope.serializer(), responseBody).data
            ?: throw ThirdPartyMalformedResponseException("auth/token")
    }

    /** [clientId] and [agentId] must both be non-blank - there is no partial/best-effort mode
     * for identifying who the rooms belong to. `bot_id` is deliberately never sent: the backend
     * rejects the request outright (400) whenever it's present, regardless of its value -
     * omitting it returns all of this client+agent's rooms across bots instead. */
    suspend fun fetchRoomsList(
        clientId: String,
        bearerToken: String,
        agentId: String,
        limit: Int? = null,
        offset: Int? = null,
    ): RoomsListResponse {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/rooms/list".toHttpUrl()
            .newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("agent_id", agentId)
            .apply { limit?.let { addQueryParameter("limit", it.toString()) } }
            .apply { offset?.let { addQueryParameter("offset", it.toString()) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .get()
            .build()
        val body = execute(request)
        return json.decodeFromString(RoomsListEnvelope.serializer(), body).data
            ?: throw ThirdPartyMalformedResponseException("rooms/list")
    }

    suspend fun updateRoom(
        roomId: String,
        clientId: String,
        roomName: String,
        bearerToken: String,
    ): RoomUpdateResponse {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/room/update"
        val body = buildJsonObject {
            put("room_id", roomId)
            put("client_id", clientId)
            put("room_name", roomName)
        }.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .patch(body)
            .build()
        val responseBody = execute(request)
        return json.decodeFromString(RoomUpdateEnvelope.serializer(), responseBody).data
            ?: throw ThirdPartyMalformedResponseException("room/update")
    }

    suspend fun updateRoomStatus(
        roomId: String,
        clientId: String,
        bearerToken: String,
    ): RoomStatusResponse {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/room/update/status"
        val body = buildJsonObject {
            put("room_id", roomId)
            put("client_id", clientId)
        }.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .patch(body)
            .build()
        val responseBody = execute(request)
        return json.decodeFromString(RoomStatusEnvelope.serializer(), responseBody).data
            ?: throw ThirdPartyMalformedResponseException("room/update/status")
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        cont.resumeWithException(ThirdPartyHttpException(it.code, request.url.toString()))
                        return
                    }
                    cont.resume(it.body?.string().orEmpty())
                }
            }
        })
    }
}
