package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.network.rest.dto.thirdparty.TokenResponse
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Caches the third-party-tasks bearer token in memory with an expiry derived from `expires_in`
 * (not the server's `issued_at`, to avoid clock-skew parsing). A 60s safety margin refreshes
 * slightly ahead of actual expiry so an in-flight rooms-list call doesn't race a stale token.
 *
 * [fetchToken] is a closure, not a concrete [ThirdPartyTasksApiService] - tests can supply a fake
 * without a real network client; the secondary constructor covers production callers.
 */
class ThirdPartyTokenManager(
    private val fetchToken: suspend () -> TokenResponse,
    /** Test seam - defaults to the real wall clock. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(
        apiService: ThirdPartyTasksApiService,
        clientId: String,
        apiKey: String,
    ) : this(fetchToken = { apiService.fetchToken(clientId, apiKey) })

    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAtMillis: Long = 0

    suspend fun validToken(): String = mutex.withLock {
        val token = cachedToken
        if (token != null && nowMillis() < expiresAtMillis) return@withLock token
        val response = fetchToken()
        cachedToken = response.bearerToken
        expiresAtMillis = nowMillis() + (response.expiresIn * 1000) - SAFETY_MARGIN_MILLIS
        response.bearerToken
    }

    /** Forces the next [validToken] call to refetch - used after a 401 from a room call. */
    suspend fun invalidate() = mutex.withLock {
        cachedToken = null
        expiresAtMillis = 0
    }

    companion object {
        private const val SAFETY_MARGIN_MILLIS = 60_000L
    }
}
