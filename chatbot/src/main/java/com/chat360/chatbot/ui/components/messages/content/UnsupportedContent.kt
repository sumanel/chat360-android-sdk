package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.runtime.Composable
import com.chat360.chatbot.model.wire.BotContent

/**
 * Fallback for any bot node type without a dedicated renderer. Only reached when the node has
 * fallback text (see ChatViewModel's drop check); [nodeType] is available for building a more
 * specific placeholder message if needed.
 */
@Composable
fun UnsupportedContent(text: String, content: BotContent.Unsupported) {
    PlainTextContent(text)
}
