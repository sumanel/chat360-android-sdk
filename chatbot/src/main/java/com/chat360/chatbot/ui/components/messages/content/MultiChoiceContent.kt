package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.common.InlineChoiceList

@Composable
fun MultiChoiceContent(
    message: ChatMessage,
    content: BotContent.MultiChoice,
    isLiveChat: Boolean,
    isConnected: Boolean,
    onQuickReply: (BotContent.MultiChoice.Option) -> Unit,
) {
    Column {
        PlainTextContent(message.text)
        if (content.options.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            InlineChoiceList(
                labels = content.options.map { it.text },
                enabled = message.repliesEnabled && !isLiveChat && isConnected,
                isSelected = { index -> message.selectedReplyIndex == content.options[index].index },
                onSelect = { index -> onQuickReply(content.options[index]) },
            )
        }
    }
}
