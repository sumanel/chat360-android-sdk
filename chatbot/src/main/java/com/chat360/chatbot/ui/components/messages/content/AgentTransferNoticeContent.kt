package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.ui.theme.LocalChat360Typography

private val LiveDotColor = Color(0xFF22C55E)

/** Ports AgentTransferMessage/index.tsx: a small pill announcing the handoff to a human agent. */
@Composable
fun AgentTransferNoticeContent(text: String) {
    val typography = LocalChat360Typography.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0x1522C55E), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(modifier = Modifier.padding(end = 6.dp).size(6.dp).background(LiveDotColor, CircleShape))
        Text(
            text = text.ifBlank { "You are now connected with an agent." },
            fontFamily = typography.textFamily,
            fontSize = 12.sp,
            color = LiveDotColor,
        )
    }
}
