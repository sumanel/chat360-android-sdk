package com.chat360.chatbot.network.ws

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
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClosed(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t)
            }
        })
    }

    fun send(text: String): Boolean = webSocket?.send(text) ?: false

    fun close() {
        webSocket?.close(1000, "client closed")
        webSocket = null
    }
}
