package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.common.InlineChoiceList
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import androidx.compose.material3.Text

/**
 * Renders the AUTOSUGGESTION sub-case of CUSTOMINPUT as a plain choice list: a single tap both
 * selects and submits, matching how MULTI_CHOICE behaves.
 */
@Composable
fun AutoSuggestionContent(
    message: ChatMessage,
    content: BotContent.AutoSuggestion,
    isLiveChat: Boolean,
    isConnected: Boolean,
    onSelected: (index: Int, text: String) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Column {
        PlainTextContent(message.text)
        content.description?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textSecondary)
        }
        if (content.choices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            InlineChoiceList(
                labels = content.choices,
                enabled = message.repliesEnabled && !isLiveChat && isConnected,
                isSelected = { index -> message.selectedReplyIndex == index },
                onSelect = { index -> onSelected(index, content.choices[index]) },
            )
        }
    }
}
