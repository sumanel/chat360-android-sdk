package com.chat360.chatbot.network.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Per-message delivery tracking: mirrors `pendingSendTimers`/`messageAckTimeout` (10s) in
 * layout/index.tsx. OkHttp's WebSocket already queues writes made before the handshake
 * completes, so unlike WSService.ts's `_stackData` there is nothing to buffer here - only the
 * ack-timeout half of that logic applies.
 */
class AckTracker(
    private val scope: CoroutineScope,
    private val ackTimeoutMs: Long = 10_000,
    private val onTimeout: (chatMsgId: String) -> Unit,
) {
    private val timers = mutableMapOf<String, Job>()

    fun trackSend(chatMsgId: String) {
        timers[chatMsgId]?.cancel()
        timers[chatMsgId] = scope.launch {
            delay(ackTimeoutMs)
            timers.remove(chatMsgId)
            onTimeout(chatMsgId)
        }
    }

    fun acknowledge(chatMsgId: String?) {
        chatMsgId ?: return
        timers.remove(chatMsgId)?.cancel()
    }

    fun cancelAll() {
        timers.values.forEach { it.cancel() }
        timers.clear()
    }
}
