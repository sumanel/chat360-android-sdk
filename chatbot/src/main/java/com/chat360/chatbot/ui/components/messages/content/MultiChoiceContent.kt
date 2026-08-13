package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.common.QuickReplyButton

@Composable
fun MultiChoiceContent(
    message: ChatMessage,
    content: BotContent.MultiChoice,
    isLiveChat: Boolean,
    onQuickReply: (BotContent.MultiChoice.Option) -> Unit,
) {
    Column {
        PlainTextContent(message.text)
        content.options.forEach { option ->
            Spacer(modifier = Modifier.height(10.dp))
            QuickReplyButton(
                text = option.text,
                enabled = message.repliesEnabled && !isLiveChat,
                selected = message.selectedReplyIndex == option.index,
                onClick = { onQuickReply(option) },
            )
        }
    }
}
