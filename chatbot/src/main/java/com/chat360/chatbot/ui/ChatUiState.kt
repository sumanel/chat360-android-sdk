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

/**
 * `content` is what content/{...} dispatches on to pick a renderer - adding a new bot content
 * type is "one new BotContent subtype + one new content composable + one `when` branch", not a
 * new field here. `text` stays separate because user messages (which have no BotContent) and
 * plain-text bot nodes both need a display string without a dedicated content type for it.
 */
/** Local (not server-echoed) state for a user-initiated file upload, keyed by [ChatMessage.id]. */
data class Attachment(
    val fileName: String,
    val progress: Int = 0,
    val uploaded: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Local draft state for a BotContent.Form message: field index -> typed value, plus submitted
 * flag. [attemptedSubmit] gates inline error text - like the widget's Mantine form, errors only
 * show after the user has tried to submit once, not while a field is merely empty/untouched.
 */
data class FormState(
    val values: Map<Int, String> = emptyMap(),
    val submitted: Boolean = false,
    val attemptedSubmit: Boolean = false,
    /** MEDIA fields only: field index -> the picked file's original name (values[index] holds the uploaded URL). */
    val fileNames: Map<Int, String> = emptyMap(),
    /** MEDIA fields only: field indices with an upload currently in flight. */
    val uploadingFields: Set<Int> = emptySet(),
)

/** Local draft state for EmailPrompt (uses [value] only) / PhonePrompt (country code + national number). */
data class PromptState(
    val value: String = "",
    val secondaryValue: String = "",
    val submitted: Boolean = false,
)

/**
 * A recorded-but-not-yet-sent voice note - mirrors UserInput/index.tsx's `voiceMessage` state.
 * [amplitudes] are the raw 0-32767 samples captured while recording, reused to draw the static
 * waveform in both the review chip and (once sent) the sent bubble, since decoding an m4a's real
 * waveform client-side has no cheap equivalent here.
 */
data class VoiceDraftState(
    val filePath: String,
    val amplitudes: List<Int>,
    val durationMs: Long,
    val uploading: Boolean = false,
    val uploadProgress: Int = 0,
    val error: String? = null,
)

/** A sent (or sending) voice message bubble - [localFilePath] answers playback instantly, before/without [remoteUrl]. */
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
    /** Which MultiOption indices are currently checked - unlike selectedReplyIndex, more than one. */
    val checkedIndices: Set<Int> = emptySet(),
    /** Set only for a chatgpt_message streaming bubble - later chunks sharing this id merge into it. */
    val streamId: String? = null,
    /** BOT vs AGENT-authored (an admin/operator message) - drives the row's name/avatar swap. */
    val author: BotNode.MessageAuthor = BotNode.MessageAuthor.BOT,
    /** Set only for a sent VOICE_MESSAGE - mutually exclusive with [attachment]/[text] rendering. */
    val voiceMessage: VoiceMessageInfo? = null,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isConnected: Boolean = false,
    val isAgentTyping: Boolean = false,
    val isSlowConnection: Boolean = false,
    val inputText: String = "",
    val error: String? = null,
    /** Server-driven "other values fetched from the API" layer, applied on top of the chosen theme preset. */
    val colorOverrides: Chat360ColorOverrides? = null,
    val logoOverride: Chat360Logo? = null,
    val botTitleOverride: String? = null,
    /** An END node's urlMessage, to be opened externally once by the UI then cleared. */
    val pendingUrlToOpen: String? = null,
    /**
     * True while the session is being handled by a human agent rather than the bot flow - set on
     * any admin/operator-authored message (not just an AGENT_TRANSFER/TEAM_TRANSFER notice), and
     * cleared on the next bot-authored message or an `update_status` end signal. See
     * ChatViewModel's authorship-flip logic and its documented simplification vs. source.
     */
    val isLiveChat: Boolean = false,
    /** The human agent currently assigned, from a `highlight` node - re-updatable for agent-to-agent transfer. */
    val assignedAgent: AssignedAgent? = null,
    /** A stopped-but-unsent recording awaiting review/send/cancel - swaps ChatInputBar out for VoiceRecorderBar. */
    val voiceDraft: VoiceDraftState? = null,
    /** The bot-owner-authored post-chat survey definition, from the appearance API's json_info. */
    val feedbackConfig: FeedbackConfig? = null,
    /** True once `shouldAskFeedback` held the session open pending a submitted (or skipped) survey. */
    val showFeedbackPrompt: Boolean = false,
    /** A `chat_inactivity_message` with `auto_archival` - mirrors innerNotification?.autoArchive gating showInputBox()/showVoiceInput() in the widget: the session is closing for inactivity, so the input bar disables. */
    val isArchived: Boolean = false,
)
