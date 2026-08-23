package com.chat360.chatbot.ui.components.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chat360.chatbot.ui.components.chrome.ActiveRed
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

private val DialogCornerRadius = RoundedCornerShape(20.dp)
private val FieldCornerRadius = RoundedCornerShape(10.dp)
private const val MIN_FEEDBACK_LENGTH = 20

/**
 * Mandatory, non-dismissable check-in shown every 3-5 bot messages. No cancel/close affordance
 * by design - [onSubmit] is the only way out, gated on at least [MIN_FEEDBACK_LENGTH] characters.
 */
@Composable
fun PeriodicFeedbackDialog(onSubmit: (feedback: String) -> Unit) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    var feedback by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val hasError = attemptedSubmit && feedback.trim().length < MIN_FEEDBACK_LENGTH

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(colors.backgroundElevated, DialogCornerRadius)
                .border(1.dp, colors.line, DialogCornerRadius)
                .padding(20.dp),
        ) {
            Text(
                text = "How's the conversation going?",
                fontFamily = typography.textFamily,
                fontSize = 16.sp,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "Please share your feedback to continue.",
                fontFamily = typography.textFamily,
                fontSize = 13.sp,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.size(14.dp))
            BasicTextField(
                value = feedback,
                onValueChange = { feedback = it },
                textStyle = TextStyle(fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .background(colors.inputBackground, FieldCornerRadius)
                    .border(1.dp, colors.inputBorder, FieldCornerRadius)
                    .padding(12.dp),
            )
            if (hasError) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Please enter at least $MIN_FEEDBACK_LENGTH characters",
                    fontFamily = typography.textFamily,
                    fontSize = 12.sp,
                    color = ActiveRed,
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            Button(
                onClick = {
                    attemptedSubmit = true
                    val trimmed = feedback.trim()
                    if (trimmed.length < MIN_FEEDBACK_LENGTH) return@Button
                    onSubmit(trimmed)
                },
                shape = FieldCornerRadius,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentContrast,
                    disabledContainerColor = colors.textDisabled,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Submit", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
            }
        }
    }
}
