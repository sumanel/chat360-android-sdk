package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/**
 * [assignedAgent] mirrors `Header/index.tsx`'s name/status swap once a human agent is assigned -
 * this header has no persistent bot-name/avatar area to swap (unlike the widget's), so it's
 * surfaced as a status line instead; the per-message name/avatar swap lives in [BotMessageRow].
 *
 * No menu/new-chat icons here (dropped a non-functional hamburger + plus): chat360 is
 * single-room-per-session with no multi-conversation history endpoint, so there was nothing
 * real to wire them to.
 */
@Composable
fun HeaderBar(connected: Boolean, assignedAgent: AssignedAgent? = null) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val agentName = assignedAgent?.name?.takeIf { it.isNotBlank() }
        when {
            !connected -> Text(
                text = "connecting…",
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
            agentName != null -> Text(
                text = "Connected with $agentName",
                fontFamily = typography.textFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
        }
    }
}
