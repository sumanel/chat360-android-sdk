package com.chat360.chatbot.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.cache.ChatCacheDatabase
import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.domain.ChatRepository
import com.chat360.chatbot.domain.validation.FormFieldValidator
import com.chat360.chatbot.domain.validation.InputValidators
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.model.wire.BotNode
import com.chat360.chatbot.model.wire.IncomingSocketEvent
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import com.chat360.chatbot.model.wire.toIncomingEvent
import com.chat360.chatbot.network.rest.Chat360ApiService
import com.chat360.chatbot.ui.theme.toColorOverrides
import com.chat360.chatbot.ui.theme.toLogoOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ChatViewModel(
    private val repository: ChatRepository,
    private val botId: String,
    private val cache: ChatCacheRepository,
    private val dealerRoomsApi: Chat360ApiService,
    private val employeeCode: String? = null,
    private val suppressInitialBotMessages: Boolean = false,
) : ViewModel() {

    private var hasStartedConversation = false

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _conversations = MutableStateFlow<List<CachedConversationEntity>>(emptyList())
    val conversations: StateFlow<List<CachedConversationEntity>> = _conversations.asStateFlow()
    private var activeConversationId: String? = null
    /** True only while Room entries are being replayed; cached messages are never “initial” UI. */
    private var restoringFromCache = false
    private val cacheJson = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch { cache.conversations(botId).collect { _conversations.value = it } }
        employeeCode?.let { code ->
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { dealerRoomsApi.getEmployeeRooms(code) }
                    .onSuccess { response ->
                        // Room emits first. The network snapshot then becomes visible immediately,
                        // followed by durable reconciliation back into Room.
                        val refreshed = cache.dealerRoomConversations(botId, response.results)
                        _conversations.value = refreshed
                        cache.syncDealerRooms(botId, refreshed)
                    }
                    .onFailure { error ->
                        Log.e("Sanket", "Sanket ===== Hyundai rooms request failed: ${error.message}", error)
                    }
            }
        }
        viewModelScope.launch {
            repository.connect(
                onEvent = ::handleEvent,
                onConnected = { _uiState.update { it.copy(isConnected = true, error = null) } },
                onError = { e ->
                    Log.e("Chat360", "Chat connection failed: ${e.message}", e)
                    _uiState.update { it.copy(isConnected = false) }
                },
                onSlowConnectionChanged = { slow -> _uiState.update { it.copy(isSlowConnection = slow) } },
                onMessageTimedOut = ::handleMessageTimedOut,
                onAppearanceLoaded = { details, chatboxName ->
                    _uiState.update {
                        it.copy(
                            colorOverrides = details?.toColorOverrides(),
                            logoOverride = details?.toLogoOverride(),
                            botTitleOverride = chatboxName?.takeIf { name -> name.isNotBlank() },
                            feedbackConfig = details?.feedback_config ?: it.feedbackConfig,
                        )
                    }
                },
                onConversationStarted = { roomId -> activateConversation(roomId) },
                onRawIncoming = ::cacheIncomingEnvelope,
                onOpenUrl = { url -> _uiState.update { it.copy(pendingUrlToOpen = url) } },
                onSessionResumed = { takeover, agent ->
                    _uiState.update { it.copy(isLiveChat = takeover, assignedAgent = agent ?: it.assignedAgent) }
                },
                onFeedbackRequested = { _uiState.update { it.copy(showFeedbackPrompt = true) } },
            )
        }
    }

    private fun handleEvent(event: IncomingSocketEvent) {
        when (event) {
            is IncomingSocketEvent.BotMessage -> {
                if (suppressInitialBotMessages && !hasStartedConversation && !restoringFromCache) return
                val node = event.node
                // An Unsupported node with no text at all renders nothing (yet) rather than an
                // empty bubble; one with fallback text (most node types include questionText)
                // still shows as plain text even before its specific renderer exists. A
                // WINDOW_EVENT node never renders a bubble at all (the widget marks it
                // isWithoutMessage/unStyled/hideAvatar) - it only drives the bridge.
                if (node.text == null && node.content is BotContent.Unsupported) return
                if (node.content is BotContent.WindowEvent) return
                // isLiveChat flips true on the transfer notice OR any admin/operator-authored
                // message (matches handleAdminOrEndUserMessage's flip condition), and - unlike
                // the source, which never resets it except via update_status - also flips back
                // false on the next bot-authored message. That's a deliberate simplification: it
                // makes replaying loaded history alone correctly reconstruct whether the session
                // is *currently* live (mirrors getHistory.ts's hasAdminMessages walk) without a
                // separate one-off scan, at the minor cost of diverging from source in the rare
                // case a bot message is ever injected mid-live-session.
                _uiState.update {
                    it.copy(
                        isLiveChat = when {
                            node.content is BotContent.AgentTransferNotice -> true
                            node.author == BotNode.MessageAuthor.AGENT -> true
                            else -> false
                        },
                    )
                }
                // A chatgpt_message streaming chunk concatenates onto the existing bubble sharing
                // its streamId instead of appending a new one; the flow stays paused (handled by
                // the always-open input bar already) until a chunk arrives with streamEnded=true.
                if (node.streamId != null) {
                    appendOrMergeStreamChunk(node)
                    return
                }
                appendMessage(
                    ChatMessage(
                        text = node.text.orEmpty(),
                        fromUser = false,
                        content = node.content,
                        author = node.author,
                        formState = if (node.content is BotContent.Form) FormState() else null,
                        promptState = if (
                            node.content is BotContent.EmailPrompt ||
                            node.content is BotContent.PhonePrompt ||
                            node.content is BotContent.DatePrompt ||
                            node.content is BotContent.TimePrompt
                        ) {
                            PromptState()
                        } else {
                            null
                        },
                    ), cacheUserMessage = false,
                )
            }
            is IncomingSocketEvent.TypingStatus -> _uiState.update { it.copy(isAgentTyping = event.isTyping) }
            is IncomingSocketEvent.CloseConnection -> _uiState.update { it.copy(isConnected = false) }
            is IncomingSocketEvent.AgentAssigned -> _uiState.update { it.copy(assignedAgent = event.agent) }
            is IncomingSocketEvent.LiveChatEnded -> _uiState.update { it.copy(isLiveChat = false) }
            is IncomingSocketEvent.InactivityNotice -> {
                if (event.message != null) appendMessage(ChatMessage(text = event.message, fromUser = false), cacheUserMessage = false)
                if (event.autoArchive) _uiState.update { it.copy(isArchived = true) }
            }
            // Ack/echoed-user/pong only drive ChatRepository's internal bookkeeping (ack-timeout
            // cancellation, heartbeat reset) - nothing further to reflect in the UI.
            else -> Unit
        }
    }

    /** Raw (unrendered) text accumulated so far per streamId - chunk text arrives raw (see the
     * chatgpt_message branches in IncomingEnvelope.kt) precisely so it can be concatenated as one
     * string before RichTextParser ever sees it: a chunk boundary can land inside an HTML tag, and
     * parsing each chunk individually would leave tag fragments as literal text. PlainTextContent
     * re-parses this same accumulated string on every recomposition, so no stripping happens here. */
    private val streamRawText = mutableMapOf<String, String>()

    private fun appendOrMergeStreamChunk(node: BotNode) {
        val streamId = node.streamId ?: return
        val mergedRaw = streamRawText.getOrDefault(streamId, "") + node.text.orEmpty()
        if (node.streamEnded) streamRawText.remove(streamId) else streamRawText[streamId] = mergedRaw
        _uiState.update { state ->
            val index = state.messages.indexOfLast { it.streamId == streamId }
            if (index >= 0) {
                val merged = state.messages[index].copy(text = mergedRaw)
                state.copy(messages = state.messages.toMutableList().apply { this[index] = merged })
            } else {
                state.copy(
                    messages = state.messages.map { it.copy(repliesEnabled = false) } +
                        ChatMessage(text = mergedRaw, fromUser = false, streamId = streamId),
                )
            }
        }
    }

    /** Clears the one-shot END-node URL after the UI has opened it. */
    fun clearPendingUrl() {
        _uiState.update { it.copy(pendingUrlToOpen = null) }
    }

    /** Appending any message locks the quick replies of everything before it (widget behavior). */
    private fun appendMessage(message: ChatMessage, cacheUserMessage: Boolean = message.fromUser) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { it.copy(repliesEnabled = false) } + message,
            )
        }
        if (cacheUserMessage) activeConversationId?.let { conversationId ->
            viewModelScope.launch { cache.cacheUserMessage(conversationId, message.text, message.chatMsgId) }
        }
    }

    fun selectQuickReply(messageId: String, option: BotContent.MultiChoice.Option) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!message.repliesEnabled) return
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(repliesEnabled = false, selectedReplyIndex = option.index) else it
                },
            )
        }
        val chatMsgId = repository.sendQuickReply(option)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = option.text, fromUser = true))
    }

    /** Answers a RATING node - disables the row and shows the value as the outgoing bubble. */
    fun selectRating(messageId: String, value: Int) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!message.repliesEnabled) return
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(repliesEnabled = false, selectedReplyIndex = value - 1) else it
                },
            )
        }
        val chatMsgId = repository.sendRating(value)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = value.toString(), fromUser = true))
    }

    /** Answers the AUTOSUGGESTION sub-case of CUSTOMINPUT - single tap selects and submits. */
    fun selectAutoSuggestion(messageId: String, index: Int, text: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!message.repliesEnabled) return
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(repliesEnabled = false, selectedReplyIndex = index) else it
                },
            )
        }
        val chatMsgId = repository.sendAutoSuggestion(text)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = text, fromUser = true))
    }

    fun updatePromptValue(messageId: String, primary: String, secondary: String = "") {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    val current = message.promptState ?: PromptState()
                    if (current.submitted) return@map message
                    message.copy(promptState = current.copy(value = primary, secondaryValue = secondary))
                },
            )
        }
    }

    /** Answers an EMAIL node - blocked (no-op) unless the current draft passes InputValidators, same rules the composable uses to enable/disable Submit. */
    fun submitEmail(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val prompt = message.promptState ?: return
        if (prompt.submitted) return
        val email = prompt.value.trim()
        if (email.isBlank() || InputValidators.validateTest(email) || !InputValidators.validateEmail(email)) return
        markPromptSubmitted(messageId)
        val chatMsgId = repository.sendEmail(email)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = email, fromUser = true))
    }

    /** Answers a PHONE node. International + splitVariable sends two variables; otherwise a single combined value. */
    fun submitPhone(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val content = message.content as? BotContent.PhonePrompt ?: return
        val prompt = message.promptState ?: return
        if (prompt.submitted) return
        val countryCode = prompt.value.trim()
        val nationalNumber = prompt.secondaryValue.trim()
        val combined = countryCode + nationalNumber
        if (countryCode.isBlank() || nationalNumber.isBlank() || !InputValidators.validatePhoneNumber(combined, international = true)) return
        markPromptSubmitted(messageId)
        val chatMsgId = if (content.splitVariable && content.countryCodeVar != null) {
            repository.sendSplitPhone(countryCode, nationalNumber, content.countryCodeVar)
        } else {
            repository.sendPhone(combined)
        }
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = combined, fromUser = true))
    }

    /** Answers a standalone DATE node - picking a date both fills and submits, single action. */
    fun selectDate(messageId: String, formattedDate: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val content = message.content as? BotContent.DatePrompt ?: return
        val prompt = message.promptState ?: return
        if (prompt.submitted) return
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(promptState = prompt.copy(value = formattedDate, submitted = true)) else it
                },
            )
        }
        val chatMsgId = repository.sendDate(formattedDate, content.rules.dateFormat)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = formattedDate, fromUser = true))
    }

    /** Answers a standalone TIME node. */
    fun submitTime(messageId: String, formattedTime: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val prompt = message.promptState ?: return
        if (prompt.submitted) return
        markPromptSubmitted(messageId)
        val chatMsgId = repository.sendTime(formattedTime)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = formattedTime, fromUser = true))
    }

    private fun markPromptSubmitted(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(promptState = it.promptState?.copy(submitted = true)) else it
                },
            )
        }
    }

    fun toggleCheckbox(messageId: String, index: Int) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id != messageId || !it.repliesEnabled) return@map it
                    val current = it.checkedIndices
                    it.copy(checkedIndices = if (index in current) current - index else current + index)
                },
            )
        }
    }

    fun submitCheckboxes(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val content = message.content as? BotContent.MultiOption ?: return
        if (!message.repliesEnabled || message.checkedIndices.isEmpty()) return
        val chatMsgId = repository.sendCheckboxOptions(content.options, message.checkedIndices)
        val text = content.options.filter { it.index in message.checkedIndices }.joinToString(", ") { it.text }
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == messageId) it.copy(repliesEnabled = false) else it })
        }
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = text, fromUser = true))
    }

    /** Answers an IMAGE_BUTTON reply-type button; web_url buttons are opened externally by the UI, never reach here. */
    fun selectImageButton(messageId: String, card: BotContent.ImageButtons.Card, button: BotContent.ImageButtons.Button, submitType: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!message.repliesEnabled) return
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == messageId) it.copy(repliesEnabled = false) else it })
        }
        val chatMsgId = repository.sendImageButton(card, button, submitType)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = button.text, fromUser = true))
    }

    /** Answers a TEXT_CAROUSEL card/button/dynamic-pill tap; "link"-type taps are opened externally by the UI. */
    fun selectTextCarouselReply(messageId: String, text: String, clickedIndex: Int, targetId: String?) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!message.repliesEnabled) return
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == messageId) it.copy(repliesEnabled = false) else it })
        }
        val chatMsgId = repository.sendTextCarouselReply(text, clickedIndex, targetId)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = text, fromUser = true))
    }

    /** Answers a WELCOME_SCREEN card tap. */
    fun selectWelcomeCard(messageId: String, card: BotContent.WelcomeScreen.Card, index: Int) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        if (!message.repliesEnabled) return
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == messageId) it.copy(repliesEnabled = false) else it })
        }
        val text = card.name?.trim()?.takeIf { it.isNotBlank() } ?: "Card ${index + 1}"
        val ctaTargetId = if (card.ctaEnabled && card.ctaType == "component" && !card.ctaLink.isNullOrBlank()) card.ctaLink else null
        val chatMsgId = repository.sendWelcomeCard(text, index + 1, ctaTargetId)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = text, fromUser = true))
    }

    /** IFRAME's postMessage bridge: the embedded page asked to advance the flow, no chat bubble involved. */
    fun advanceFromIframe(targetId: String) {
        repository.jumpToNode(targetId)
    }

    fun updateFormField(messageId: String, fieldIndex: Int, value: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    val current = message.formState ?: FormState()
                    if (current.submitted) return@map message
                    message.copy(formState = current.copy(values = current.values + (fieldIndex to value)))
                },
            )
        }
    }

    /** Uploads a MEDIA field's picked file immediately (holding the URL as its draft value until the form submits). */
    fun uploadFormField(messageId: String, fieldIndex: Int, bytes: ByteArray, fileName: String, mimeType: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    val current = message.formState ?: FormState()
                    message.copy(formState = current.copy(uploadingFields = current.uploadingFields + fieldIndex))
                },
            )
        }
        viewModelScope.launch {
            try {
                val url = repository.uploadFormMedia(bytes, fileName, mimeType) { }
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id != messageId) return@map message
                            val current = message.formState ?: FormState()
                            message.copy(
                                formState = current.copy(
                                    values = current.values + (fieldIndex to url),
                                    fileNames = current.fileNames + (fieldIndex to fileName),
                                    uploadingFields = current.uploadingFields - fieldIndex,
                                ),
                            )
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { message ->
                            if (message.id != messageId) return@map message
                            val current = message.formState ?: FormState()
                            message.copy(formState = current.copy(uploadingFields = current.uploadingFields - fieldIndex))
                        },
                    )
                }
            }
        }
    }

    /**
     * Validates every field with FormFieldValidator before sending - on failure, marks
     * attemptedSubmit so FormContent starts showing each field's inline error, matching the
     * widget's "errors appear once you try to submit" behavior.
     */
    fun submitForm(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val form = message.content as? BotContent.Form ?: return
        val formState = message.formState ?: FormState()
        if (formState.submitted) return
        val hasErrors = form.fields.any {
            FormFieldValidator.validate(it, formState.values[it.index].orEmpty()) != null
        }
        if (hasErrors) {
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map {
                        if (it.id == messageId) it.copy(formState = formState.copy(attemptedSubmit = true)) else it
                    },
                )
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(formState = formState.copy(submitted = true)) else it
                },
            )
        }
        val chatMsgId = repository.sendFormResponse(formState.values, form.fields, formState.fileNames)
        val summary = form.fields.sortedBy { it.index }
            .joinToString(", ") { formState.values[it.index].orEmpty() }
            .ifBlank { "Form submitted" }
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = summary, fromUser = true))
    }

    fun sendFile(bytes: ByteArray, fileName: String, mimeType: String) {
        val message = ChatMessage(text = "", fromUser = true, attachment = Attachment(fileName))
        appendMessage(message)
        viewModelScope.launch {
            try {
                repository.uploadAndSendFile(bytes, fileName, mimeType) { percent ->
                    updateAttachment(message.id) { it.copy(progress = percent) }
                }
                updateAttachment(message.id) { it.copy(progress = 100, uploaded = true) }
            } catch (e: Exception) {
                updateAttachment(message.id) { it.copy(failed = true) }
            }
        }
    }

    /** A recording just stopped - hold it as a reviewable draft (mirrors onVoiceMessageCaptured). */
    fun onVoiceRecordingCaptured(filePath: String, amplitudes: List<Int>, durationMs: Long) {
        _uiState.update { it.copy(voiceDraft = VoiceDraftState(filePath, amplitudes, durationMs)) }
    }

    /** Discards the draft file entirely (mirrors onClear/cancelVoiceMessage) - not recoverable. */
    fun cancelVoiceDraft() {
        val path = _uiState.value.voiceDraft?.filePath
        _uiState.update { it.copy(voiceDraft = null) }
        path?.let { java.io.File(it).delete() }
    }

    /** Uploads the draft then appends it as a sent bubble (mirrors sendVoiceMessage/retryVoiceUpload - retry just calls this again). */
    fun sendVoiceDraft() {
        val draft = _uiState.value.voiceDraft ?: return
        if (draft.uploading) return
        _uiState.update { it.copy(voiceDraft = draft.copy(uploading = true, uploadProgress = 0, error = null)) }
        viewModelScope.launch {
            try {
                val file = java.io.File(draft.filePath)
                val voiceUrl = repository.uploadAndSendVoiceMessage(file.readBytes(), file.name, "audio/mp4", "") { percent ->
                    _uiState.update { it.copy(voiceDraft = it.voiceDraft?.copy(uploadProgress = percent)) }
                }
                appendMessage(
                    ChatMessage(
                        text = "",
                        fromUser = true,
                        voiceMessage = VoiceMessageInfo(
                            localFilePath = draft.filePath,
                            remoteUrl = voiceUrl,
                            amplitudes = draft.amplitudes,
                            durationMs = draft.durationMs,
                        ),
                    ),
                )
                _uiState.update { it.copy(voiceDraft = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(voiceDraft = it.voiceDraft?.copy(uploading = false, error = "Upload failed. Try again or cancel.")) }
            }
        }
    }

    /** Submits the post-chat survey and ends the session - see ChatRepository.sendConfigurableFeedback. */
    fun submitFeedback(rating: Int?, feedbackText: String) {
        repository.sendConfigurableFeedback(rating, feedbackText)
        _uiState.update { it.copy(showFeedbackPrompt = false) }
    }

    /** Skips the survey - the session was already held open only for it, so this just lets the screen close normally. */
    fun dismissFeedbackPrompt() {
        _uiState.update { it.copy(showFeedbackPrompt = false) }
    }

    private fun updateAttachment(messageId: String, transform: (Attachment) -> Attachment) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.id == messageId) it.copy(attachment = it.attachment?.let(transform)) else it
                },
            )
        }
    }

    private fun handleMessageTimedOut(chatMsgId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (it.chatMsgId == chatMsgId) it.copy(failed = true) else it
                },
            )
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** Starts a distinct locally persisted conversation. */
    fun startNewChat() {
        val conversationId = java.util.UUID.randomUUID().toString()
        activeConversationId = conversationId
        viewModelScope.launch { cache.createConversation(botId, conversationId) }
        streamRawText.clear()
        hasStartedConversation = false
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                isAgentTyping = false,
                isLiveChat = false,
                assignedAgent = null,
                isArchived = false,
                voiceDraft = null,
                showFeedbackPrompt = false,
                pendingUrlToOpen = null,
            )
        }
    }

    /** Updates the sidebar immediately; the server rename is a best-effort background sync. */
    fun renameConversation(conversationId: String, title: String) {
        val normalizedTitle = title.trim().replace(Regex("\\s+"), " ").take(80)
        if (normalizedTitle.isBlank()) return
        val roomId = _conversations.value.firstOrNull { it.id == conversationId }?.roomId
        viewModelScope.launch(Dispatchers.IO) {
            cache.renameConversation(conversationId, normalizedTitle)
            if (roomId != null) {
                runCatching { dealerRoomsApi.renameRoom(roomId, normalizedTitle) }
                    .onFailure { error -> Log.e("Chat360", "Room rename API failed: ${error.message}", error) }
            }
        }
    }

    /** Binds the server room to its Room record and restores cached rich websocket messages. */
    private suspend fun activateConversation(roomId: String): Boolean {
        val (conversationId, hasCachedMessages) = cache.activateForRoom(botId, roomId, activeConversationId)
        activeConversationId = conversationId
        if (hasCachedMessages) {
            streamRawText.clear()
            _uiState.update { it.copy(messages = emptyList()) }
            restoringFromCache = true
            try {
                cache.messages(conversationId).forEach { cached ->
                    when (cached.kind) {
                        "USER" -> appendMessage(ChatMessage(chatMsgId = cached.chatMsgId, text = cached.payload, fromUser = true), cacheUserMessage = false)
                        "RAW" -> runCatching { cacheJson.decodeFromString<RawSocketEnvelope>(cached.payload).toIncomingEvent() }
                            .getOrNull()?.let(::handleEvent)
                    }
                }
            } finally {
                restoringFromCache = false
            }
        }
        val refreshed = refreshConversationHistory(conversationId, roomId)
        return hasCachedMessages || refreshed
    }

    /** Shows cached messages first, then replaces them with the room-id API snapshot and caches it. */
    private suspend fun refreshConversationHistory(conversationId: String, roomId: String): Boolean {
        val history = runCatching { repository.fetchHistory(roomId) }.getOrNull() ?: return false
        if (activeConversationId != conversationId) return history.isNotEmpty()
        streamRawText.clear()
        _uiState.update { it.copy(messages = emptyList(), isArchived = false, isLiveChat = false, assignedAgent = null) }
        restoringFromCache = true
        try {
            history.map { it.toIncomingEvent() }.forEach(::handleEvent)
        } finally {
            restoringFromCache = false
        }
        cache.replaceRawHistory(conversationId, history)
        return history.isNotEmpty()
    }

    private fun cacheIncomingEnvelope(raw: String) {
        val conversationId = activeConversationId ?: return
        // Cache only envelopes that create visible bot-side state.  This is deliberately
        // committed before the websocket callback returns instead of launching another UI
        // coroutine: otherwise a fast Activity/ViewModel teardown can cancel the write and
        // leave a conversation containing only the locally authored user messages.
        val isRenderableBotEvent = runCatching {
            when (cacheJson.decodeFromString<RawSocketEnvelope>(raw).toIncomingEvent()) {
                is IncomingSocketEvent.BotMessage,
                is IncomingSocketEvent.InactivityNotice -> true
                else -> false
            }
        }.getOrDefault(false)
        if (isRenderableBotEvent) {
            runCatching {
                runBlocking(Dispatchers.IO) { cache.cacheRaw(conversationId, raw) }
            }
        }
    }

    fun openConversation(conversationId: String) {
        if (conversationId == activeConversationId) return
        viewModelScope.launch {
            activeConversationId = conversationId
            streamRawText.clear()
            _uiState.update { it.copy(messages = emptyList(), isArchived = false, isLiveChat = false, assignedAgent = null) }
            restoringFromCache = true
            try {
                cache.messages(conversationId).forEach { cached ->
                    when (cached.kind) {
                        "USER" -> appendMessage(ChatMessage(chatMsgId = cached.chatMsgId, text = cached.payload, fromUser = true), cacheUserMessage = false)
                        "RAW" -> runCatching { cacheJson.decodeFromString<RawSocketEnvelope>(cached.payload).toIncomingEvent() }
                            .getOrNull()?.let(::handleEvent)
                    }
                }
            } finally {
                restoringFromCache = false
            }
            val roomId = _conversations.value.firstOrNull { it.id == conversationId }?.roomId
            if (roomId != null) refreshConversationHistory(conversationId, roomId)
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        // Ports UserInput/index.tsx's validate(): the empty-message guard only applies outside
        // live chat (`!value.trim() && !isLiveChat`) - live chat allows an empty submit through.
        if (text.isEmpty() && !_uiState.value.isLiveChat) return
        hasStartedConversation = true
        val chatMsgId = repository.sendFreeText(text)
        _uiState.update { it.copy(inputText = "") }
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = text, fromUser = true))
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }

    class Factory(
        private val context: Context,
        private val baseUrl: String,
        private val botId: String,
        private val historyEnabled: Boolean = true,
        private val employeeCode: String? = null,
        private val suppressInitialBotMessages: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                repository = ChatRepository(baseUrl, botId, historyEnabled = historyEnabled),
                botId = botId,
                cache = ChatCacheRepository(ChatCacheDatabase.get(context).dao()),
                dealerRoomsApi = Chat360ApiService(baseUrl),
                employeeCode = employeeCode,
                suppressInitialBotMessages = suppressInitialBotMessages,
            ) as T
        }
    }
}
