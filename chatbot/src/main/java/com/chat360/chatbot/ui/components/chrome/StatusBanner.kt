package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Shared "error/destructive" red, reused for failed-message text. */
val ActiveRed = Color(0xFFE63312)

@Composable
fun StatusBanner(text: String, emphasized: Boolean) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Text(
        text = text,
        fontFamily = typography.textFamily,
        fontSize = 13.sp,
        color = if (emphasized) ActiveRed else colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.backgroundElevated)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
