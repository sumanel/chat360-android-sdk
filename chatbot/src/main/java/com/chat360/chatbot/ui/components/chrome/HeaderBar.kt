package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.ui.components.icons.AddIcon
import com.chat360.chatbot.ui.components.icons.MenuIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Minimal utility bar matching the embedded-widget chrome. */
@Composable
fun HeaderBar(
    connected: Boolean,
    assignedAgent: AssignedAgent? = null,
    showMenu: Boolean = true,
    showNewChat: Boolean = true,
    onMenuClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenu) Icon(
            imageVector = MenuIcon,
            contentDescription = "Open menu",
            tint = colors.textPrimary,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onMenuClick),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showNewChat) Icon(
            imageVector = AddIcon,
            contentDescription = "Start new chat",
            tint = colors.textPrimary,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onNewChatClick),
        )
    }
}
