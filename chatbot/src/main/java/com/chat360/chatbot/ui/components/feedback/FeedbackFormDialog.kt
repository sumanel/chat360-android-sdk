package com.chat360.chatbot.ui.components.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chat360.chatbot.model.wire.FeedbackConfig
import com.chat360.chatbot.model.wire.activeForm
import com.chat360.chatbot.model.wire.normalizeFeedbackFieldType
import com.chat360.chatbot.model.wire.normalizeFeedbackOptions
import com.chat360.chatbot.model.wire.resolveFeedbackFormId
import com.chat360.chatbot.ui.components.chrome.ActiveRed
import com.chat360.chatbot.ui.components.icons.CheckIcon
import com.chat360.chatbot.ui.components.icons.StarIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

private const val DEFAULT_RATING = 5
private val DialogCornerRadius = RoundedCornerShape(20.dp)
private val FieldCornerRadius = RoundedCornerShape(10.dp)

/** Field types rendered directly - checkbox-matrix/radio-matrix/rank/file fall back to a plain notice (see [UnsupportedFieldNotice]). */
private val SUPPORTED_FIELD_TYPES = setOf("label", "textarea", "textbox", "dropdown", "radio", "checkbox", "date")

/**
 * The post-chat configurable survey: a rating (star/emoji) picks which
 * [FeedbackConfig.trigger_rules] form applies, then that form's fields render below it.
 * [onSubmit] receives the rating and every field's value already concatenated into one text
 * blob - a separate structured per-field map is never sent.
 */
