package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.ui.components.icons.AddIcon
import com.chat360.chatbot.ui.components.icons.MenuIcon
import com.chat360.chatbot.ui.components.icons.RefreshIcon
import com.chat360.chatbot.ui.components.icons.ShortcutIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import kotlinx.coroutines.delay

@Composable
fun HeaderBar(
    connected: Boolean,
    assignedAgent: AssignedAgent? = null,
    showMenu: Boolean = true,
    showNewChat: Boolean = true,
    newChatEnabled: Boolean = true,
    onMenuClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    /** label -> targetId - the shortcuts button only shows when this is non-empty. */
    shortcuts: Map<String, String> = emptyMap(),
    onShortcutSelected: (targetId: String, label: String) -> Unit = { _, _ -> },
    onRefreshClick: () -> Unit = {},
    sessionCreatedAtMs: Long? = null,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    var showShortcutsMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 18.dp),
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
        // Most useful once the socket has actually dropped, but left tappable regardless so a
        // host app always has a manual escape hatch.
        // Spacer(modifier = Modifier.width(16.dp))
        // Icon(
        //     imageVector = RefreshIcon,
        //     contentDescription = "Refresh chat",
        //     tint = if (connected) colors.textSecondary else colors.textPrimary,
        //     modifier = Modifier
        //         .size(22.dp)
        //         .clickable(onClick = onRefreshClick),
        // )
        if (shortcuts.isNotEmpty()) {
            Spacer(modifier = Modifier.width(16.dp))
            Box {
                Icon(
                    imageVector = ShortcutIcon,
                    contentDescription = "Shortcuts",
                    tint = colors.textPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { showShortcutsMenu = true },
                )
                DropdownMenu(expanded = showShortcutsMenu, onDismissRequest = { showShortcutsMenu = false }) {
                    shortcuts.forEach { (label, targetId) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                showShortcutsMenu = false
                                onShortcutSelected(targetId, label)
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (sessionCreatedAtMs != null) {
            val remainingText by produceState(initialValue = formatSessionCountdown(sessionCreatedAtMs), sessionCreatedAtMs) {
                while (true) {
                    value = formatSessionCountdown(sessionCreatedAtMs)
                    delay(1_000)
                }
            }
            Text(
                text = remainingText,
                color = colors.textPrimary,
                fontFamily = typography.textFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        if (showNewChat) Icon(
            imageVector = AddIcon,
            contentDescription = "Start new chat",
            tint = if (newChatEnabled) colors.textPrimary else colors.textDisabled,
            modifier = Modifier
                .size(24.dp)
                .clickable(enabled = newChatEnabled, onClick = onNewChatClick),
        )
    }
}

private fun formatSessionCountdown(createdAtMs: Long): String {
    val elapsedSeconds = ((System.currentTimeMillis() - createdAtMs) / 1000).coerceAtLeast(0)
    val remainingSeconds = 3_599 - (elapsedSeconds % 3_600)
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}
