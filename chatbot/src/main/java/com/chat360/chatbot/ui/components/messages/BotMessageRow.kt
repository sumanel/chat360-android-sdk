package com.chat360.chatbot.ui.components.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.model.wire.BotNode
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.common.LogoBadge
import com.chat360.chatbot.ui.components.messages.content.BotContentActions
import com.chat360.chatbot.ui.components.messages.content.BotContentBody
import com.chat360.chatbot.ui.theme.LocalChat360Branding
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * Row chrome (avatar, name label, card, timestamp) shared by every bot content type. When
 * [message] is agent-authored and [assignedAgent] is known, the name/avatar swap to the human
 * agent's instead of the bot's - mirrors `BotMessageBox.tsx`'s `useAgentAvatar` swap, adapted to
 * per-message since this app has no persistent header identity to swap instead.
 */
@Composable
fun BotMessageRow(message: ChatMessage, actions: BotContentActions, isLiveChat: Boolean = false, assignedAgent: AssignedAgent? = null) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val agent = assignedAgent.takeIf { message.author == BotNode.MessageAuthor.AGENT }
    val displayName = agent?.name?.takeIf { it.isNotBlank() } ?: LocalChat360Branding.current.botTitle
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoBadge(size = 28.dp, overrideName = agent?.name, overrideAvatarUrl = agent?.avatarUrl)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = displayName,
                fontFamily = typography.textFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bubbleAiBackground, RoundedCornerShape(2.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(2.dp))
                .padding(14.dp),
        ) {
            BotContentBody(message, actions, isLiveChat)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.timeText,
            fontFamily = typography.textFamily,
            fontSize = 11.sp,
            color = colors.textDisabled,
        )
    }
}
