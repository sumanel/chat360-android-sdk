package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.domain.validation.InputValidators
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.PromptState
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

private val ErrorColor = Color(0xFFDC2626)

/** Ports Email/index.tsx: required + validateTest ("Test" word) + validateEmail format check. */
@Composable
fun EmailPromptContent(
    message: ChatMessage,
    isLiveChat: Boolean,
    onValueChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val state = message.promptState ?: PromptState()
    val value = state.value
    val fieldsEnabled = !state.submitted && !isLiveChat

    val errorText = when {
        value.isBlank() -> null
        InputValidators.validateTest(value) -> "Email cannot contain the word 'Test'"
        !InputValidators.validateEmail(value) -> "Please enter a valid email address"
        else -> null
    }
    val isValid = value.isNotBlank() && errorText == null

    Column {
        PlainTextContent(message.text)
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it, "") },
            enabled = fieldsEnabled,
            singleLine = true,
            isError = errorText != null,
            placeholder = { Text("you@example.com", fontFamily = typography.textFamily) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.inputBorder,
                errorBorderColor = ErrorColor,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        errorText?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, fontFamily = typography.textFamily, fontSize = 12.sp, color = ErrorColor)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onSubmit,
            enabled = isValid && fieldsEnabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.accentContrast,
                disabledContainerColor = colors.textDisabled,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.submitted) "Sent" else "Submit",
                fontFamily = typography.textFamily,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
