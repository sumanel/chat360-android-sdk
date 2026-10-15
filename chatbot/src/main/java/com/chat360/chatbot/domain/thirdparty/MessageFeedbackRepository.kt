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

    /** Sends application-wide feedback; true once the server accepted it. Blank text is never sent. */
    suspend fun submitAppFeedback(roomId: String, feedback: String, dealerId: String?, empId: String?): Boolean {
        val text = feedback.trim().takeIf { it.isNotEmpty() } ?: return false
        return try {
            withAuthRetry { token -> apiService.submitAppFeedback(roomId, text, dealerId, empId, token) }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Chat360", "third-party-tasks app-feedback failed: ${e.message}", e)
            false
        }
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
