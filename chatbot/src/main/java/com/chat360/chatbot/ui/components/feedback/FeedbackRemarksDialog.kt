package com.chat360.chatbot.ui.components.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chat360.chatbot.R
import com.chat360.chatbot.ui.components.chrome.ActiveRed
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

private val DialogCornerRadius = RoundedCornerShape(20.dp)
private val FieldCornerRadius = RoundedCornerShape(10.dp)

/** A thumbs-down reason must be at least this long - the feedback API rejects a dislike without a remark. */
internal const val DISLIKE_MIN_REMARKS_LENGTH = 20

/** A thumbs-up reason is optional, so no characters are required. */
internal const val LIKE_MIN_REMARKS_LENGTH = 0

/** Application-wide feedback needs some text - an empty one is not worth sending. */
internal const val APP_FEEDBACK_MIN_REMARKS_LENGTH = 1

internal fun remarksAreValid(trimmedLength: Int, minLength: Int): Boolean = trimmedLength >= minLength

/** The text to send as the remark: trimmed, or null (nothing sent) when the user typed nothing. */
internal fun remarksToSend(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

/**
 * Collects the reason for a message rating. For a "not helpful" (thumbs down) rating it is required - the feedback
 * API rejects a dislike without a remark, so [onSubmit] is gated behind [minLength] characters (20 by default). The
 * thumbs-up variant (see [LikeRemarksDialog]) passes `minLength = 0`, which makes the text optional.
 *
 * [onDismiss] is called when the user closes the dialog without submitting.
 */
@Composable
fun FeedbackRemarksDialog(
    onSubmit: (remarks: String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "What went wrong?",
    prompt: String = "Please tell us what went wrong with this response before continuing.",
    minLength: Int = DISLIKE_MIN_REMARKS_LENGTH,
    /** Disables Submit while the request is out. */
    isSending: Boolean = false,
    /** Shown under the field when sending failed; the dialog stays open so the text is not lost. */
    errorMessage: String? = null,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    var remarks by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val trimmedLength = remarks.trim().length
    val hasError = attemptedSubmit && !remarksAreValid(trimmedLength, minLength)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(colors.backgroundElevated, DialogCornerRadius)
                .border(1.dp, colors.line, DialogCornerRadius)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontFamily = typography.textFamily,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_close_24),
                    contentDescription = "Close",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp).clickable { onDismiss() },
                )
            }
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = prompt,
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
                text = when {
                    minLength == 0 -> "Optional"
                    minLength == 1 -> "Required"
                    hasError -> "Please enter at least $minLength characters"
                    else -> "$trimmedLength/$minLength"
                },
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                color = if (hasError) ActiveRed else colors.textSecondary,
            )
            if (errorMessage != null) {
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = errorMessage, fontFamily = typography.textFamily, fontSize = 12.sp, color = ActiveRed)
            }
            Spacer(modifier = Modifier.size(16.dp))
            Button(
                enabled = !isSending,
                onClick = {
                    attemptedSubmit = true
                    if (!remarksAreValid(trimmedLength, minLength)) return@Button
                    onSubmit(remarks.trim())
                },
                shape = FieldCornerRadius,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentContrast,
                    disabledContainerColor = colors.textDisabled,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isSending) "Sending…" else "Submit", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** The thumbs-up popup: the same dialog as a dislike, but the text is optional. */
@Composable
fun LikeRemarksDialog(
    onSubmit: (remarks: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    FeedbackRemarksDialog(
        onSubmit = { onSubmit(remarksToSend(it)) },
        onDismiss = onDismiss,
        title = "What did you like?",
        prompt = "Tell us what was helpful about this response (optional).",
        minLength = LIKE_MIN_REMARKS_LENGTH,
    )
}

/** The drawer's application-wide feedback popup: the same dialog, with required text and a send state. */
@Composable
fun AppFeedbackDialog(
    isSending: Boolean,
    errorMessage: String?,
    onSubmit: (feedback: String) -> Unit,
    onDismiss: () -> Unit,
) {
    FeedbackRemarksDialog(
        onSubmit = onSubmit,
        onDismiss = onDismiss,
        title = "Share your feedback",
        prompt = "Tell us about your experience with the app.",
        minLength = APP_FEEDBACK_MIN_REMARKS_LENGTH,
        isSending = isSending,
        errorMessage = errorMessage,
    )
}
