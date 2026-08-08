package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.PromptState
import com.chat360.chatbot.ui.components.common.QuickReplyButton
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import java.time.LocalTime

@Composable
fun TimePromptContent(
    message: ChatMessage,
    content: BotContent.TimePrompt,
    isLiveChat: Boolean,
    onSubmit: (String) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val state = message.promptState ?: PromptState()
    var hour by remember { mutableIntStateOf(9) }
    var minute by remember { mutableIntStateOf(0) }
    var isPm by remember { mutableStateOf(false) }

    val slotBlocked = content.disabledSlots[hour]?.contains(minute) == true
    val pastBlocked = content.disablePrevious && isBeforeNow(hour, minute, isPm)
    val isValid = !slotBlocked && !pastBlocked
    val fieldsEnabled = !state.submitted && !isLiveChat

    Column {
        PlainTextContent(message.text)
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(value = hour, onChange = { hour = it.coerceIn(1, 12) }, enabled = fieldsEnabled)
            Text(":", fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.width(16.dp))
            Stepper(value = minute, onChange = { minute = it.coerceIn(0, 59) }, enabled = fieldsEnabled, pad = true)
            Spacer(modifier = Modifier.width(12.dp))
            QuickReplyButton(text = "AM", enabled = fieldsEnabled, selected = !isPm, onClick = { isPm = false })
            Spacer(modifier = Modifier.width(6.dp))
            QuickReplyButton(text = "PM", enabled = fieldsEnabled, selected = isPm, onClick = { isPm = true })
        }
        if (!isValid) {
            Spacer(modifier = Modifier.height(6.dp))
            Text("This time slot is not available", color = colors.textDisabled)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { onSubmit(formatTime(hour, minute, isPm)) },
            enabled = isValid && fieldsEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentContrast, disabledContainerColor = colors.textDisabled),
        ) {
            Text(if (state.submitted) "Sent" else "Submit")
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit, enabled: Boolean, pad: Boolean = false) {
    val colors = LocalChat360Colors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }, enabled = enabled) { Text("-", color = colors.accent) }
        Text(if (pad) value.toString().padStart(2, '0') else value.toString(), color = colors.textPrimary)
        IconButton(onClick = { onChange(value + 1) }, enabled = enabled) { Text("+", color = colors.accent) }
    }
}

private fun to24Hour(hour12: Int, isPm: Boolean): Int {
    val h = hour12 % 12
    return if (isPm) h + 12 else h
}

private fun isBeforeNow(hour12: Int, minute: Int, isPm: Boolean): Boolean {
    val now = LocalTime.now()
    val selected = LocalTime.of(to24Hour(hour12, isPm), minute)
    return selected.isBefore(now)
}

private fun formatTime(hour: Int, minute: Int, isPm: Boolean): String =
    "${hour.toString().padStart(2, '0')} : ${minute.toString().padStart(2, '0')} ${if (isPm) "PM" else "AM"}"
