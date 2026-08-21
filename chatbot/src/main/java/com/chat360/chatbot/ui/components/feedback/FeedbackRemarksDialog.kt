package com.chat360.chatbot.ui.components.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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

private const val MIN_REMARKS_LENGTH = 20

/**
 * Collects the required reason for a "not helpful" (thumbs down) message rating - the feedback
 * API rejects a dislike without a remark, so this gates [onSubmit] behind [MIN_REMARKS_LENGTH]
 * characters instead of letting the dislike through with an empty/short reason.
 */
@Composable
fun FeedbackRemarksDialog(
    onSubmit: (remarks: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    var remarks by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val trimmedLength = remarks.trim().length
    val hasError = attemptedSubmit && trimmedLength < MIN_REMARKS_LENGTH

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(colors.backgroundElevated, DialogCornerRadius)
                .border(1.dp, colors.line, DialogCornerRadius)
                .padding(20.dp),
        ) {
            Text(
                text = "What went wrong?",
                fontFamily = typography.textFamily,
                fontSize = 16.sp,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "Please share at least $MIN_REMARKS_LENGTH characters so we can improve.",
                fontFamily = typography.textFamily,
                fontSize = 13.sp,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.size(14.dp))
            BasicTextField(
                value = remarks,
                onValueChange = { remarks = it },
                textStyle = TextStyle(fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .background(colors.inputBackground, FieldCornerRadius)
                    .border(1.dp, colors.inputBorder, FieldCornerRadius)
                    .padding(12.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = if (hasError) "Please enter at least $MIN_REMARKS_LENGTH characters" else "$trimmedLength/$MIN_REMARKS_LENGTH",
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                color = if (hasError) ActiveRed else colors.textSecondary,
            )
            Spacer(modifier = Modifier.size(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = FieldCornerRadius,
                    border = BorderStroke(1.dp, colors.inputBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.size(12.dp))
                Button(
                    onClick = {
                        attemptedSubmit = true
                        if (trimmedLength < MIN_REMARKS_LENGTH) return@Button
                        onSubmit(remarks.trim())
                    },
                    shape = FieldCornerRadius,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentContrast,
                        disabledContainerColor = colors.textDisabled,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Submit", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
