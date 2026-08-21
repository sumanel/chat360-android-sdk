package com.chat360.chatbot.domain.thirdparty

import android.util.Log
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyHttpException
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService

class MessageFeedbackRepository(
    private val apiService: ThirdPartyTasksApiService,
    private val tokenManager: ThirdPartyTokenManager,
) {
    suspend fun submit(
        roomId: String,
        sessionId: String,
        messageId: String,
        query: String,
        response: String,
        liked: Boolean,
        remarks: String?,
    ) {
        val feedback = if (liked) "Like" else "Dislike"
        runCatching {
            withAuthRetry { token ->
                apiService.submitMessageFeedback(roomId, sessionId, token, messageId, query, response, feedback, remarks)
            }
        }.onFailure { error -> Log.e("Chat360", "third-party-tasks feedback/queries failed: ${error.message}", error) }
    }

    suspend fun submitPeriodic(roomId: String, sessionId: String, feedback: String) {
        runCatching {
            withAuthRetry { token -> apiService.submitPeriodicFeedback(roomId, sessionId, token, feedback) }
        }.onFailure { error -> Log.e("Chat360", "third-party-tasks feedback failed: ${error.message}", error) }
    }

    private suspend fun <T> withAuthRetry(block: suspend (token: String) -> T): T {
        val token = tokenManager.validToken()
        return try {
            block(token)
        } catch (e: ThirdPartyHttpException) {
            if (e.httpCode != 401) throw e
            tokenManager.invalidate()
            block(tokenManager.validToken())
        }
    }
}
