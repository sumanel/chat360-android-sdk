package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.PromptState
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

private val ErrorColor = Color(0xFFDC2626)

/**
 * Renders the international-phone variant only - a plain (non-international) PHONE node has no
 * dedicated renderer or validation, so [BotContentBody] never routes here for it; the
 * always-visible bottom input bar answers it as free text.
 */
@Composable
fun PhonePromptContent(
    message: ChatMessage,
    content: BotContent.PhonePrompt,
    isLiveChat: Boolean,
    onValueChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val state = message.promptState ?: PromptState()
    val fieldsEnabled = !state.submitted && !isLiveChat
    val countryCode = state.value
    val nationalNumber = state.secondaryValue
    val touched = countryCode.isNotBlank() && nationalNumber.isNotBlank()
    val isValid = touched && InputValidators.validatePhoneNumber(countryCode + nationalNumber, international = true)

    Column {
        PlainTextContent(message.text)
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            OutlinedTextField(
                value = countryCode,
                onValueChange = { onValueChange(it, nationalNumber) },
                enabled = fieldsEnabled,
                singleLine = true,
                placeholder = { Text("+91", fontFamily = typography.textFamily) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.inputBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
                modifier = Modifier.width(90.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = nationalNumber,
                onValueChange = { onValueChange(countryCode, it) },
                enabled = fieldsEnabled,
                singleLine = true,
                isError = touched && !isValid,
                placeholder = { Text("Phone number", fontFamily = typography.textFamily) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.inputBorder,
                    errorBorderColor = ErrorColor,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        if (touched && !isValid) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Please enter a valid phone number", fontFamily = typography.textFamily, fontSize = 12.sp, color = ErrorColor)
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
