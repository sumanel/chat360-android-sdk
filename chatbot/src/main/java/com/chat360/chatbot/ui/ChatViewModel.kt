package com.chat360.chatbot.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chat360.chatbot.cache.CachedConversationEntity
import com.chat360.chatbot.cache.ChatCacheDatabase
import com.chat360.chatbot.cache.ChatCacheRepository
import com.chat360.chatbot.domain.ChatRepository
import com.chat360.chatbot.domain.thirdparty.ChatHistoryRepository
import com.chat360.chatbot.domain.thirdparty.ThirdPartyTokenManager
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import com.chat360.chatbot.domain.validation.FormFieldValidator
import com.chat360.chatbot.domain.validation.InputValidators
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.model.wire.BotNode
import com.chat360.chatbot.model.wire.IncomingSocketEvent
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import com.chat360.chatbot.model.wire.toIncomingEvent
import com.chat360.chatbot.network.rest.dto.SessionLanguage
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
    private val chatHistoryRepository: ChatHistoryRepository? = null,
    private val suppressInitialBotMessages: Boolean = false,
    /** Only used for the offline pre-send connectivity check (see [sendMessage]) - null is
     * safe, it just disables that check and lets a send attempt fail normally instead. */
    private val appContext: Context? = null,
) : ViewModel() {

    private var hasStartedConversation = false

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val _conversations = MutableStateFlow<List<CachedConversationEntity>>(emptyList())
    val conversations: StateFlow<List<CachedConversationEntity>> = _conversations.asStateFlow()
    /** label -> targetId, from session-init's `bot_settings.bot_shortcuts`. Empty when the
     * bot has none configured. */
    private val _shortcuts = MutableStateFlow<Map<String, String>>(emptyMap())
    val shortcuts: StateFlow<Map<String, String>> = _shortcuts.asStateFlow()
    /** Mirrors session-init's `bot_settings.languages` - empty when the bot is single-language. */
    private val _languages = MutableStateFlow<List<SessionLanguage>>(emptyList())
    val languages: StateFlow<List<SessionLanguage>> = _languages.asStateFlow()
    /** The conversation currently *displayed* - can diverge from [connectedConversationId] when
     * the user is browsing an older cached conversation from the drawer (see [openConversation]). */
    private var activeConversationId: String? = null
    /** The conversation the live [ChatRepository] socket is actually bound to - set once per
     * `connect()`/[startNewChat]'s `startNewSession()`, never by [openConversation]. Live socket
     * events/reconnects only ever belong to *this* conversation - caching and rendering both key
     * off it, never off [activeConversationId], so browsing an old conversation can never
     * misattribute a live frame into it. */
    private var connectedConversationId: String? = null
    /** True only while Room entries are being replayed; cached messages are never “initial” UI. */
    private var restoringFromCache = false
    private val cacheJson = Json { ignoreUnknownKeys = true }
    /** The active conversation's `previous_cursor` for [loadMoreHistory] - set by whichever
     * fetch (seed or "load more") most recently ran, reset to null on every conversation
     * switch since a cursor is only ever meaningful within the room it came from. */
    private var previousHistoryCursor: Int? = null

    init {
        viewModelScope.launch { cache.conversations(botId).collect { _conversations.value = it } }
        if (chatHistoryRepository != null) {
            viewModelScope.launch(Dispatchers.IO) {
                // Room emits first (see collector above). A successful rooms/list fetch then
                // becomes visible immediately; a failure leaves whatever's already shown alone,
                // only flipping isHistoryUnavailable so a host UI can show a retry affordance.
                val refreshed = chatHistoryRepository.refreshRooms()
                if (refreshed != null) {
                    _conversations.value = refreshed
                    _uiState.update { it.copy(isHistoryUnavailable = false) }
                } else {
                    _uiState.update { it.copy(isHistoryUnavailable = true) }
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
                onBotSettingsLoaded = { shortcuts, languages ->
                    _shortcuts.value = shortcuts
                    _languages.value = languages
                },
            )
        }
    }

    private fun handleEvent(event: IncomingSocketEvent) {
        // A live frame arriving while the user is browsing a different (non-connected)
        // conversation from the drawer must not render into that unrelated transcript - it still
        // got cached correctly (cacheIncomingEnvelope keys off connectedConversationId, not
        // this), so reopening the connected conversation will show it. Replay (cache or REST
        // history) is always allowed through: it only ever runs against whatever
        // activeConversationId was just set to immediately before it started.
        if (!restoringFromCache && activeConversationId != connectedConversationId) return
        when (event) {
            is IncomingSocketEvent.BotMessage -> {
                if (suppressInitialBotMessages && !hasStartedConversation && !restoringFromCache) return
                val node = event.node
                // An Unsupported node with no text at all renders nothing (yet) rather than an
                // empty bubble; one with fallback text (most node types include questionText)
                // still shows as plain text even before its specific renderer exists. A
                // WINDOW_EVENT node never renders a bubble at all - it only drives the bridge.
                if (node.text == null && node.content is BotContent.Unsupported) return
                if (node.content is BotContent.WindowEvent) return
                // isLiveChat flips true on the transfer notice OR any admin/operator-authored
                // message, and also flips back false on the next bot-authored message (rather
                // than staying true until an explicit update_status). That's a deliberate
                // simplification: it makes replaying loaded history alone correctly reconstruct
                // whether the session is *currently* live without a separate one-off scan, at
                // the minor cost of misclassifying the rare case where a bot message is ever
                // injected mid-live-session.
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
            // Live echoes only drive ChatRepository's ack-tracking - the user's own bubble was
            // already appended optimistically the moment they hit send, so rendering it again
            // here would duplicate it. During history replay there was no such optimistic
            // append, so this is the only place that user turn ever gets reconstructed from.
            is IncomingSocketEvent.EchoedUserMessage -> {
                if (restoringFromCache) {
                    event.text?.let {
                        appendMessage(ChatMessage(chatMsgId = event.chatMsgId, text = it, fromUser = true), cacheUserMessage = false)
                    }
                }
            }
            // Ack/pong only drive ChatRepository's internal bookkeeping (ack-timeout
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

    /** Appending any message locks the quick replies of everything before it.
     * A user-authored message also persists to the local cache, keyed by [connectedConversationId]
     * (the room the live socket is actually bound to) - never by [activeConversationId], which can
     * point at a different, merely-browsed conversation with no live room behind it at all.
     * Sending anything while browsing an old conversation snaps the display back to the connected
     * one (replaying its cache) first, since there's nowhere else coherent for it to go. */
    private fun appendMessage(message: ChatMessage, cacheUserMessage: Boolean = message.fromUser) {
        val target = connectedConversationId
        if (message.fromUser && !restoringFromCache && target != null && activeConversationId != target) {
            activeConversationId = target
            viewModelScope.launch {
                replayFromCache(target)
                appendMessageNow(message, cacheUserMessage)
            }
            return
        }
        appendMessageNow(message, cacheUserMessage)
    }

    private fun appendMessageNow(message: ChatMessage, cacheUserMessage: Boolean) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { it.copy(repliesEnabled = false) } + message,
            )
        }
        if (cacheUserMessage) connectedConversationId?.let { conversationId ->
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

    /** Answers a tap on the shortcuts menu - sends the shortcut's label as a real
     * user-authored message that jumps straight to its target node. */
    fun selectShortcut(targetId: String, label: String) {
        val chatMsgId = repository.sendShortcut(targetId, label)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = label, fromUser = true))
    }

    /** Switches the bot flow to another language's entry node - reconnects first if the socket
     * dropped, clears the transcript (this is a flow restart, not a new conversation - same
     * local conversation/room, just a fresh starting point), then jumps to the language's
     * target with no user-authored bubble. */
    fun switchLanguage(targetId: String) {
        if (connectedConversationId != null && activeConversationId != connectedConversationId) {
            activeConversationId = connectedConversationId
        }
        if (!_uiState.value.isConnected) repository.reconnectNow()
        streamRawText.clear()
        hasStartedConversation = false
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isAgentTyping = false,
                isLiveChat = false,
                assignedAgent = null,
                isArchived = false,
                showFeedbackPrompt = false,
            )
        }
        repository.jumpToNode(targetId)
    }

    /** User-triggered "refresh this chat" action - reconnects immediately instead of waiting
     * out the automatic backoff, for when a host app wants to expose a manual retry affordance. */
    fun refreshConnection() {
        repository.reconnectNow()
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
     * attemptedSubmit so FormContent starts showing each field's inline error only once
     * a submit has been attempted.
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

    /** A recording just stopped - hold it as a reviewable draft. */
    fun onVoiceRecordingCaptured(filePath: String, amplitudes: List<Int>, durationMs: Long) {
        _uiState.update { it.copy(voiceDraft = VoiceDraftState(filePath, amplitudes, durationMs)) }
    }

    /** Discards the draft file entirely - not recoverable. */
    fun cancelVoiceDraft() {
        val path = _uiState.value.voiceDraft?.filePath
        _uiState.update { it.copy(voiceDraft = null) }
        path?.let { java.io.File(it).delete() }
    }

    /** Uploads the draft then appends it as a sent bubble - retry just calls this again. */
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

    /** Starts a distinct locally persisted conversation backed by a genuinely new server room -
     * the repository's roomId is otherwise fixed for the ViewModel's lifetime, so without this
     * every "new chat" would keep sending into the same room as whatever conversation was
     * active before, silently mixing them together (and clobbering each other's history on
     * reopen, since [refreshConversationHistory] replaces a conversation's cache with its
     * room's current server state). */
    fun startNewChat() {
        val conversationId = java.util.UUID.randomUUID().toString()
        connectedConversationId = conversationId
        activeConversationId = conversationId
        streamRawText.clear()
        hasStartedConversation = false
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                isAgentTyping = false,
                isConnected = false,
                isLiveChat = false,
                assignedAgent = null,
                isArchived = false,
                voiceDraft = null,
                showFeedbackPrompt = false,
                pendingUrlToOpen = null,
                hasMoreHistory = false,
            )
        }
        viewModelScope.launch {
            cache.createConversation(botId, conversationId)
            repository.startNewSession(onConversationStarted = ::activateConversation)
        }
    }

    /** Updates the sidebar immediately; the server rename is a best-effort background sync. */
    fun renameConversation(conversationId: String, title: String) {
        val normalizedTitle = title.trim().replace(Regex("\\s+"), " ").take(80)
        if (normalizedTitle.isBlank()) return
        val roomId = _conversations.value.firstOrNull { it.id == conversationId }?.roomId
        viewModelScope.launch(Dispatchers.IO) {
            cache.renameConversation(conversationId, normalizedTitle)
            if (roomId != null && chatHistoryRepository != null) {
                chatHistoryRepository.renameRoom(roomId, normalizedTitle)
            }
        }
    }

    /** Deletes a conversation from local history. Deleting the *connected* one always starts a
     * fresh chat - its live room's cache row is gone, so nothing should keep writing into it.
     * Deleting a merely-*displayed* (browsed) one falls back to another existing conversation (or
     * starts a fresh one if none remain) so the screen is never left pointing at a conversation
     * that no longer exists. */
    fun deleteConversation(conversationId: String) {
        val wasConnected = conversationId == connectedConversationId
        val wasActive = conversationId == activeConversationId
        val roomId = _conversations.value.firstOrNull { it.id == conversationId }?.roomId
        viewModelScope.launch {
            cache.deleteConversation(conversationId)
            if (roomId != null && chatHistoryRepository != null) {
                launch(Dispatchers.IO) { chatHistoryRepository.markRoomInactive(roomId) }
            }
            if (wasConnected) {
                startNewChat()
                return@launch
            }
            if (!wasActive) return@launch
            val fallback = _conversations.value.firstOrNull { it.id != conversationId }
            if (fallback != null) openConversation(fallback.id) else startNewChat()
        }
    }

    /** Binds the server room to its Room record and restores cached rich websocket messages.
     * Local cache is the durable source of truth for a conversation this device has already
     * seen - [refreshConversationHistory]'s REST fetch only ever seeds a conversation that has
     * none yet; growing it further is only ever additive pagination, never a wholesale resync.
     * Skipping it once local messages exist also avoids a real data-loss bug:
     * that endpoint caps at a handful of the most recent raw items, and unconditionally
     * replacing the local cache with a truncated snapshot on every reopen would silently delete
     * everything older than that window. */
    private suspend fun activateConversation(roomId: String): Boolean {
        val (conversationId, hasCachedMessages) = cache.activateForRoom(botId, roomId, connectedConversationId)
        connectedConversationId = conversationId
        activeConversationId = conversationId
        previousHistoryCursor = null
        if (hasCachedMessages) {
            replayFromCache(conversationId)
            return true
        }
        return refreshConversationHistory(conversationId, roomId)
    }

    /** Replays a conversation's cached Room entries through [handleEvent] - shared by
     * [activateConversation]'s cache-hit path, [openConversation], and [appendMessage]'s
     * snap-back-to-connected path. Returns whether any cached entries were found. */
    private suspend fun replayFromCache(conversationId: String): Boolean {
        streamRawText.clear()
        _uiState.update { it.copy(messages = emptyList(), hasMoreHistory = false) }
        restoringFromCache = true
        var hasCachedMessages = false
        try {
            cache.messages(conversationId).forEach { cached ->
                hasCachedMessages = true
                when (cached.kind) {
                    "USER" -> appendMessage(ChatMessage(chatMsgId = cached.chatMsgId, text = cached.payload, fromUser = true), cacheUserMessage = false)
                    "RAW" -> runCatching { cacheJson.decodeFromString<RawSocketEnvelope>(cached.payload).toIncomingEvent() }
                        .getOrNull()?.let(::handleEvent)
                }
            }
        } finally {
            restoringFromCache = false
        }
        return hasCachedMessages
    }

    /** Seeds a conversation that has no local messages yet from the room's server snapshot -
     * see [activateConversation]'s doc for why this must never run once local cache exists. */
    private suspend fun refreshConversationHistory(conversationId: String, roomId: String): Boolean {
        val response = runCatching { repository.fetchHistory(roomId) }.getOrNull() ?: return false
        val history = response.history
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
        previousHistoryCursor = response.previous_cursor
        _uiState.update { it.copy(hasMoreHistory = response.previous_cursor != null) }
        return history.isNotEmpty()
    }

    /** Pages one step further back, triggered directly by the UI when the scroll position
     * nears the top. Additive only: prepends the older page, never touches what's already shown. */
    fun loadMoreHistory() {
        val conversationId = activeConversationId ?: return
        val roomId = _conversations.value.firstOrNull { it.id == conversationId }?.roomId ?: return
        val cursor = previousHistoryCursor ?: return
        if (_uiState.value.isLoadingMoreHistory) return
        _uiState.update { it.copy(isLoadingMoreHistory = true) }
        viewModelScope.launch {
            val response = runCatching { repository.fetchMoreHistory(roomId, cursor) }.getOrNull()
            if (response == null || activeConversationId != conversationId) {
                _uiState.update { it.copy(isLoadingMoreHistory = false) }
                return@launch
            }
            val sizeBefore = _uiState.value.messages.size
            restoringFromCache = true
            try {
                response.history.map { it.toIncomingEvent() }.forEach(::handleEvent)
            } finally {
                restoringFromCache = false
            }
            // handleEvent only ever appends - the older page just landed at the tail, so move
            // it to the front instead of re-deriving each ChatMessage's construction here.
            _uiState.update { state ->
                val olderMessages = state.messages.drop(sizeBefore)
                val existingMessages = state.messages.take(sizeBefore)
                state.copy(
                    messages = olderMessages + existingMessages,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = response.previous_cursor != null,
                )
            }
            previousHistoryCursor = response.previous_cursor
        }
    }

    private fun cacheIncomingEnvelope(raw: String) {
        val conversationId = connectedConversationId ?: return
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
            previousHistoryCursor = null
            _uiState.update { it.copy(isArchived = false, isLiveChat = false, assignedAgent = null) }
            val hasCachedMessages = replayFromCache(conversationId)
            // See activateConversation's doc: only seed from the server when there's nothing
            // local yet - never resync an already-known conversation and risk truncating it.
            if (!hasCachedMessages) {
                val roomId = _conversations.value.firstOrNull { it.id == conversationId }?.roomId
                if (roomId != null) refreshConversationHistory(conversationId, roomId)
            }
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        // The empty-message guard only applies outside live chat; live chat allows an empty
        // submit through.
        if (text.isEmpty() && !_uiState.value.isLiveChat) return
        hasStartedConversation = true
        _uiState.update { it.copy(inputText = "") }
        // With no connectivity at all, skip the send attempt entirely and show the bubble as
        // failed immediately, rather than waiting out a doomed attempt and the ack-timeout that
        // would eventually flag it anyway.
        if (!isOnline()) {
            appendMessage(ChatMessage(text = text, fromUser = true, failed = true))
            return
        }
        val chatMsgId = repository.sendFreeText(text)
        appendMessage(ChatMessage(chatMsgId = chatMsgId, text = text, fromUser = true))
    }

    /** Best-effort connectivity check. Returns true (i.e. "don't block the send") when there's
     * no [appContext] to check with or the platform API errors, since a failed send still
     * degrades gracefully via the existing ack-timeout path. */
    private fun isOnline(): Boolean {
        val context = appContext ?: return true
        return runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
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
        private val clientId: String? = null,
        private val apiKey: String? = null,
        private val endUserId: String? = null,
        private val suppressInitialBotMessages: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val cache = ChatCacheRepository(ChatCacheDatabase.get(context).dao())
            val chatHistoryRepository = buildChatHistoryRepository(cache)
            return ChatViewModel(
                repository = ChatRepository(baseUrl, botId, historyEnabled = historyEnabled),
                botId = botId,
                cache = cache,
                chatHistoryRepository = chatHistoryRepository,
                suppressInitialBotMessages = suppressInitialBotMessages,
                appContext = context.applicationContext,
            ) as T
        }

        /** History (the rooms list backed by the `third-party-tasks` API) requires [clientId],
         * [apiKey], [endUserId], and [botId] *all* set - there is no partially-configured mode.
         * Leaving all three third-party fields unset is a valid choice (history is simply off);
         * setting only some of them is almost certainly a host-app integration mistake, so it's
         * always logged loudly - but never crashes the host app, in any build type, since a
         * third-party SDK misconfiguration must never be able to take down the whole app. */
        private fun buildChatHistoryRepository(cache: ChatCacheRepository): ChatHistoryRepository? {
            val trimmedClientId = clientId?.trim()?.takeIf { it.isNotEmpty() }
            val trimmedApiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() }
            val trimmedEndUserId = endUserId?.trim()?.takeIf { it.isNotEmpty() }
            val hasAnyThirdPartyConfig = trimmedClientId != null || trimmedApiKey != null || trimmedEndUserId != null
            if (trimmedClientId == null || trimmedApiKey == null || trimmedEndUserId == null || botId.isBlank()) {
                if (hasAnyThirdPartyConfig) {
                    Log.e(
                        "Chat360",
                        "Chat360 history is misconfigured: clientId, apiKey, and endUserId must ALL " +
                            "be set together (botId is already required) to enable the rooms/history " +
                            "list. Got clientId=${trimmedClientId != null}, apiKey=${trimmedApiKey != null}, " +
                            "endUserId=${trimmedEndUserId != null}. History will stay disabled until all are provided.",
                    )
                }
                return null
            }
            val thirdPartyApi = ThirdPartyTasksApiService(baseUrl)
            return ChatHistoryRepository(
                apiService = thirdPartyApi,
                tokenManager = ThirdPartyTokenManager(thirdPartyApi, trimmedClientId, trimmedApiKey),
                cache = cache,
                clientId = trimmedClientId,
                botId = botId,
                endUserId = trimmedEndUserId,
            )
        }
    }
}
