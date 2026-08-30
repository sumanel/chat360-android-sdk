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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.chat360.chatbot.ui.components.feedback.FeedbackRemarksDialog
import com.chat360.chatbot.ui.components.messages.content.BotContentActions
import com.chat360.chatbot.ui.components.messages.content.BotContentBody
import com.chat360.chatbot.ui.components.messages.content.copyText
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

@Composable
fun BotMessageRow(
    message: ChatMessage,
    actions: BotContentActions,
    isLiveChat: Boolean = false,
    isConnected: Boolean = true,
    assignedAgent: AssignedAgent? = null,
    onFeedback: (liked: Boolean, remarks: String?) -> Unit = { _, _ -> },
    onClearFeedback: () -> Unit = {},
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val config = LocalChat360UIConfig.current
    val agent = assignedAgent.takeIf { message.author == BotNode.MessageAuthor.AGENT }
    val clipboard = LocalClipboardManager.current
    val feedback = message.liked
    var showDislikeDialog by remember(message.id) { mutableStateOf(false) }
    Column {
        if (config.features.showBotAvatar) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoBadge(size = 28.dp, overrideName = agent?.name, overrideAvatarUrl = agent?.avatarUrl)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bubbleAiBackground, RoundedCornerShape(0.dp))
                .border(1.dp, colors.cardBorder, RoundedCornerShape(0.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BotContentBody(message, actions, isLiveChat, isConnected)
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
                modifier = Modifier.size(18.dp).clickable { clipboard.setText(AnnotatedString(message.copyText())) },
            )
            }
            if (config.features.showFeedback && config.features.showLike) {
            Spacer(modifier = Modifier.size(18.dp))
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_outline_thumb_up_24),
                contentDescription = "Helpful",
                tint = if (feedback == true) colors.accent else colors.textSecondary,
                modifier = if (feedback == false) {
                    Modifier.size(18.dp)
                } else {
                    Modifier.size(18.dp).clickable {
                        if (feedback == true) {
                            onClearFeedback()
                        } else {
                            onFeedback(true, null)
                        }
                    }
                },
            )
            }
            if (config.features.showFeedback && config.features.showDislike) {
            Spacer(modifier = Modifier.size(18.dp))
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.ic_outline_thumb_down_24),
                contentDescription = "Not helpful",
                tint = if (feedback == false) colors.accent else colors.textSecondary,
                modifier = Modifier.size(18.dp).clickable {
                    if (feedback == false) {
                        onClearFeedback()
                    } else {
                        showDislikeDialog = true
                    }
                },
            )
            }
        }
    }
    if (showDislikeDialog) {
        FeedbackRemarksDialog(
            onSubmit = { remarks ->
                showDislikeDialog = false
                onFeedback(false, remarks)
            },
            onDismiss = { showDislikeDialog = false },
        )
    }
}
