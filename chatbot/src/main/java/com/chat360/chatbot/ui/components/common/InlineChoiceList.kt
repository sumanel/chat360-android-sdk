package com.chat360.chatbot.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * Choice list rendered as underlined, comma-separated text that flows inline rather than a
 * stack of pill-shaped buttons. Used by MULTI_CHOICE, AUTOSUGGESTION and MULTI_OPTION nodes.
 *
 * Each label gets its own padded, independently clickable region (rather than one clickable
 * word butted up against the next) so adjacent options don't share a touch target and short
 * labels stay easy to tap.
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
    FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = isSelected(index)
            val textColor = when {
                selected -> colors.accent
                enabled -> colors.textSecondary
                else -> colors.textDisabled
            }
            Text(
                text = if (index < labels.lastIndex) "$label," else label,
                fontFamily = typography.textFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = textColor,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(enabled = enabled) { onSelect(index) }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            )
        }
    }
}
