package com.chat360.chatbot.ui

import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.model.wire.BotNode
import com.chat360.chatbot.model.wire.FeedbackConfig
import com.chat360.chatbot.ui.theme.Chat360ColorOverrides
import com.chat360.chatbot.ui.theme.Chat360Logo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Attachment(
    val fileName: String,
    val progress: Int = 0,
    val uploaded: Boolean = false,
    val failed: Boolean = false,
)

data class FormState(
    val values: Map<Int, String> = emptyMap(),
    val submitted: Boolean = false,
    val attemptedSubmit: Boolean = false,
    val fileNames: Map<Int, String> = emptyMap(),
    val uploadingFields: Set<Int> = emptySet(),
)

data class PromptState(
    val value: String = "",
    val secondaryValue: String = "",
    val submitted: Boolean = false,
)

data class VoiceDraftState(
    val filePath: String,
    val amplitudes: List<Int>,
    val durationMs: Long,
    val uploading: Boolean = false,
    val uploadProgress: Int = 0,
    val error: String? = null,
)

data class VoiceMessageInfo(
    val localFilePath: String?,
    val remoteUrl: String?,
    val amplitudes: List<Int>,
    val durationMs: Long,
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val chatMsgId: String? = null,
    val text: String,
    val fromUser: Boolean,
    val failed: Boolean = false,
    val timeText: String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
    val content: BotContent = BotContent.PlainText,
    val repliesEnabled: Boolean = true,
    val selectedReplyIndex: Int? = null,
    val attachment: Attachment? = null,
    val formState: FormState? = null,
    val promptState: PromptState? = null,
    val checkedIndices: Set<Int> = emptySet(),
    val streamId: String? = null,
    val author: BotNode.MessageAuthor = BotNode.MessageAuthor.BOT,
    val voiceMessage: VoiceMessageInfo? = null,
    val liked: Boolean? = null,
    val cacheRowId: Long? = null,
)

fun formatMessageTime(timestampMs: Long? = null): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestampMs?.let(::Date) ?: Date())

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isConnected: Boolean = false,
    val isAgentTyping: Boolean = false,
    val isSlowConnection: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
    val colorOverrides: Chat360ColorOverrides? = null,
    val logoOverride: Chat360Logo? = null,
    val botTitleOverride: String? = null,
    val pendingUrlToOpen: String? = null,
    val isLiveChat: Boolean = false,
    val assignedAgent: AssignedAgent? = null,
    val voiceDraft: VoiceDraftState? = null,
    val feedbackConfig: FeedbackConfig? = null,
    val showFeedbackPrompt: Boolean = false,
    val showPeriodicFeedbackPrompt: Boolean = false,
    val isArchived: Boolean = false,
    val hasMoreHistory: Boolean = false,
    val isLoadingMoreHistory: Boolean = false,
    val isHistoryUnavailable: Boolean = false,
    val activeConversationId: String? = null,
    val sessionCreatedAtMs: Long? = null,
)
