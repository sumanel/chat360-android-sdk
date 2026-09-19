package com.chat360.chatbot.domain.thirdparty

import android.util.Log
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What `third-party-tasks/sales-exectives` says about the sales executive using the chat. */
data class SalesExecutiveResult(
    val success: Boolean,
    /** The server's own wording, e.g. "Sales Executive onboarded as INACTIVE.". */
    val message: String?,
    /** `ACTIVE` / `INACTIVE` (compared case-insensitively), or null when the reply carried none. */
    val status: String?,
) {
    val isInactive: Boolean get() = success && status.equals("INACTIVE", ignoreCase = true)
}

/**
 * Decides whether the chat is closed for this sales executive, the same way maintenance mode closes it: the
 * socket never opens and the message replaces the input bar.
 *
 * The rule is deliberately narrow. The chat is blocked ONLY when the server answers successfully and says the
 * executive is INACTIVE. Every other outcome - no network, a timeout, a 404 (endpoint not deployed), a 400
 * validation error, an error page, `success: false`, an unknown or missing status, an ACTIVE status - lets the
 * user through and the bot flow run exactly as it would without this check. A check that is silently broken must
 * never lock anyone out.
 *
 * [details] is the host app's map (`dealer_code` and `emp_code` are required by the server; `name`, `status` and
 * anything else are optional) and is sent as the JSON body untouched.
 */
class SalesExecutiveGate(
    private val apiService: ThirdPartyTasksApiService,
    private val clientId: String,
    private val details: Map<String, String>,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val lock = Mutex()
    private var clearedForThisSession = false

    /** The message to show while the chat is closed to this executive, or null to let them through. */
    suspend fun blockedMessage(): String? = lock.withLock {
        // Once the server has said "not inactive" the answer is kept for the session: a later deactivation is
        // delivered by the server closing the socket, so re-asking on every foreground would only add traffic.
        if (clearedForThisSession) return null
        if (details["dealer_code"].isNullOrBlank() || details["emp_code"].isNullOrBlank()) return null

        val result = withTimeoutOrNull(timeoutMs) {
            runCatching { apiService.checkSalesExecutive(clientId, details) }
                .onFailure { Log.w(TAG, "sales-exectives check failed - letting the user through: ${it.message}") }
                .getOrNull()
        }
        when {
            result == null -> {
                Log.i(LOG_TAG, "decision: allowed (no usable reply - the check failed or timed out)")
                null
            }
            result.isInactive -> {
                Log.i(LOG_TAG, "decision: BLOCKED - executive is INACTIVE (${result.message})")
                result.message?.takeIf { it.isNotBlank() } ?: DEFAULT_MESSAGE
            }
            else -> {
                Log.i(LOG_TAG, "decision: allowed (success=${result.success}, status=${result.status})")
                if (result.success && !result.status.isNullOrBlank()) clearedForThisSession = true
                null
            }
        }
    }

    companion object {
        private const val TAG = "Chat360"
        private const val LOG_TAG = ThirdPartyTasksApiService.SALES_EXEC_LOG_TAG
        const val DEFAULT_TIMEOUT_MS = 3_000L
        const val DEFAULT_MESSAGE = "Your access is currently inactive. Please contact your administrator."
    }
}
