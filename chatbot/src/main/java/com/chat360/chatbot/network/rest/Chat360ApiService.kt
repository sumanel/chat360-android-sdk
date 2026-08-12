package com.chat360.chatbot.network.rest

import com.chat360.chatbot.model.wire.RawSocketEnvelope
import com.chat360.chatbot.network.rest.dto.BotAppearanceResponse
import com.chat360.chatbot.network.rest.dto.HistoryResponse
import com.chat360.chatbot.network.rest.dto.SessionInitResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Native REST client for the Chat360 backend: session-init, history, appearance, and media
 * upload endpoints.
 */
class Chat360ApiService(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** [roomId]/[sessionId] resume a prior session (returned as-is by the backend, no error,
     * when they're missing/stale/unknown - so it's always safe to pass whatever was last
     * persisted, see ChatRepository/SessionStore) instead of allocating a brand-new room. */
    suspend fun getSession(
        botId: String,
        websiteUrl: String,
        currentUrl: String,
        standalone: Boolean = true,
        roomId: String? = null,
        sessionId: String? = null,
    ): SessionInitResponse {
        val url = "${baseUrl.trimEnd('/')}/api/clientwidget_updated/session/$botId".toHttpUrl()
            .newBuilder()
            .addQueryParameter("website_url", websiteUrl)
            .addQueryParameter("location", "")
            .addQueryParameter("region", "")
            .addQueryParameter("country_code", "")
            .addQueryParameter("current_url", currentUrl)
            .apply { if (standalone) addQueryParameter("standalone", "true") }
            .apply { roomId?.let { addQueryParameter("room_id", it) } }
            .apply { sessionId?.let { addQueryParameter("session_id", it) } }
            .build()

        val request = Request.Builder().url(url).get().build()
        val body = execute(request)
        return json.decodeFromString(SessionInitResponse.serializer(), body)
    }

    /**
     * A bare JSON array of frames (not wrapped like [HistoryResponse]), each decodable the same
     * way history/live frames are (see [HistoryResponse]'s own note) - every item is fed through
     * the identical parsing path used for socket frames.
     */
    suspend fun getFirstMessages(botId: String): List<RawSocketEnvelope> {
        val url = "${baseUrl.trimEnd('/')}/api/chatbox/short-data/$botId"
        val body = execute(Request.Builder().url(url).get().build())
        return json.decodeFromString(ListSerializer(RawSocketEnvelope.serializer()), body)
    }

    /** Pass both [taskType] and [taskValue] to page backwards via a prior response's
     * `previous_cursor`; omit both for the first (most recent) page. */
    suspend fun getHistory(roomId: String, limit: Int = 10, taskType: String? = null, taskValue: Int? = null): HistoryResponse {
        val url = "${baseUrl.trimEnd('/')}/api/clientwidget_updated/chatbox/messages/$roomId".toHttpUrl()
            .newBuilder()
            .addQueryParameter("limit", limit.toString())
            .apply { taskType?.let { addQueryParameter("task_type", it) } }
            .apply { taskValue?.let { addQueryParameter("task_value", it.toString()) } }
            .build()
        val body = execute(Request.Builder().url(url).get().build())
        return json.decodeFromString(HistoryResponse.serializer(), body)
    }

    /** Multipart POST; response is a JSON array of URLs. */
    suspend fun uploadMedia(
        roomId: String,
        botId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onProgress: (percent: Int) -> Unit = {},
    ): List<String> {
        val body = ProgressRequestBody(fileBytes, mimeType.toMediaTypeOrNull(), onProgress)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("media_file", fileName, body)
            .addFormDataPart("bot_id", botId)
            .build()
        val url = "${baseUrl.trimEnd('/')}/api/chatbox/client_media/$roomId"
        val request = Request.Builder().url(url).post(multipart).build()
        val responseBody = execute(request)
        return json.decodeFromString(ListSerializer(String.serializer()), responseBody)
    }

    /**
     * The bot's server-side appearance/branding config. `json_info` is a JSON-encoded string,
     * not nested JSON - [BotAppearanceResponse.details] decodes it once fetched, tolerating a
     * malformed/missing blob (returns null) rather than failing the whole appearance fetch.
     */
    suspend fun getBotAppearance(
        botId: String,
        websiteUrl: String,
        subdomainUrl: String,
        preview: Boolean = false,
    ): BotAppearanceResponse {
        val url = "${baseUrl.trimEnd('/')}/api/chatbox/$botId/chatboxappeareance".toHttpUrl()
            .newBuilder()
            .addQueryParameter("website_url", websiteUrl)
            .addQueryParameter("subdomain_url", subdomainUrl)
            .apply { if (preview) addQueryParameter("preview", "true") }
            .build()
        val body = execute(Request.Builder().url(url).get().build())
        return json.decodeFromString(BotAppearanceResponse.serializer(), body)
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
                        cont.resumeWithException(IOException("HTTP ${it.code} for ${request.url}"))
                        return
                    }
                    cont.resume(it.body?.string().orEmpty())
                }
            }
        })
    }
}
