package com.chat360.chatbot.ui.components.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.network.rest.dto.SessionLanguage
import com.chat360.chatbot.ui.components.icons.AddIcon
import com.chat360.chatbot.ui.components.icons.ChevronLeftIcon
import com.chat360.chatbot.ui.components.icons.DarkModeIcon
import com.chat360.chatbot.ui.components.icons.ChatBubbleIcon
import com.chat360.chatbot.ui.components.icons.LightModeIcon
import com.chat360.chatbot.ui.components.icons.MoreIcon
import com.chat360.chatbot.ui.components.icons.PersonIcon
import com.chat360.chatbot.ui.components.icons.TrainingIcon
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography
import java.text.SimpleDateFormat
import java.util.Calendar
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
    trainingModeEnabled: Boolean = true,
    showAppearanceSwitcher: Boolean,
    conversations: List<CachedConversationEntity>,
    activeConversationId: String? = null,
    onConversationSelected: (String) -> Unit,
    onConversationRenamed: (String, String) -> Unit,
    onConversationDeleted: (String) -> Unit = {},
    languages: List<SessionLanguage> = emptyList(),
    onLanguageSelected: (String) -> Unit = {},
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
            Icon(ChevronLeftIcon, contentDescription = "Close menu", tint = colors.accent)
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
                Icon(AddIcon, contentDescription = null, tint = colors.accentContrast)
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
                HistoryTimeline(
                    conversations = conversations,
                    activeConversationId = activeConversationId,
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
                    "Assistant Mode",
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    ModeOption(
                        text = "Training",
                        icon = TrainingIcon,
                        modifier = Modifier.weight(1f),
                        selected = isTrainingMode,
                        enabled = trainingModeEnabled,
                    ) { onAssistantModeChanged(true) }
                    ModeOption(
                        text = "Customer",
                        icon = PersonIcon,
                        modifier = Modifier.weight(1f),
                        selected = !isTrainingMode,
                    ) { onAssistantModeChanged(false) }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (showAppearanceSwitcher) {
                Text(
                    "Appearance",
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    ModeOption("Light", LightModeIcon, Modifier.weight(1f), !isDarkTheme) { onThemeChanged(false) }
                    ModeOption("Dark", DarkModeIcon, Modifier.weight(1f), isDarkTheme) { onThemeChanged(true) }
                }
            }
            if (languages.size > 1) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "LANGUAGE",
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    languages.forEach { language ->
                        LanguageChip(language.value, language.default) { onLanguageSelected(language.key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (selected) colors.backgroundElevated else colors.backgroundSunken)
            .then(if (selected) Modifier.border(1.dp, colors.line, RoundedCornerShape(0.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            fontFamily = typography.textFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.accent else colors.textPrimary,
        )
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Buckets conversations into timeline sections (Today / Yesterday / Previous 7 Days / ...)
 * based on [CachedConversationEntity.createdAt], preserving each bucket's incoming order
 * (conversations already arrive newest-first from the repository).
 */
private fun groupConversationsByDate(
    conversations: List<CachedConversationEntity>
): List<Pair<String, List<CachedConversationEntity>>> {
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - DAY_MILLIS
    val last7Start = todayStart - 7 * DAY_MILLIS
    val last30Start = todayStart - 30 * DAY_MILLIS

    val buckets = linkedMapOf(
        "Today" to mutableListOf<CachedConversationEntity>(),
        "Yesterday" to mutableListOf(),
        "Previous 7 Days" to mutableListOf(),
        "Previous 30 Days" to mutableListOf(),
        "Older" to mutableListOf(),
    )

    conversations.forEach { conversation ->
        val bucketKey = when {
            conversation.createdAt >= todayStart -> "Today"
            conversation.createdAt >= yesterdayStart -> "Yesterday"
            conversation.createdAt >= last7Start -> "Previous 7 Days"
            conversation.createdAt >= last30Start -> "Previous 30 Days"
            else -> "Older"
        }
        buckets.getValue(bucketKey).add(conversation)
    }

    return buckets.filterValues { it.isNotEmpty() }.map { (label, items) -> label to items }
}

@Composable
private fun HistoryTimeline(
    conversations: List<CachedConversationEntity>,
    activeConversationId: String?,
    onConversationSelected: (String) -> Unit,
    onConversationRenamed: (String, String) -> Unit,
    onConversationDeleted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val groups = remember(conversations) { groupConversationsByDate(conversations) }

    LazyColumn(
        modifier = modifier.fillMaxWidth()
    ) {
        groups.forEach { (label, items) ->
            item(key = "header_$label") {
                Text(
                    label.uppercase(Locale.getDefault()),
                    fontFamily = typography.textFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )
            }
            items(
                items = items,
                key = { it.id }
            ) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    isActive = conversation.id == activeConversationId,
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
    isActive: Boolean,
    onSelected: () -> Unit,
    onRenamed: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    var showActions by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    // A conversation keeps its "New conversation" placeholder until the first message is sent;
    // show its date/time instead of that placeholder so the list never displays the literal text.
    val displayTitle = if (conversation.title == "New conversation") {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(conversation.createdAt))
    } else {
        conversation.title
    }
    var title by remember(conversation.id, displayTitle) { mutableStateOf(displayTitle) }
    val itemColor = if (isActive) colors.accent else colors.textPrimary

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isActive) colors.backgroundSunken else colors.backgroundElevated, RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onSelected, onLongClick = { showActions = true })
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(ChatBubbleIcon, contentDescription = null, tint = if (isActive) colors.accent else colors.textSecondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(displayTitle, fontFamily = typography.textFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = itemColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Shows when the conversation started, not its last activity - createdAt is
                // stable (set once on first insert; see ensureConversationPersisted /
                // replaceAgentRoomConversations' IGNORE-conflict insert), so this stays fixed
                // as new messages arrive, unlike updatedAt.
                Text(SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(conversation.createdAt)), fontFamily = typography.textFamily, fontSize = 13.sp, color = colors.textSecondary)
            }
            Column {
                IconButton(onClick = { showActions = true }) {
                    Icon(MoreIcon, contentDescription = "Conversation options", tint = colors.textSecondary)
                }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showActions = false; showRenameDialog = true })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showActions = false; showDeleteDialog = true })
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameConversationDialog(
            initialTitle = title,
            onConfirm = { newTitle -> title = newTitle; onRenamed(newTitle); showRenameDialog = false },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        DeleteConversationDialog(
            conversationTitle = displayTitle,
            onConfirm = { onDeleted(); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

private val ConversationDialogCornerRadius = RoundedCornerShape(20.dp)
private val ConversationDialogFieldCornerRadius = RoundedCornerShape(10.dp)

@Composable
private fun ConversationDialogShell(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(colors.backgroundElevated, ConversationDialogCornerRadius)
            .border(1.dp, colors.line, ConversationDialogCornerRadius)
            .padding(20.dp),
    ) {
        Text(
            text = title,
            fontFamily = typography.textFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun RenameConversationDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    var name by remember { mutableStateOf(initialTitle) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ConversationDialogShell(title = "Rename conversation") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Conversation name", fontFamily = typography.textFamily) },
                shape = ConversationDialogFieldCornerRadius,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.inputBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedLabelColor = colors.accent,
                    unfocusedLabelColor = colors.textSecondary,
                    cursorColor = colors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = ConversationDialogFieldCornerRadius,
                    border = BorderStroke(1.dp, colors.inputBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { onConfirm(name) },
                    shape = ConversationDialogFieldCornerRadius,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentContrast,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun DeleteConversationDialog(
    conversationTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ConversationDialogShell(title = "Delete conversation") {
            Text(
                text = "This can't be undone. Delete \"$conversationTitle\"?",
                fontFamily = typography.textFamily,
                fontSize = 14.sp,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = ConversationDialogFieldCornerRadius,
                    border = BorderStroke(1.dp, colors.inputBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onConfirm,
                    shape = ConversationDialogFieldCornerRadius,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActiveRed,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Delete", fontFamily = typography.textFamily, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val contentColor = when {
        !enabled -> colors.textSecondary
        selected -> colors.accent
        else -> colors.textPrimary
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(if (selected) colors.backgroundElevated else colors.backgroundSunken)
            .then(if (selected) Modifier.border(1.dp, colors.line, RoundedCornerShape(0.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontFamily = typography.textFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
    }
}
