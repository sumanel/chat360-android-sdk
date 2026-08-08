package com.chat360.chatbot.ui.components.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.R
import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.model.wire.BotNode
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.config.LocalChat360UIConfig
import com.chat360.chatbot.ui.components.common.LogoBadge
import com.chat360.chatbot.ui.components.messages.content.BotContentActions
import com.chat360.chatbot.ui.components.messages.content.BotContentBody
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * Row chrome (avatar, name label, card, timestamp) shared by every bot content type. When
 * [message] is agent-authored and [assignedAgent] is known, the name/avatar swap to the human
 * agent's instead of the bot's, applied per-message since this app has no persistent header
 * identity to swap instead.
 */
@Composable
fun BotMessageRow(message: ChatMessage, actions: BotContentActions, isLiveChat: Boolean = false, assignedAgent: AssignedAgent? = null) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val config = LocalChat360UIConfig.current
    val agent = assignedAgent.takeIf { message.author == BotNode.MessageAuthor.AGENT }
    val clipboard = LocalClipboardManager.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoBadge(size = 28.dp, overrideName = agent?.name, overrideAvatarUrl = agent?.avatarUrl)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bubbleAiBackground, RoundedCornerShape(0.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(0.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BotContentBody(message, actions, isLiveChat)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.timeText,
                fontFamily = typography.textFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textDisabled,
            )
            if (config.features.showCopyMessage) {
            Spacer(modifier = Modifier.size(18.dp))
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_outline_content_copy_24),
                contentDescription = "Copy response",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp).clickable { clipboard.setText(AnnotatedString(message.text)) },
            )
            }
            if (config.features.showRegenerate) {
            Spacer(modifier = Modifier.size(18.dp))
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_baseline_refresh_24),
                contentDescription = "Regenerate response",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp).clickable { config.callbacks.onRegenerateClicked(message.id) },
            )
            }
        }
    }
}
