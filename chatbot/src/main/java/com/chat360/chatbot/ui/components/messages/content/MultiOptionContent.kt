package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** A checkbox list (unlike MultiChoice, more than one option can be checked). */
@Composable
fun MultiOptionContent(
    message: ChatMessage,
    content: BotContent.MultiOption,
    isLiveChat: Boolean,
    onToggle: (index: Int) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val enabled = message.repliesEnabled && !isLiveChat

    Column {
        content.description?.let {
            Text(it, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
        }
        content.options.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = option.index in message.checkedIndices,
                    onCheckedChange = { onToggle(option.index) },
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent),
                )
                Text(option.text, fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSubmit,
            enabled = enabled && message.checkedIndices.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentContrast, disabledContainerColor = colors.textDisabled),
        ) {
            Text("Submit", fontFamily = typography.textFamily)
        }
    }
}
