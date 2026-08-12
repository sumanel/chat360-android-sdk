package com.chat360.chatbot.network.ws

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Thin OkHttp WebSocket wrapper: connect, send, and receive raw text frames. Heartbeat,
 * reconnect, and ack-tracking are handled by separate collaborators, not this class.
 */
class Chat360WebSocketClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private var webSocket: WebSocket? = null

    fun connect(
        wsUrl: String,
        onOpen: () -> Unit,
        onMessage: (String) -> Unit,
        onClosed: (code: Int, reason: String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        Log.d(TAG, "Connecting -> $wsUrl")
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Socket OPEN (code=${response.code}) -> $wsUrl")
                onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "<< RECEIVED: $text")
                onMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Socket CLOSED (code=$code, reason=$reason)")
                onClosed(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Socket FAILURE (response=${response?.code}): ${t.message}", t)
                onFailure(t)
            }
        })
    }

    fun send(text: String): Boolean {
        val sent = webSocket?.send(text) ?: false
        if (sent) {
            Log.d(TAG, ">> SENT: $text")
        } else {
            Log.w(TAG, ">> SEND FAILED (socket not open): $text")
        }
        return sent
    }

    fun close() {
        Log.i(TAG, "Closing socket (client requested)")
        webSocket?.close(1000, "client closed")
        webSocket = null
    }

    private companion object {
        const val TAG = "Chat360WS"
    }
}
