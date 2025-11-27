package com.chat360.chatbot.common.models

import android.util.Log
import android.webkit.WebView
import java.lang.ref.WeakReference

object Chat360JSBridge {

    private var webViewRef: WeakReference<WebView>? = null

    fun registerWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    fun send(type: String, data: Map<String, String>) {
        val webView = webViewRef?.get()

        if (webView == null) {
            Log.e("Chat360JSBridge", "WebView reference lost, cannot send event")
            return
        }

        var inner : String? = null
        if (data.isEmpty()) {
            inner = "{}"
        }

        inner = data?.entries?.joinToString(", ") { (key, value) ->
            """$key: '$value'"""
        }
        Log.d("JavaScriptConsole", "{type: '$type', data:{ $inner} ")
        val jsCode = "window.receiveFromApp({type: '$type', data: {$inner}});"

        webView.post {
            try {
                webView.evaluateJavascript(jsCode) {
                    Log.d("Chat360JSBridge", "Event sent: $type")
                }
            } catch (e: Exception) {
                Log.e("Chat360JSBridge", "Error sending JS event", e)
            }
        }
    }
}
