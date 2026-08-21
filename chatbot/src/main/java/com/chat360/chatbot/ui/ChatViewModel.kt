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
import com.chat360.chatbot.domain.SharedPreferencesSessionStore
import com.chat360.chatbot.domain.thirdparty.ChatHistoryRepository
import com.chat360.chatbot.domain.thirdparty.MessageFeedbackRepository
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
import com.chat360.chatbot.ui.components.messages.content.copyText
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
    private val messageFeedbackRepository: MessageFeedbackRepository? = null,
    private val suppressInitialBotMessages: Boolean = false,
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
    /** The connected conversation's server room id, staged here until [ensureConversationPersisted]
     * commits it - only known once session-init/[activateConversation] returns. */
    private var connectedRoomId: String? = null
    /** Whether [connectedConversationId] is a real row in the cache DB yet. False for a freshly
     * opened chat the user hasn't sent anything in - see [ensureConversationPersisted]. */
    private var conversationPersisted = false
    /** Raw bot envelopes ([cacheIncomingEnvelope]) received before the user has sent a message,
     * held here instead of hitting the DB (which would require a conversation row to exist -
     * see [conversationPersisted]) until [ensureConversationPersisted] flushes them. */
    private val pendingRawEnvelopes = mutableListOf<String>()
    /** True only while Room entries are being replayed; cached messages are never “initial” UI. */
    private var restoringFromCache = false
    /** Set only while [appendMessage]'s browsing-snap-back restore is in flight for this exact
     * chatMsgId - see its doc for why. */
    private var pendingSnapBackChatMsgId: String? = null
    /** The `chat_messages` row id [cacheIncomingEnvelope] just wrote for the raw envelope
     * [handleEvent] is about to dispatch - consumed (and cleared) at the top of [handleEvent] so
     * the resulting ChatMessage can be stamped with the row backing it. Relies on
     * ChatRepository.handleIncoming calling onRawIncoming then onEvent synchronously for the same
     * frame; stays null for replay paths, which never go through [cacheIncomingEnvelope]. */
    private var lastCachedRowId: Long? = null
    private val cacheJson = Json { ignoreUnknownKeys = true }
    /** Bot messages seen since the last periodic feedback prompt (or since connect) - compared
     * against [nextPeriodicFeedbackThreshold] in [registerBotMessageForFeedback]. */
    private var botMessagesSinceFeedback = 0
    private var nextPeriodicFeedbackThreshold = (3..5).random()
    /** The active conversation's `previous_cursor` for [loadMoreHistory] - set by whichever
     * fetch (seed or "load more") most recently ran, reset to null on every conversation
     * switch since a cursor is only ever meaningful within the room it came from. */
    private var previousHistoryCursor: Int? = null

    /** Mirrors [activeConversationId] into [ChatUiState] so the history sidebar can highlight
     * whichever conversation is currently open - the plain `var` above is read-only from the UI. */
    private fun setActiveConversationId(id: String?) {
        activeConversationId = id
        _uiState.update { it.copy(activeConversationId = id) }
    }

    init {
        // Local Room cache is the primary source for the sidebar; when a third-party
        // chatHistoryRepository is also configured (below), its refreshRooms() result can
        // overwrite this with the live agent-room list instead.
        viewModelScope.launch { cache.conversations(botId).collect { _conversations.value = it } }
        if (chatHistoryRepository != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val refreshed = chatHistoryRepository.refreshRooms()
                if (refreshed != null) _conversations.value = refreshed
                _uiState.update { it.copy(isHistoryUnavailable = refreshed == null) }
            }
        }
        viewModelScope.launch {
            repository.connect(
                onEvent = ::handleEvent,
                onConnected = { _uiState.update { it.copy(isConnected = true, error = null) } },
                onError = { e ->
                    // No banner here on purpose: transient drops are common and the socket
                    // already retries with backoff in the background (see ReconnectManager),
                    // same as message sends now retry silently (see AckTracker) instead of
                    // nagging the user - a message only ever surfaces as "Not delivered" once
                    // every retry has genuinely failed.
                    Log.e("Chat360", "Chat connection failed: ${e.message}", e)
                    _uiState.update { it.copy(isConnected = false, isAgentTyping = false) }
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
        // Consumed here (not left for the BotMessage branch below) so it's cleared on every call
        // regardless of which branch runs or whether this event even reaches one - otherwise a
        // stale id could leak onto an unrelated later message.
        val cachedRowId = lastCachedRowId
        lastCachedRowId = null
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
                // Counted once per complete bot bubble, not per streamed chunk - a streaming
                // reply only counts on its final chunk (streamEnded); history replay never counts
                // at all, or every app reopen would immediately re-trigger the prompt.
                if (!restoringFromCache && (node.streamId == null || node.streamEnded)) {
                    registerBotMessageForFeedback()
                }
                // isLiveChat flips true on the transfer notice OR any admin/operator-authored
                // message, and also flips back false on the next bot-authored message (rather
                // than staying true until an explicit update_status). That's a deliberate
                // simplification: it makes replaying loaded history alone correctly reconstruct
                // whether the session is *currently* live without a separate one-off scan, at
                // the minor cost of misclassifying the rare case where a bot message is ever
                // injected mid-live-session.
                _uiState.update {
                    it.copy(
                        // A real reply just arrived, so the "waiting for a response" loader set
                        // optimistically in sendMessage() (or by a server typing_status) no
                        // longer applies - clear it here as a safety net independent of whether
                        // the server ever sent an explicit typing_status:false.
                        isAgentTyping = false,
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
                    appendOrMergeStreamChunk(node, cachedRowId)
                    return
                }
                appendMessage(
                    ChatMessage(
                        text = node.text.orEmpty(),
                        fromUser = false,
                        timeText = formatMessageTime(node.timestampMs),
                        content = node.content,
                        author = node.author,
                        cacheRowId = cachedRowId,
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
            // The chatMsgId check excludes one specific case where both are true at once: a
            // browsing-snap-back restore (see appendMessage/pendingSnapBackChatMsgId) sets
            // restoringFromCache for an unrelated reason while the just-sent message's own live
            // echo can land mid-restore - that echo must not double-render alongside the
            // deliberate optimistic append still pending right behind it.
            is IncomingSocketEvent.EchoedUserMessage -> {
                if (restoringFromCache && event.chatMsgId != pendingSnapBackChatMsgId) {
                    event.text?.let {
                        appendMessage(
                            ChatMessage(
                                chatMsgId = event.chatMsgId,
                                text = it,
                                fromUser = true,
                                timeText = formatMessageTime(event.timestampMs),
                            ),
                            cacheUserMessage = false,
                        )
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

    private fun appendOrMergeStreamChunk(node: BotNode, cachedRowId: Long?) {
        val streamId = node.streamId ?: return
        val mergedRaw = streamRawText.getOrDefault(streamId, "") + node.text.orEmpty()
        if (node.streamEnded) streamRawText.remove(streamId) else streamRawText[streamId] = mergedRaw
        _uiState.update { state ->
            val index = state.messages.indexOfLast { it.streamId == streamId }
            if (index >= 0) {
                // Track the latest chunk's row so a like/dislike tapped mid-stream persists to
                // wherever the bubble's text currently ends - falls back to whatever row this
                // bubble already had if the newest chunk wasn't cached (e.g. conversation not
                // yet persisted, see cacheIncomingEnvelope).
                val merged = state.messages[index].copy(text = mergedRaw, cacheRowId = cachedRowId ?: state.messages[index].cacheRowId)
                state.copy(messages = state.messages.toMutableList().apply { this[index] = merged })
            } else {
                state.copy(
                    messages = state.messages.map { it.copy(repliesEnabled = false) } +
                        ChatMessage(text = mergedRaw, fromUser = false, streamId = streamId, cacheRowId = cachedRowId),
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
     * one first, since there's nowhere else coherent for it to go - via [restoreConversation]
     * rather than a bare cache replay, so it comes back showing its real history rather than
     * whatever partial state was last rendered. */
    private fun appendMessage(message: ChatMessage, cacheUserMessage: Boolean = message.fromUser) {
        val target = connectedConversationId
        if (message.fromUser && !restoringFromCache && target != null && activeConversationId != target) {
            setActiveConversationId(target)
            // restoreConversation's REST fetch (unlike the old bare cache replay it replaced) is
            // a real network round trip, wide enough for the server's own echo of this same send
            // to land mid-restore. That echo would otherwise double-render: handleEvent's
            // EchoedUserMessage branch renders any echo that arrives while restoringFromCache is
            // true (that's the mechanism live history replay relies on), and restoreConversation
            // sets exactly that flag for an unrelated reason. Tracking this one chatMsgId lets
            // that branch tell "this is the send I'm about to append myself" apart from a
            // genuine older turn being replayed, without touching the replay path itself.
            pendingSnapBackChatMsgId = message.chatMsgId
            viewModelScope.launch {
                restoreConversation(target, connectedRoomId)
                pendingSnapBackChatMsgId = null
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
        // Whatever earned this chat its first real user turn - typed text, a quick reply, a
        // form field, all funnel through here - is what earns its conversation a real row in
        // history; see ensureConversationPersisted.
        if (cacheUserMessage) connectedConversationId?.let { conversationId ->
            viewModelScope.launch {
                ensureConversationPersisted()
                cache.cacheUserMessage(conversationId, message.text, message.chatMsgId)
                upsertLiveConversationEntry(conversationId, message.text)
            }
        }
    }

    /** Reflects a just-sent message into the sidebar immediately, ahead of the Room flow's own
     * (slightly async) invalidation-triggered re-query, so the entry never has a visible lag
     * appearing after the user's first message. The DB write in [cache.cacheUserMessage] above
     * is the durable copy; this is just the optimistic in-memory mirror of it. */
    private fun upsertLiveConversationEntry(conversationId: String, title: String) {
        val now = System.currentTimeMillis()
        val normalizedTitle = title.trim().replace(Regex("\\s+"), " ").take(80).ifBlank { "New conversation" }
        _conversations.update { current ->
            val existing = current.firstOrNull { it.id == conversationId }
            val entry = CachedConversationEntity(
                id = conversationId,
                botId = botId,
                roomId = connectedRoomId,
                // Only the first real turn names the conversation - same rule as
                // ChatCacheDao.touchAndSetTitleIfUnset, mirrored here so the optimistic entry
                // above matches the title the DB row will settle on.
                title = if (existing == null || existing.title == "New conversation") normalizedTitle else existing.title,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            (listOf(entry) + current.filterNot { it.id == conversationId }).sortedByDescending { it.updatedAt }
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

    /**
     * Records a thumbs up/down on a bot message: updates [ChatUiState.messages] immediately so
     * the icon reflects it, persists it to the local cache row backing this message (via
     * [ChatMessage.cacheRowId]) so it survives leaving and reopening the conversation, and -
     * separately, only when that backend is configured ([messageFeedbackRepository] is non-null,
     * see [Factory]) and the session is established - submits it to the `third-party-tasks`
     * feedback endpoint. [remarks] must be null for a like; for a dislike the UI collects a
     * required 20+ char reason before calling this (see FeedbackRemarksDialog). `query`/`response`
     * are the user turn that led to this bot message and the message's own display text - the
     * nearest preceding user message in [messages][ChatUiState.messages] is used since bot
     * messages don't carry a link to the turn that prompted them.
     */
    fun submitMessageFeedback(messageId: String, liked: Boolean, remarks: String?) {
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val message = messages[index]
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == messageId) it.copy(liked = liked) else it })
        }
        persistMessageFeedback(message.cacheRowId, liked)

        val repo = messageFeedbackRepository ?: return
        val roomId = connectedRoomId ?: return
        val sessionId = repository.currentSessionId() ?: return
        val query = messages.subList(0, index).lastOrNull { it.fromUser }?.text.orEmpty()
        val response = message.copyText()
        viewModelScope.launch(Dispatchers.IO) {
            repo.submit(roomId, sessionId, messageId, query, response, liked, remarks)
        }
    }

    /** Un-sets a previously given thumbs up/down (tapping the same icon again) - local/cache only,
     * mirroring the existing UI behavior of never calling the feedback endpoint for a retraction
     * (it has no "unset" concept). */
    fun clearMessageFeedback(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == messageId) it.copy(liked = null) else it })
        }
        persistMessageFeedback(message.cacheRowId, null)
    }

    private fun persistMessageFeedback(cacheRowId: Long?, liked: Boolean?) {
        cacheRowId ?: return
        viewModelScope.launch(Dispatchers.IO) { cache.setMessageFeedback(cacheRowId, liked) }
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
            setActiveConversationId(connectedConversationId)
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

    /** Called when the host screen returns to the foreground (see ChatScreen's own doc for why).
     * Reuses the connected room - the same reconnect a user could trigger manually via
     * [refreshConnection] - never a brand-new chat; only fires when there's actually a dead
     * connection to recover, so this is a no-op on a completely normal resume. */
    fun onAppForegrounded() {
        if (_uiState.value.isConnected) return
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

    /** Bumps the periodic-feedback counter on every real bot bubble and, once it hits the
     * current random 3-5 threshold, shows [PeriodicFeedbackDialog] and re-rolls the threshold
     * for next time. A no-op if the `third-party-tasks` feedback backend isn't configured
     * ([messageFeedbackRepository] null) or the prompt is already showing. */
    private fun registerBotMessageForFeedback() {
        if (messageFeedbackRepository == null) return
        if (_uiState.value.showPeriodicFeedbackPrompt) return
        botMessagesSinceFeedback++
        if (botMessagesSinceFeedback < nextPeriodicFeedbackThreshold) return
        botMessagesSinceFeedback = 0
        nextPeriodicFeedbackThreshold = (3..5).random()
        _uiState.update { it.copy(showPeriodicFeedbackPrompt = true) }
    }

    /** Submits the mandatory periodic check-in and closes the dialog - the dialog itself has no
     * cancel path, so this is the only caller. */
    fun submitPeriodicFeedback(feedback: String) {
        _uiState.update { it.copy(showPeriodicFeedbackPrompt = false) }
        val repo = messageFeedbackRepository ?: return
        val roomId = connectedRoomId ?: return
        val sessionId = repository.currentSessionId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.submitPeriodic(roomId, sessionId, feedback)
        }
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
                isAgentTyping = false,
            )
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** Starts a distinct conversation backed by a genuinely new server room - the repository's
     * roomId is otherwise fixed for the ViewModel's lifetime, so without this every "new chat"
     * would keep sending into the same room as whatever conversation was active before, silently
     * mixing them together (and clobbering each other's history on reopen, since
     * [refreshConversationHistory] replaces a conversation's cache with its room's current server
     * state). Not written to the DB until the user actually sends something in it - see
     * [ensureConversationPersisted] - so an unused "new chat" never leaves an empty entry behind. */
    fun startNewChat() {
        val conversationId = java.util.UUID.randomUUID().toString()
        connectedConversationId = conversationId
        connectedRoomId = null
        conversationPersisted = false
        pendingRawEnvelopes.clear()
        setActiveConversationId(conversationId)
        streamRawText.clear()
        hasStartedConversation = false
        botMessagesSinceFeedback = 0
        nextPeriodicFeedbackThreshold = (3..5).random()
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
                showPeriodicFeedbackPrompt = false,
                pendingUrlToOpen = null,
                hasMoreHistory = false,
            )
        }
        viewModelScope.launch {
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
        // Optimistic - dropped from the in-memory list immediately rather than waiting on
        // cache.deleteConversation's DB write and the Room flow's own (slightly async)
        // invalidation-triggered re-query to catch up.
        _conversations.update { it.filterNot { conversation -> conversation.id == conversationId } }
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
        connectedRoomId = roomId
        // An already-cached conversation is by definition already a persisted row; a brand-new
        // one stays unpersisted (and its pre-send bot envelopes buffered, not written) until the
        // user sends something - see ensureConversationPersisted.
        conversationPersisted = hasCachedMessages
        if (!hasCachedMessages) pendingRawEnvelopes.clear()
        setActiveConversationId(conversationId)
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
                    "USER" -> appendMessage(ChatMessage(chatMsgId = cached.chatMsgId, text = cached.payload, fromUser = true, timeText = formatMessageTime(cached.createdAt)), cacheUserMessage = false)
                    "RAW" -> {
                        val event = runCatching { cacheJson.decodeFromString<RawSocketEnvelope>(cached.payload).toIncomingEvent() }.getOrNull()
                        if (event != null) {
                            handleEvent(event)
                            if (event is IncomingSocketEvent.BotMessage) {
                                attachCachedFeedback(cached.id, cached.liked, event.node.streamId)
                            }
                        }
                    }
                }
            }
        } finally {
            restoringFromCache = false
        }
        return hasCachedMessages
    }

    /** Stamps a just-replayed bot bubble with the `chat_messages` row backing it, and restores
     * any previously saved thumbs up/down - called right after [handleEvent] renders one RAW
     * cache entry, since that's the only place with both the row id and the resulting ChatMessage
     * in scope together (live dispatch instead threads this through [lastCachedRowId]). For a
     * streaming bubble made of several chunk rows, [liked] only overwrites the target's existing
     * value when non-null, so a later chunk's un-rated row can't clobber an earlier tap. */
    private fun attachCachedFeedback(rowId: Long, liked: Boolean?, streamId: String?) {
        _uiState.update { state ->
            val index = if (streamId != null) state.messages.indexOfLast { it.streamId == streamId } else state.messages.lastIndex
            if (index < 0) return@update state
            val target = state.messages[index]
            if (target.fromUser) return@update state
            state.copy(
                messages = state.messages.toMutableList().apply {
                    this[index] = target.copy(cacheRowId = rowId, liked = liked ?: target.liked)
                },
            )
        }
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
        if (!isRenderableBotEvent) return
        if (!conversationPersisted) {
            pendingRawEnvelopes += raw
            return
        }
        lastCachedRowId = runCatching {
            runBlocking(Dispatchers.IO) { cache.cacheRaw(conversationId, raw) }
        }.getOrNull()
    }

    /** Commits [connectedConversationId] as a real cache row the first time the user sends a
     * message in it, flushing whatever bot envelopes arrived beforehand (the welcome message,
     * typically) in their original order. A no-op once already persisted. Must run before that
     * message's own [appendMessage]/cacheUserMessage so the row exists for its foreign key. */
    private suspend fun ensureConversationPersisted() {
        if (conversationPersisted) return
        val conversationId = connectedConversationId ?: return
        cache.ensureConversationPersisted(botId, conversationId, connectedRoomId)
        conversationPersisted = true
        pendingRawEnvelopes.forEach { raw -> cache.cacheRaw(conversationId, raw) }
        pendingRawEnvelopes.clear()
    }

    fun openConversation(conversationId: String) {
        if (conversationId == activeConversationId) return
        viewModelScope.launch {
            setActiveConversationId(conversationId)
            previousHistoryCursor = null
            // Only the connected conversation has a live socket that could actually be typing a
            // reply - a merely-browsed one (this) never does, so any typing indicator left over
            // from whatever was connected before must not leak into this view.
            _uiState.update { it.copy(isArchived = false, isLiveChat = false, assignedAgent = null, isAgentTyping = false) }
            val roomId = _conversations.value.firstOrNull { it.id == conversationId }?.roomId
            restoreConversation(conversationId, roomId)
        }
    }

    /** Replays [conversationId]'s local cache if there is one; otherwise re-seeds it from the
     * server (see [activateConversation]'s doc for why cache always wins when present). The REST
     * fallback also covers a conversation with no local cache yet whose reply arrived on the live
     * socket while it wasn't the one on screen (see [handleEvent]'s doc): the server already has
     * it, even though nothing local does yet. */
    private suspend fun restoreConversation(conversationId: String, roomId: String?) {
        val hasCachedMessages = replayFromCache(conversationId)
        if (!hasCachedMessages && roomId != null) refreshConversationHistory(conversationId, roomId)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        // The empty-message guard only applies outside live chat; live chat allows an empty
        // submit through.
        if (text.isEmpty() && !_uiState.value.isLiveChat) return
        hasStartedConversation = true
        _uiState.update { it.copy(inputText = "") }
        viewModelScope.launch {
            // Browsing a different, older conversation and sending here must continue *that*
            // room, not whatever's still connected - switchToActiveRoomIfResumable reconnects
            // the live socket to it first when this device can (see its own doc); appendMessage's
            // snap-back only remains as the fallback for a room it can't resume this way.
            switchToActiveRoomIfResumable()
            // Even with no connectivity right now, still hand this to the repository rather than
            // failing it immediately - AckTracker retries the send for a couple of minutes, which
            // covers the common case of a blip that clears before the retries run out. The bubble
            // only ever flips to "Not delivered" once every retry has genuinely failed.
            val chatMsgId = repository.sendFreeText(text)
            appendMessage(ChatMessage(chatMsgId = chatMsgId, text = text, fromUser = true))
            // Show the typing/loading indicator right away instead of waiting on a server
            // typing_status event, which the bot flow (unlike a live human agent) doesn't
            // reliably send. Skipped in live chat, where an idle agent shouldn't appear typing.
            _uiState.update { if (it.isLiveChat) it else it.copy(isAgentTyping = true) }
        }
    }

    /** If the user is browsing a conversation other than the one actually connected, and this
     * device has a persisted session for that browsed room (i.e. it connected to it itself at
     * some point - see SessionStore.loadForRoom), reconnects the live socket to resume it. A
     * room this device never itself connected to (e.g. one only seen in another device's
     * history) can't be resumed this way - there is no way to rejoin a room without its session
     * token, confirmed against the live API - so this is a no-op for it, same as before this
     * existed. */
    private suspend fun switchToActiveRoomIfResumable() {
        val active = activeConversationId ?: return
        if (active == connectedConversationId) return
        val targetRoomId = _conversations.value.firstOrNull { it.id == active }?.roomId ?: return
        // Mirrors startNewChat()'s own isConnected=false - the input bar is wired to it
        // (see ChatScreen's ChatInputBar `enabled`) so nothing can be typed/sent into a room
        // mid-switch. onConnected() flips it back true once the new socket is actually up;
        // if switchToRoom turns out to be a no-op (no persisted session for this room, see its
        // own doc) there's no reconnect coming to do that, so it's restored here instead.
        _uiState.update { it.copy(isConnected = false) }
        val switched = repository.switchToRoom(targetRoomId) { resumedRoomId ->
            connectedConversationId = active
            connectedRoomId = resumedRoomId
            conversationPersisted = true
            true
        }
        if (!switched) _uiState.update { it.copy(isConnected = true) }
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
            val trimmedClientId = clientId?.trim()?.takeIf { it.isNotEmpty() }
            val trimmedApiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() }
            val trimmedEndUserId = endUserId?.trim()?.takeIf { it.isNotEmpty() }
            // Both history (rooms/list) and per-message feedback ride the same `third-party-tasks`
            // bearer-token auth - shared here so a host app that configures both doesn't fetch/
            // cache two independent tokens for the identical clientId/apiKey pair.
            val thirdParty = buildThirdPartyAuth(trimmedClientId, trimmedApiKey)
            val chatHistoryRepository = buildChatHistoryRepository(cache, thirdParty, trimmedClientId, trimmedApiKey, trimmedEndUserId)
            val messageFeedbackRepository = thirdParty?.let { (api, tokenManager) -> MessageFeedbackRepository(api, tokenManager) }
            return ChatViewModel(
                repository = ChatRepository(
                    baseUrl,
                    botId,
                    historyEnabled = historyEnabled,
                    sessionStore = SharedPreferencesSessionStore(context),
                ),
                botId = botId,
                cache = cache,
                chatHistoryRepository = chatHistoryRepository,
                messageFeedbackRepository = messageFeedbackRepository,
                suppressInitialBotMessages = suppressInitialBotMessages,
            ) as T
        }

        /** Builds the shared `third-party-tasks` API client + token manager once [clientId]/
         * [apiKey] are both set - null otherwise (feedback and history both stay disabled).
         * Doesn't log on its own; [buildChatHistoryRepository] below owns the one loud
         * misconfiguration warning shared by both features. */
        private fun buildThirdPartyAuth(clientId: String?, apiKey: String?): Pair<ThirdPartyTasksApiService, ThirdPartyTokenManager>? {
            if (clientId == null || apiKey == null) return null
            val api = ThirdPartyTasksApiService(baseUrl)
            return api to ThirdPartyTokenManager(api, clientId, apiKey)
        }

        /** History (the rooms list backed by the `third-party-tasks` API) requires [clientId],
         * [apiKey], [endUserId], and [botId] *all* set - there is no partially-configured mode.
         * Leaving all three third-party fields unset is a valid choice (history is simply off);
         * setting only some of them is almost certainly a host-app integration mistake, so it's
         * always logged loudly - but never crashes the host app, in any build type, since a
         * third-party SDK misconfiguration must never be able to take down the whole app.
         * Per-message feedback ([MessageFeedbackRepository]) only needs [clientId]/[apiKey] (see
         * [thirdParty]) and isn't gated by [endUserId] here - it has no concept of "whose room". */
        private fun buildChatHistoryRepository(
            cache: ChatCacheRepository,
            thirdParty: Pair<ThirdPartyTasksApiService, ThirdPartyTokenManager>?,
            clientId: String?,
            apiKey: String?,
            endUserId: String?,
        ): ChatHistoryRepository? {
            val hasAnyThirdPartyConfig = clientId != null || apiKey != null || endUserId != null
            if (thirdParty == null || clientId == null || endUserId == null || botId.isBlank()) {
                if (hasAnyThirdPartyConfig) {
                    Log.e(
                        "Chat360",
                        "Chat360 history is misconfigured: clientId, apiKey, and endUserId must ALL " +
                            "be set together (botId is already required) to enable the rooms/history " +
                            "list. Got clientId=${clientId != null}, apiKey=${apiKey != null}, " +
                            "endUserId=${endUserId != null}. History will stay disabled until all are provided.",
                    )
                }
                return null
            }
            val (thirdPartyApi, tokenManager) = thirdParty
            return ChatHistoryRepository(
                apiService = thirdPartyApi,
                tokenManager = tokenManager,
                cache = cache,
                clientId = clientId,
                botId = botId,
                endUserId = endUserId,
            )
        }
    }
}
