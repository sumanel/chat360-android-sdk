package com.chat360.chatbot.network.rest.thirdparty

import android.util.Log
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomStatusEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomUpdateEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomsListEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomsListResponse
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomUpdateResponse
import com.chat360.chatbot.network.rest.dto.thirdparty.RoomStatusResponse
import com.chat360.chatbot.network.rest.dto.thirdparty.TokenEnvelope
import com.chat360.chatbot.network.rest.dto.thirdparty.TokenResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import com.chat360.chatbot.domain.thirdparty.SalesExecutiveResult
import com.chat360.chatbot.domain.thirdparty.WelcomeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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

class ThirdPartyTasksApiService(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaTypeOrNull()

    companion object {
        /** Filter logcat by this tag alone to see only the history sidebar's `rooms/list`
         * request/response traffic - e.g. `adb logcat -s Chat360RoomsApi`, or type it into
         * Android Studio's Logcat search/tag filter. */
        const val ROOMS_LOG_TAG = "Chat360RoomsApi"

        /** Filter logcat by this tag to see the sales-executive check: the request sent, the reply, and whether it
         * blocked the chat - `adb logcat -s Chat360SalesExec`. */
        const val SALES_EXEC_LOG_TAG = "Chat360SalesExec"

        /** Logcat truncates a single log line at ~4KB, which silently cuts off long response
         * bodies. Split into fixed-size chunks (numbered when there's more than one) so the
         * full payload is always visible. */
        private const val LOG_CHUNK_SIZE = 3000

        private fun logChunked(tag: String, message: String) {
            if (message.length <= LOG_CHUNK_SIZE) {
                Log.d(tag, message)
                return
            }
            val chunks = message.chunked(LOG_CHUNK_SIZE)
            chunks.forEachIndexed { index, chunk ->
                Log.d(tag, "[${index + 1}/${chunks.size}] $chunk")
            }
        }
    }

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
        Log.d(ROOMS_LOG_TAG, "request url=$url")
        val body = try {
            execute(request)
        } catch (e: IOException) {
            Log.e(ROOMS_LOG_TAG, "request failed url=$url", e)
            throw e
        }
        logChunked(ROOMS_LOG_TAG, "response body=$body")
        val response = json.decodeFromString(RoomsListEnvelope.serializer(), body).data
            ?: throw ThirdPartyMalformedResponseException("rooms/list")
        Log.d(ROOMS_LOG_TAG, "rooms=${response.rooms.size} totalCount=${response.totalCount} hasMore=${response.hasMore}")
        response.rooms.forEach { room ->
            Log.d(ROOMS_LOG_TAG, "room room_id=${room.roomId} room_name=${room.roomName} status=${room.status} created_at=${room.createdAt} updated_at=${room.updatedAt} session_count=${room.sessionCount}")
        }
        return response
    }

    /**
     * `GET third-party-tasks/welcome-text`, identified by a `Client-Id` header (no bearer token). The hyphenated
     * spelling is the one the server reads - `client_id` with an underscore is rejected with a 400, so the
     * request would silently fall back to the defaults. Returns
     * the configured heading/text, or null when neither is set. Accepts the fields at the top level, in a
     * `data` object, or in a `data` list of such objects.
     * Throws on any non-2xx (a 404 while the endpoint isn't deployed) or an unreadable body - the caller
     * treats every failure the same way, by keeping what it already has.
     */
    suspend fun fetchWelcomeText(clientId: String): WelcomeText? {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/welcome-text"
        val body = execute(Request.Builder().url(url).addHeader("Client-Id", clientId).get().build())
        val root = json.parseToJsonElement(body) as? JsonObject ?: throw ThirdPartyMalformedResponseException("welcome-text")
        // `data` is either the welcome object itself or a list of them (an empty list when nothing is set).
        // Fields at the top level also work. With a list, the first entry that actually has a heading or text wins.
        val candidates: List<JsonObject> = when (val data = root["data"]) {
            is JsonObject -> listOf(data)
            is JsonArray -> data.filterIsInstance<JsonObject>()
            else -> listOf(root)
        }
        return candidates.firstNotNullOfOrNull { entry ->
            fun field(name: String) = (entry[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            WelcomeText(heading = field("heading"), text = field("text")).takeUnless { it.isEmpty }
        }
    }

    /**
     * `POST third-party-tasks/sales-exectives` (the misspelling is the server's real route), identified by a
     * `Client-Id` header. [details] goes out as the JSON body exactly as given. It must be sent as
     * `application/json` - without that content type the server ignores the body and answers that both
     * `dealer_code` and `emp_code` are required.
     *
     * Throws on any non-2xx (the server answers 400 with `{"success":false,...}` for validation errors and an
     * unconfigured client) or an unreadable body; [SalesExecutiveGate] treats every failure as "let them through".
     */
    suspend fun checkSalesExecutive(clientId: String, details: Map<String, String>): SalesExecutiveResult {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/sales-exectives"
        val body = buildJsonObject { details.forEach { (key, value) -> put(key, value) } }.toString().toRequestBody(jsonMediaType)
        Log.d(SALES_EXEC_LOG_TAG, "request POST $url Client-Id=$clientId body=${details}")
        val reply = try {
            execute(Request.Builder().url(url).addHeader("Client-Id", clientId).post(body).build())
        } catch (e: Exception) {
            Log.w(SALES_EXEC_LOG_TAG, "request failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
        logChunked(SALES_EXEC_LOG_TAG, "response body=$reply")
        val root = json.parseToJsonElement(reply) as? JsonObject ?: throw ThirdPartyMalformedResponseException("sales-exectives")
        val executive = root["sales_executive"] as? JsonObject
        return SalesExecutiveResult(
            success = (root["success"] as? JsonPrimitive)?.booleanOrNull ?: false,
            message = (root["message"] as? JsonPrimitive)?.contentOrNull,
            status = (executive?.get("status") as? JsonPrimitive)?.contentOrNull,
        )
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

    suspend fun submitMessageFeedback(
        roomId: String,
        sessionId: String,
        bearerToken: String,
        messageId: String,
        query: String,
        response: String,
        feedback: String,
        remarks: String?,
    ) {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/feedback/queries"
        val body = buildJsonObject {
            put("room_id", roomId)
            put("session_id", sessionId)
            put("data", buildJsonObject {
                put("message_id", messageId)
                put("query", query)
                put("response", response)
                put("feedback", feedback)
                put("Remarks", remarks)
            })
        }.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .patch(body)
            .build()
        execute(request)
    }

    suspend fun submitPeriodicFeedback(
        roomId: String,
        sessionId: String,
        bearerToken: String,
        feedback: String,
    ) {
        val url = "${baseUrl.trimEnd('/')}/api/third-party-tasks/feedback"
        val body = buildJsonObject {
            put("room_id", roomId)
            put("session_id", sessionId)
            put("data", buildJsonObject {
                put("feedback", feedback)
            })
        }.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $bearerToken")
            .patch(body)
            .build()
        execute(request)
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
