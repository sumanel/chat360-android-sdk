package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.components.icons.StarIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors

/** Renders a row of stars, or a row of 5 fixed emoji; selecting index `i` sends `i + 1`. */
@Composable
fun RatingContent(message: ChatMessage, content: BotContent.Rating, isLiveChat: Boolean, onRatingSelected: (Int) -> Unit) {
    val colors = LocalChat360Colors.current
    val selected = message.selectedReplyIndex
    val enabled = message.repliesEnabled && !isLiveChat

    Row {
        if (content.style == "star") {
            repeat(content.scale) { index ->
                val filled = selected != null && index <= selected
                Icon(
                    imageVector = StarIcon,
                    contentDescription = "Star ${index + 1}",
                    tint = if (filled) colors.accent else colors.line,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clickable(enabled = enabled) { onRatingSelected(index + 1) },
                )
            }
        } else {
            EMOJI_SET.forEachIndexed { index, emoji ->
                val isSelected = selected == index
                Text(
                    text = emoji,
                    fontSize = if (isSelected) 26.sp else 22.sp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickable(enabled = enabled) { onRatingSelected(index + 1) },
                )
            }
        }
    }
}

private val EMOJI_SET = listOf("😞", "😕", "😐", "😊", "🤩")
