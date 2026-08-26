package com.chat360.chatbot.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * Choice list rendered as underlined, comma-separated text that flows inline rather than a
 * stack of pill-shaped buttons. Used by MULTI_CHOICE and AUTOSUGGESTION nodes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InlineChoiceList(
    labels: List<String>,
    enabled: Boolean,
    isSelected: (index: Int) -> Boolean,
    onSelect: (index: Int) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    FlowRow {
        labels.forEachIndexed { index, label ->
            val selected = isSelected(index)
            val textColor = when {
                selected -> colors.accent
                enabled -> colors.textPrimary
                else -> colors.textDisabled
            }
            Text(
                text = label,
                fontFamily = typography.textFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = textColor,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(enabled = enabled) { onSelect(index) },
            )
            if (index < labels.lastIndex) {
                Text(
                    text = ", ",
                    fontFamily = typography.textFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (enabled) colors.textPrimary else colors.textDisabled,
                )
            }
        }
    }
}
