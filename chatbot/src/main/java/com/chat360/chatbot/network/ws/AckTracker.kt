package com.chat360.chatbot.network.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Per-message delivery tracking. An unacked send is retried [retryDelaysMs] times - covering a
 * couple of flaky minutes, e.g. while the socket is mid-reconnect - before [onTimeout] finally
 * flags it as failed. OkHttp's WebSocket already queues writes made before the handshake
 * completes, so there is nothing to buffer here beyond re-issuing the write on each retry.
 */
class AckTracker(
    private val scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = listOf(15_000, 20_000, 30_000, 40_000, 30_000),
    private val onTimeout: (chatMsgId: String) -> Unit,
) {
    private val timers = mutableMapOf<String, Job>()

    /** [resend] re-issues the same wire message; called on every retry, never on the first
     * (already-sent) attempt. */
    fun trackSend(chatMsgId: String, resend: () -> Unit) {
        scheduleAttempt(chatMsgId, attempt = 0, resend)
    }

    private fun scheduleAttempt(chatMsgId: String, attempt: Int, resend: () -> Unit) {
        timers[chatMsgId]?.cancel()
        timers[chatMsgId] = scope.launch {
            delay(retryDelaysMs[attempt])
            // Every tick resends, including the last - each send now also actively tries to
            // reconnect a dead socket (see ChatRepository.ensureReconnecting), so the last
            // scheduled resend is exactly the one most likely to land right as connectivity
            // recovers. A bare timeout on that final tick (the old behavior) would silently
            // drop a message that a working connection was only moments away from delivering.
            resend()
            if (attempt < retryDelaysMs.lastIndex) {
                scheduleAttempt(chatMsgId, attempt + 1, resend)
            } else {
                // One last grace window, equal to the final interval, for that last resend's
                // ack to actually arrive before giving up.
                delay(retryDelaysMs.last())
                timers.remove(chatMsgId)
                onTimeout(chatMsgId)
            }
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
