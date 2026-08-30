package com.chat360.chatbot.network.ws

import android.os.Handler
import android.os.Looper
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
    // OkHttp delivers WebSocketListener callbacks on its own internal dispatcher thread, never
    // the caller's thread - but every downstream consumer (ChatRepository's session/room state,
    // ChatViewModel's UI state) is written from plain, unsynchronized vars and assumes single-
    // threaded (main) confinement. Without this, a frame for room A can be mid-processing on
    // OkHttp's thread at the exact moment the main thread reassigns those vars for a user-
    // initiated room switch, corrupting both sides. Posting here - rather than fixing every
    // downstream var - confines the whole callback chain to the main thread, matching what its
    // callers already assume. A single Handler preserves OkHttp's own per-socket callback
    // ordering, since it dispatches posted Runnables strictly in the order they were posted.
    private val mainHandler = Handler(Looper.getMainLooper())

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
                mainHandler.post { onOpen() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "<< RECEIVED: $text")
                mainHandler.post { onMessage(text) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "Socket CLOSED (code=$code, reason=$reason)")
                mainHandler.post { onClosed(code, reason) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Socket FAILURE (response=${response?.code}): ${t.message}", t)
                mainHandler.post { onFailure(t) }
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
