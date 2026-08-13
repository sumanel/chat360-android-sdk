package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.network.rest.dto.thirdparty.TokenResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyTokenManagerTest {

    @Test
    fun `fetches a token on first use`() = runTest {
        var fetchCount = 0
        val manager = ThirdPartyTokenManager(
            fetchToken = { fetchCount++; TokenResponse(bearerToken = "token-1", expiresIn = 3600) },
            nowMillis = { 0L },
        )

        val token = manager.validToken()

        assertEquals("token-1", token)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `reuses the cached token while it has not expired`() = runTest {
        var fetchCount = 0
        var now = 0L
        val manager = ThirdPartyTokenManager(
            fetchToken = { fetchCount++; TokenResponse(bearerToken = "token-$fetchCount", expiresIn = 3600) },
            nowMillis = { now },
        )

        manager.validToken()
        now += 3_000_000 // well under the 3600s expiry
        val token = manager.validToken()

        assertEquals("token-1", token)
        assertEquals(1, fetchCount)
    }

    @Test
    fun `refetches once the safety margin before expiry has passed`() = runTest {
        var fetchCount = 0
        var now = 0L
        val manager = ThirdPartyTokenManager(
            fetchToken = { fetchCount++; TokenResponse(bearerToken = "token-$fetchCount", expiresIn = 3600) },
            nowMillis = { now },
        )

        manager.validToken()
        // expiresAtMillis = 3_600_000 - 60_000 = 3_540_000; land exactly on/after that boundary.
        now = 3_540_000
        val token = manager.validToken()

        assertEquals("token-2", token)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `invalidate forces the next call to refetch even if not expired`() = runTest {
        var fetchCount = 0
        val manager = ThirdPartyTokenManager(
            fetchToken = { fetchCount++; TokenResponse(bearerToken = "token-$fetchCount", expiresIn = 3600) },
            nowMillis = { 0L },
        )

        manager.validToken()
        manager.invalidate()
        val token = manager.validToken()

        assertEquals("token-2", token)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `concurrent callers only trigger one fetch`() = runTest {
        var fetchCount = 0
        val manager = ThirdPartyTokenManager(
            // Simulated network latency so all 20 callers are genuinely in-flight together before
            // any of them completes, exercising the mutex rather than a call that already returns.
            fetchToken = { fetchCount++; delay(100); TokenResponse(bearerToken = "token-1", expiresIn = 3600) },
            nowMillis = { 0L },
        )

        val results = List(20) { async { manager.validToken() } }.awaitAll()

        assertTrue(results.all { it == "token-1" })
        assertEquals(1, fetchCount)
    }
}
