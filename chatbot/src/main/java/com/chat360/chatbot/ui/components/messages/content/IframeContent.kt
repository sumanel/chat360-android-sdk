package com.chat360.chatbot.ui.components.messages.content

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chat360.chatbot.model.wire.BotContent
import org.json.JSONObject

/**
 * Renders the IFRAME node with `postMessage` auto-advance: the bot flow should jump forward when
 * the embedded page posts a `message` event with `data.type === moveForEvent`. A native WebView
 * has no parent/child browsing context to listen from outside, so this injects a small script
 * into the embedded page itself that listens on its *own* `window` and relays a match back to
 * Android through a `JavascriptInterface`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IframeContent(content: BotContent.IframeContent, onAdvance: (String) -> Unit) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                val moveForEvent = content.moveForEvent
                val targetId = content.targetId
                if (!moveForEvent.isNullOrBlank() && targetId != null) {
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onEvent() = onAdvance(targetId)
                        },
                        "Chat360IframeBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(bridgeScript(moveForEvent), null)
                        }
                    }
                }
                loadUrl(content.url)
            }
        },
        update = { it.loadUrl(content.url) },
        modifier = Modifier
            .fillMaxWidth()
            .height((content.heightDp ?: 240).dp)
            .clip(RoundedCornerShape(10.dp)),
    )
}

private fun bridgeScript(moveForEvent: String): String {
    val quotedEvent = JSONObject.quote(moveForEvent)
    return """
        (function() {
          if (window.__chat360BridgeInstalled) return;
          window.__chat360BridgeInstalled = true;
          window.addEventListener('message', function(e) {
            try {
              if (e.origin === window.location.origin && e.data && e.data.type === $quotedEvent) {
                Chat360IframeBridge.onEvent();
              }
            } catch (err) {}
          });
        })();
    """.trimIndent()
}
