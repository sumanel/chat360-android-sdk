package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.config.LocalChat360UIConfig
import com.chat360.chatbot.ui.components.common.LogoBadge
import com.chat360.chatbot.ui.theme.LocalChat360Branding
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

@Composable
fun TypingIndicatorRow() {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val botTitle = LocalChat360Branding.current.botTitle
    val showBotAvatar = LocalChat360UIConfig.current.features.showBotAvatar
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBotAvatar) {
            LogoBadge(size = 28.dp)
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = "$botTitle is typing…",
            fontFamily = typography.textFamily,
            fontSize = 13.sp,
            color = colors.textSecondary,
        )
    }
}
