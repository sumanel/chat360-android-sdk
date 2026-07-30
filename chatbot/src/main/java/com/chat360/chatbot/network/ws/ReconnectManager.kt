package com.chat360.chatbot.network.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors layout/index.tsx's onSocketClose reconnect logic: delay = baseDelayMs * intervalCount,
 * doubling intervalCount after each scheduled attempt and resetting to 1 on a successful open.
 * Pure state - no socket reference - so it's unit-testable with a virtual-time scope.
 */
class ReconnectManager(
    private val scope: CoroutineScope,
    private val baseDelayMs: Long = 5_000,
    private val reconnect: () -> Unit,
) {
    private var intervalCount = 1
    private var job: Job? = null

    fun onConnected() {
        job?.cancel()
        job = null
        intervalCount = 1
    }

    /** [suppress] covers both "flow finished"/"chatbox hidden" and the superseded-session signal. */
    fun scheduleReconnect(suppress: Boolean) {
        if (suppress) return
        val delayMs = baseDelayMs * intervalCount
        intervalCount *= 2
        job = scope.launch {
            delay(delayMs)
            reconnect()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