@Composable
fun FeedbackFormDialog(
    feedbackConfig: FeedbackConfig,
    onSubmit: (rating: Int?, feedbackText: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current

    val showDefaultForm = feedbackConfig.show_default_form
    val defaultRating = feedbackConfig.rating?.scale?.takeIf { it > 0 } ?: DEFAULT_RATING
    // Initial state is asymmetric: showing the default form starts with no rating picked yet;
    // relying on trigger_rules instead starts with the top rating pre-selected.
    var rating by remember { mutableStateOf(if (showDefaultForm) null else defaultRating) }
    var attemptedSubmit by remember { mutableStateOf(false) }
    val values = remember { mutableStateMapOf<String, String>() }
    val checkboxValues = remember { mutableStateMapOf<String, List<String>>() }

    val activeFormId = when {
        showDefaultForm -> "default"
        feedbackConfig.trigger_rules.isEmpty() -> null
        else -> resolveFeedbackFormId(rating ?: DEFAULT_RATING, feedbackConfig.trigger_rules)
    }
    val activeForm = activeFormId?.let { feedbackConfig.activeForm(it) }
    val isNoneForm = activeFormId == "none"
    val displayTitle = activeForm?.widgetTitle?.trim()?.takeIf { it.isNotBlank() }
        ?: activeForm?.title?.trim()?.takeIf { it.isNotBlank() }
    val showFormContent = activeForm != null &&
        (showDefaultForm || (!isNoneForm && (displayTitle != null || activeForm.fields.isNotEmpty())))

    fun isMissingRequired(fieldId: String, type: String, required: Boolean): Boolean {
        if (!required) return false
        return when (type) {
            "checkbox" -> checkboxValues[fieldId].isNullOrEmpty()
            else -> values[fieldId].isNullOrBlank()
        }
    }

    fun buildFeedbackText(): String {
        val fields = activeForm?.fields.orEmpty()
        return fields.mapNotNull { field ->
            val type = normalizeFeedbackFieldType(field.type)
            when (type) {
                "label" -> null
                "checkbox" -> checkboxValues[field.id]?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                else -> values[field.id]?.takeIf { it.isNotBlank() }
            }
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    fun hasValidationErrors(): Boolean {
        val fields = activeForm?.fields.orEmpty()
        return fields.any { field ->
            val type = normalizeFeedbackFieldType(field.type)
            SUPPORTED_FIELD_TYPES.contains(type) && isMissingRequired(field.id, type, field.required)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 16.dp)
                .background(colors.backgroundElevated, DialogCornerRadius)
                .border(1.dp, colors.line, DialogCornerRadius)
                .padding(20.dp),
        ) {
            Text(
                text = "How was your experience?",
                fontFamily = typography.textFamily,
                fontSize = 16.sp,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.size(14.dp))
            RatingRow(
                style = feedbackConfig.rating?.style ?: "star",
                scale = defaultRating,
                selected = rating,
                onSelect = { rating = it },
            )
            Spacer(modifier = Modifier.size(16.dp))

            if (showFormContent) {
                displayTitle?.let {
                    Text(
                        text = it,
                        fontFamily = typography.textFamily,
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(activeForm!!.fields) { field ->
                        FeedbackFieldRow(
                            field = field,
                            value = values[field.id].orEmpty(),
                            checkboxValue = checkboxValues[field.id].orEmpty(),
                            showError = attemptedSubmit,
                            onValueChange = { values[field.id] = it },
                            onCheckboxChange = { checkboxValues[field.id] = it },
                        )
                    }
                }
                Spacer(modifier = Modifier.size(16.dp))
            }

            Button(
                onClick = {
                    attemptedSubmit = true
                    if (hasValidationErrors()) return@Button
                    onSubmit(rating, buildFeedbackText())
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

@Composable
private fun RatingRow(style: String, scale: Int, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = LocalChat360Colors.current
    Row {
        if (style == "star") {
            repeat(scale) { index ->
                val filled = selected != null && index < selected
                Icon(
                    imageVector = StarIcon,
                    contentDescription = "Rate ${index + 1}",
                    tint = if (filled) colors.accent else colors.line,
                    modifier = Modifier.padding(end = 4.dp).size(28.dp).clickable { onSelect(index + 1) },
                )
            }
        } else {
            listOf("😞", "😕", "😐", "😊", "🤩").forEachIndexed { index, emoji ->
                val isSelected = selected == index + 1
                Text(
                    text = emoji,
                    fontSize = if (isSelected) 28.sp else 24.sp,
                    modifier = Modifier.padding(end = 6.dp).clickable { onSelect(index + 1) },
                )
            }
        }
    }
}

@Composable
private fun FeedbackFieldRow(
    field: FeedbackConfig.FeedbackField,
    value: String,
    checkboxValue: List<String>,
    showError: Boolean,
    onValueChange: (String) -> Unit,
    onCheckboxChange: (List<String>) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val type = normalizeFeedbackFieldType(field.type)
    val hasError = showError && field.required && when (type) {
        "checkbox" -> checkboxValue.isEmpty()
        else -> value.isBlank()
    }

    Column {
        if (type == "label") {
            Text(field.label.orEmpty(), fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
            return
        }
        field.label?.let {
            Text(
                text = it + if (field.required) " *" else "",
                fontFamily = typography.textFamily,
                fontSize = 13.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        when (type) {
            "textarea", "textbox" -> BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.inputBackground, FieldCornerRadius)
                    .border(1.dp, colors.inputBorder, FieldCornerRadius)
                    .padding(12.dp),
            )
            "date" -> BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.inputBackground, FieldCornerRadius)
                    .border(1.dp, colors.inputBorder, FieldCornerRadius)
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(field.placeholder ?: "DD-MM-YYYY", fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textSecondary)
                    }
                    inner()
                },
            )
            // Rendered as a single-select list rather than a dropdown menu, to avoid pulling in
            // Compose's dropdown-menu machinery for one field type; the label doubles as the
            // value, and exactly one option can be picked.
            "dropdown" -> {
                val options = normalizeFeedbackOptions(field.options)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onValueChange(option.label) },
                        ) {
                            RadioButton(
                                selected = value == option.label,
                                onClick = { onValueChange(option.label) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary),
                            )
                            Text(option.label, fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
            "radio" -> {
                val options = normalizeFeedbackOptions(field.options)
                Column {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onValueChange(option.value) },
                        ) {
                            RadioButton(
                                selected = value == option.value,
                                onClick = { onValueChange(option.value) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.textSecondary),
                            )
                            Text(option.label, fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
            "checkbox" -> {
                val options = normalizeFeedbackOptions(field.options)
                Column {
                    options.forEach { option ->
                        val checked = checkboxValue.contains(option.value)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onCheckboxChange(if (checked) checkboxValue - option.value else checkboxValue + option.value)
                            },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(if (checked) colors.accent else colors.inputBackground, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (checked) Icon(imageVector = CheckIcon, contentDescription = null, tint = colors.accentContrast, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(option.label, fontFamily = typography.textFamily, fontSize = 14.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
            else -> UnsupportedFieldNotice(type)
        }
        if (hasError) {
            Text(
                text = "This field is required",
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                color = ActiveRed,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** checkbox-matrix/radio-matrix/rank/file aren't supported - an honest placeholder beats silently dropping the field. */
@Composable
private fun UnsupportedFieldNotice(type: String) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Text(
        text = "This field type ($type) isn't supported in the app yet.",
        fontFamily = typography.textFamily,
        fontSize = 12.sp,
        color = colors.textSecondary,
    )
}
