package com.chat360.chatbot.domain.windowevent

/**
 * Replaces Chat360JSBridge.kt: the same two-way seam (a bot-authored WINDOW_EVENT node talking
 * to the host app), but as direct in-process Kotlin calls instead of `WebView.evaluateJavascript`
 * / `addJavascriptInterface`. There is no JSON stringify/parse anywhere in this path since both
 * sides are already native.
 *
 * Outbound (bot -> host): a WINDOW_EVENT node with shouldSend=true calls [dispatchToHost], which
 * invokes the host's `Chat360.setHandleWindowEvent` callback (via ConfigService.WebEventHandler)
 * directly and returns its result.
 *
 * Inbound (host -> bot): `Chat360.sendEventToBot(event)` ultimately calls [sendToActiveSession],
 * which - only while the most recently rendered WINDOW_EVENT node has shouldReceive=true -
 * forwards the event into the active chat session as an outgoing message.
 */
object WindowEventBridge {
    private var activeSessionSender: ((Map<String, String>) -> Unit)? = null
    private var receiving = false

    fun registerSession(sender: (Map<String, String>) -> Unit) {
        activeSessionSender = sender
    }

    fun unregisterSession() {
        activeSessionSender = null
        receiving = false
    }

    /** Called by ChatRepository whenever a bot message arrives - only WINDOW_EVENT nodes set this true. */
    fun setReceiving(enabled: Boolean) {
        receiving = enabled
    }

    fun dispatchToHost(handleWindowEvent: ((Map<String, String>) -> Map<String, String>)?, sendData: Map<String, String>): Map<String, String> {
        return handleWindowEvent?.invoke(sendData) ?: emptyMap()
    }

    fun sendToActiveSession(event: Map<String, String>) {
        if (!receiving) return
        activeSessionSender?.invoke(event)
    }
}
