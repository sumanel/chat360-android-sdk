package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.runtime.Composable
import com.chat360.chatbot.model.wire.BotContent

/**
 * Fallback for any bot node type without a dedicated renderer yet. Only reached when the node
 * has fallback text (see ChatViewModel's drop check) - [nodeType] is available if a future
 * placeholder ("this bot sent a [Calendly] card - update the app to view it") is wanted.
 */
@Composable
fun UnsupportedContent(text: String, content: BotContent.Unsupported) {
    PlainTextContent(text)
}
