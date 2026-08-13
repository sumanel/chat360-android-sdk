package com.chat360.chatbot.ui.components.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.R
import com.chat360.chatbot.ui.Attachment
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.chrome.ActiveRed
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

@Composable
fun UserMessageRow(message: ChatMessage) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(colors.bubbleUserBackground, RoundedCornerShape(2.dp)),
        ) {
            val attachment = message.attachment
            val voiceMessage = message.voiceMessage
            if (voiceMessage != null) {
                VoiceMessageBubble(voiceMessage)
            } else if (attachment != null) {
                AttachmentRow(attachment)
            } else {
                Text(
                    text = message.text,
                    fontFamily = typography.textFamily,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = colors.bubbleUserText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (message.failed) "Not delivered" else message.timeText,
            fontFamily = typography.textFamily,
            fontSize = 11.sp,
            color = if (message.failed) ActiveRed else colors.textDisabled,
        )
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            attachment.failed -> Icon(
                painter = painterResource(R.drawable.ic_outline_insert_file_24),
                contentDescription = null,
                tint = colors.bubbleUserText,
                modifier = Modifier.size(18.dp),
            )
            attachment.uploaded -> Icon(
                painter = painterResource(R.drawable.ic_outline_insert_file_24),
                contentDescription = null,
                tint = colors.bubbleUserText,
                modifier = Modifier.size(18.dp),
            )
            else -> CircularProgressIndicator(
                progress = { attachment.progress / 100f },
                modifier = Modifier.size(18.dp),
                color = colors.bubbleUserText,
                strokeWidth = 2.dp,
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = when {
                attachment.failed -> "${attachment.fileName} (failed)"
                attachment.uploaded -> attachment.fileName
                else -> "${attachment.fileName} ${attachment.progress}%"
            },
            fontFamily = typography.textFamily,
            fontSize = 14.sp,
            color = colors.bubbleUserText,
            maxLines = 1,
        )
    }
}
