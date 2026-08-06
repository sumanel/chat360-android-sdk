package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatHistorySidebar(
    onDismiss: () -> Unit,
    onNewChat: () -> Unit,
    isTrainingMode: Boolean,
    onAssistantModeChanged: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    showAssistantMode: Boolean,
    showAppearanceSwitcher: Boolean,
    conversations: List<CachedConversationEntity>,
    onConversationSelected: (String) -> Unit,
    onConversationRenamed: (String, String) -> Unit,
    onConversationDeleted: (String) -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.8f)
            .background(colors.backgroundElevated),
    ) {
        // Dismiss Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .border(1.dp, colors.line)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("<", fontSize = 24.sp, color = colors.accent)
            Spacer(Modifier.width(12.dp))
            Text(
                "Menu",
                fontFamily = typography.textFamily,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent
            )
        }

        // Main Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                "AI Chatbot - History",
                fontFamily = typography.textFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp)
                    .background(colors.accent)
                    .clickable(onClick = onNewChat),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", fontSize = 25.sp, color = colors.accentContrast)
                Spacer(Modifier.width(12.dp))
                Text(
                    "New chat",
                    fontFamily = typography.textFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accentContrast
                )
            }
            Spacer(Modifier.height(28.dp))

            if (conversations.isEmpty()) {
                Text(
                    "No saved conversations yet",
                    fontFamily = typography.textFamily,
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
            } else {
                HistoryGroup(
                    title = "CONVERSATIONS",
                    items = conversations,
                    onConversationSelected = onConversationSelected,
                    onConversationRenamed = onConversationRenamed,
                    onConversationDeleted = onConversationDeleted,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Bottom Settings Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.line)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            if (showAssistantMode) {
                Text(
                    "ASSISTANT MODE",
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    ModeOption("Training", Modifier.weight(1f), isTrainingMode) { onAssistantModeChanged(true) }
                    ModeOption("Customer", Modifier.weight(1f), !isTrainingMode) { onAssistantModeChanged(false) }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (showAppearanceSwitcher) {
                Text(
                    "APPEARANCE",
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    ModeOption("Light", Modifier.weight(1f), !isDarkTheme) { onThemeChanged(false) }
                    ModeOption("Dark", Modifier.weight(1f), isDarkTheme) { onThemeChanged(true) }
                }
            }
        }
    }
}

@Composable
private fun HistoryGroup(
    title: String,
    items: List<CachedConversationEntity>,
    onConversationSelected: (String) -> Unit,
    onConversationRenamed: (String, String) -> Unit,
    onConversationDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current

    Column(modifier = modifier) {
        Text(
            title,
            fontFamily = typography.textFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = items,
                key = { it.id }
            ) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    onSelected = { onConversationSelected(conversation.id) },
                    onRenamed = { onConversationRenamed(conversation.id, it) },
                    onDeleted = { onConversationDeleted(conversation.id) },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: CachedConversationEntity,
    onSelected: () -> Unit,
    onRenamed: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val destructiveColor = Color(0xFFB3261E)
    var showActions by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var title by remember(conversation.id, conversation.title) { mutableStateOf(conversation.title) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onSelected, onLongClick = { showActions = true })
            .padding(bottom = 22.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("o", fontSize = 19.sp, color = colors.textSecondary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(conversation.title, fontFamily = typography.textFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(conversation.updatedAt)), fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
        }
        Column {
            Text("...", fontSize = 16.sp, color = colors.textSecondary, modifier = Modifier.clickable { showActions = true }.padding(horizontal = 8.dp))
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { showActions = false; showRenameDialog = true })
                DropdownMenuItem(text = { Text("Delete", color = destructiveColor) }, onClick = { showActions = false; showDeleteDialog = true })
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename conversation") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Conversation name") }) },
            confirmButton = { TextButton(onClick = { onRenamed(title); showRenameDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete conversation?") },
            text = { Text("This removes the conversation and its cached messages from this device.") },
            confirmButton = { TextButton(onClick = { onDeleted(); showDeleteDialog = false }) { Text("Delete", color = destructiveColor) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ModeOption(
    text: String,
    modifier: Modifier,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Text(
        text = text,
        fontFamily = typography.textFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) colors.accent else colors.textPrimary,
        modifier = modifier
            .background(if (selected) colors.backgroundElevated else colors.backgroundSunken)
            .then(if (selected) Modifier.border(1.dp, colors.line, RoundedCornerShape(0.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
