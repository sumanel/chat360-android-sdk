package com.chat360.chatbot.ui.components.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Pill-shaped choice button used by MULTI_CHOICE nodes (and any future button-based content). */
@Composable
fun QuickReplyButton(text: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val borderColor = when {
        selected -> colors.accent
        enabled -> colors.inputBorder
        else -> colors.line
    }
    val textColor = when {
        selected -> colors.accent
        enabled -> colors.textPrimary
        else -> colors.textDisabled
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = typography.textFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = textColor,
        )
    }
}
