package com.chat360.chatbot.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.R
import com.chat360.chatbot.ui.components.icons.ArrowUpIcon
import com.chat360.chatbot.ui.theme.LocalChat360Branding
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachmentClick: () -> Unit,
    onMicClick: () -> Unit,
    showDictationIcon: Boolean = false,
    onDictateClick: () -> Unit = {},
    onEmojiClick: () -> Unit = {},
    showAttachment: Boolean = true,
    showEmoji: Boolean = true,
    showVoiceInput: Boolean = true,
    showSend: Boolean = true,
    sendEnabled: Boolean = true,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val placeholder = LocalChat360Branding.current.inputPlaceholder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.line)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAttachment) Icon(
            painter = painterResource(R.drawable.ic_outline_insert_file_24),
            contentDescription = "Attach file",
            tint = colors.textSecondary,
            modifier = Modifier
                .clickable(onClick = onAttachmentClick)
                .padding(8.dp)
                .size(24.dp),
        )
        if (showDictationIcon) {
            Icon(
                imageVector = com.chat360.chatbot.ui.components.icons.DictateIcon,
                contentDescription = "Dictate message",
                tint = colors.textSecondary,
                modifier = Modifier
                    .clickable(onClick = onDictateClick)
                    .padding(8.dp)
                    .size(22.dp),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .background(colors.inputBackground, RoundedCornerShape(0.dp))
                .border(1.dp, colors.inputBorder, RoundedCornerShape(0.dp))
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontFamily = typography.textFamily,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontFamily = typography.textFamily,
                                fontSize = 15.sp,
                                color = colors.textSecondary,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (showEmoji) Text(
                text = "🙂",
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(onClick = onEmojiClick)
                    .padding(6.dp),
            )
            if (showVoiceInput) Icon(
                painter = painterResource(R.drawable.chat360_ic_mic),
                contentDescription = "Record voice message",
                tint = colors.textSecondary,
                modifier = Modifier
                                    .clickable(onClick = onMicClick)
                                    .padding(8.dp)
                                    .size(20.dp),
            )
        }
        if (showSend) Spacer(modifier = Modifier.size(12.dp))
        if (showSend) IconButton(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier
                .size(55.dp)
                .background(
                    if (!sendEnabled || value.isBlank()) colors.textDisabled else colors.accent,
                    RoundedCornerShape(0.dp),
                ),
        ) {
            Icon(
                imageVector = ArrowUpIcon,
                contentDescription = "Send",
                tint = colors.accentContrast,
            )
        }
    }
}
