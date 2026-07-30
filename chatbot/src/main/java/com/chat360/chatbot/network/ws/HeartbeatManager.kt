package com.chat360.chatbot.network.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors WSService.ts's ping/pong cycle: `pingWaitingTimer` (2s, `constants.pingCheckTime` in
 * the widget) is used as both the idle-before-ping delay AND the wait-for-pong delay. Any
 * incoming frame (not just a pong) reschedules the next ping - only a pong cancels the
 * currently-pending "waiting for pong" timer.
 */
class HeartbeatManager(
    private val scope: CoroutineScope,
    private val pingWaitingTimerMs: Long = 2_000,
    private val sendPing: () -> Unit,
    private val onSlowConnectionChanged: (Boolean) -> Unit,
) {
    private var sendJob: Job? = null
    private var waitJob: Job? = null
    private var isSlow = false

    fun start() {
        scheduleSendPing()
    }

    /** Call for every inbound frame, pong or not - matches `_checkPong` always calling `_sendPing()`. */
    fun onMessageReceived(isPong: Boolean) {
        if (isPong) {
            waitJob?.cancel()
            waitJob = null
        }
        if (isSlow) {
            isSlow = false
            onSlowConnectionChanged(false)
        }
        scheduleSendPing()
    }

    fun stop() {
        sendJob?.cancel()
        waitJob?.cancel()
        sendJob = null
        waitJob = null
    }

    private fun scheduleSendPing() {
        sendJob?.cancel()
        sendJob = scope.launch {
            delay(pingWaitingTimerMs)
            sendPing()
            waitJob?.cancel()
            waitJob = scope.launch {
                delay(pingWaitingTimerMs)
                isSlow = true
                onSlowConnectionChanged(true)
            }
        }
    }
}
