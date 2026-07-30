package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.chat360.chatbot.domain.validation.DateAvailability
import com.chat360.chatbot.domain.validation.FormFieldValidator
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.model.wire.DateRules
import com.chat360.chatbot.ui.ChatMessage
import com.chat360.chatbot.ui.FormState
import com.chat360.chatbot.ui.components.common.QuickReplyButton
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import com.chat360.chatbot.ui.util.rememberAttachmentPicker
import java.time.Instant
import java.time.ZoneOffset

private val ErrorColor = Color(0xFFDC2626)

/**
 * Ports Form/index.tsx's field set (TEXT/NUMBER/EMAIL/PHONE/SELECT/DATE/MEDIA), with the exact
 * per-field validation matrix from FormFieldValidator (mirrors the widget's validate() - required,
 * format, length/count bounds, "Test"-word blocks, phone blocklist, alpha-only). Field types
 * without a dedicated renderer (RCS-specific, etc.) fall back to FieldType.OTHER and render as a
 * disabled placeholder row rather than blocking the whole form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormContent(
    message: ChatMessage,
    content: BotContent.Form,
    isLiveChat: Boolean,
    onFieldChange: (fieldIndex: Int, value: String) -> Unit,
    onMediaFieldPicked: (fieldIndex: Int, bytes: ByteArray, fileName: String, mimeType: String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val formState = message.formState ?: FormState()
    val submitted = formState.submitted
    // Fields/submit disable (not hide) once live chat takes over too, matching every
    // `disabled={isFlowCompleted || isLiveChat}` in RenderRequiredMessage.tsx - kept separate
    // from `submitted` itself so the button still reads the real submit-state label.
    val fieldsEnabled = !submitted && !isLiveChat
    val canSubmit = fieldsEnabled && formState.uploadingFields.isEmpty()

    Column {
        content.fields.forEach { field ->
            val value = formState.values[field.index].orEmpty()
            val error = if (formState.attemptedSubmit) FormFieldValidator.validate(field, value) else null
            val label = buildString {
                append(field.label ?: field.placeholder ?: "Field ${field.index + 1}")
                if (field.isRequired) append(" *")
            }
            when (field.type) {
                BotContent.Form.FieldType.SELECT -> {
                    Text(
                        text = label,
                        fontFamily = typography.textFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    field.options.forEach { option ->
                        QuickReplyButton(
                            text = option,
                            enabled = fieldsEnabled,
                            selected = value == option,
                            onClick = { onFieldChange(field.index, option) },
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                BotContent.Form.FieldType.DATE -> {
                    Text(label, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    var showDatePicker by remember { mutableStateOf(false) }
                    val dateRules = field.validation?.dateRules
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = fieldsEnabled,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(value.ifBlank { field.placeholder ?: "Select a date" }, fontFamily = typography.textFamily)
                    }
                    if (showDatePicker) {
                        val pickerState = rememberDatePickerState(
                            selectableDates = object : SelectableDates {
                                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                                    return dateRules == null || !DateAvailability.isDisabled(dateRules, date)
                                }
                            },
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    pickerState.selectedDateMillis?.let { millis ->
                                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                        onFieldChange(field.index, DateAvailability.format(date, dateRules?.dateFormat ?: "DD MMM YYYY"))
                                    }
                                    showDatePicker = false
                                }) { Text("OK") }
                            },
                            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
                        ) {
                            DatePicker(state = pickerState)
                        }
                    }
                }
                BotContent.Form.FieldType.MEDIA -> {
                    Text(label, fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    val uploading = field.index in formState.uploadingFields
                    val fileName = formState.fileNames[field.index]
                    val pickFile = rememberAttachmentPicker { payload ->
                        onMediaFieldPicked(field.index, payload.bytes, payload.fileName, payload.mimeType)
                    }
                    OutlinedButton(
                        onClick = pickFile,
                        enabled = fieldsEnabled && !uploading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when {
                                uploading -> "Uploading…"
                                !fileName.isNullOrBlank() -> fileName
                                else -> "Choose file"
                            },
                            fontFamily = typography.textFamily,
                        )
                    }
                }
                BotContent.Form.FieldType.OTHER -> {
                    Text(
                        text = "$label (unsupported field type)",
                        fontFamily = typography.textFamily,
                        fontSize = 13.sp,
                        color = colors.textDisabled,
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onFieldChange(field.index, it) },
                        label = { Text(label, fontFamily = typography.textFamily, fontSize = 13.sp) },
                        placeholder = field.placeholder?.let { { Text(it, fontFamily = typography.textFamily) } },
                        enabled = fieldsEnabled,
                        singleLine = true,
                        isError = error != null,
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = field.type.toKeyboardType()),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.inputBorder,
                            errorBorderColor = ErrorColor,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            error?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, fontFamily = typography.textFamily, fontSize = 12.sp, color = ErrorColor)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.accentContrast,
                disabledContainerColor = colors.textDisabled,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (submitted) "Submitted" else content.submitButtonText,
                fontFamily = typography.textFamily,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun BotContent.Form.FieldType.toKeyboardType(): KeyboardType = when (this) {
    BotContent.Form.FieldType.EMAIL -> KeyboardType.Email
    BotContent.Form.FieldType.PHONE -> KeyboardType.Phone
    BotContent.Form.FieldType.NUMBER -> KeyboardType.Number
    else -> KeyboardType.Text
}
