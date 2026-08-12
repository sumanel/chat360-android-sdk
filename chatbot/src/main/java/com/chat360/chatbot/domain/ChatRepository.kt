package com.chat360.chatbot.domain

import android.util.Log
import com.chat360.chatbot.common.models.ConfigService
import com.chat360.chatbot.domain.validation.InputValidators
import com.chat360.chatbot.domain.windowevent.WindowEventBridge
import com.chat360.chatbot.model.wire.AssignedAgent
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.model.wire.BotNode
import com.chat360.chatbot.model.wire.IncomingSocketEvent
import com.chat360.chatbot.model.wire.OutgoingMessage
import com.chat360.chatbot.model.wire.PingMessage
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import com.chat360.chatbot.model.wire.SystemJumpMessage
import com.chat360.chatbot.model.wire.toIncomingEvent
import com.chat360.chatbot.network.rest.Chat360ApiService
import com.chat360.chatbot.network.rest.dto.BotAppearanceDetails
import com.chat360.chatbot.network.rest.dto.HistoryResponse
import com.chat360.chatbot.network.rest.dto.SessionLanguage
import com.chat360.chatbot.network.rest.dto.details
import com.chat360.chatbot.network.ws.AckTracker
import com.chat360.chatbot.network.ws.Chat360WebSocketClient
import com.chat360.chatbot.network.ws.HeartbeatManager
import com.chat360.chatbot.network.ws.ReconnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/**
 * Session + connection orchestration. REST session-init must complete before the WebSocket
 * connects (ownerId/roomId only come from that response); reconnects reuse that same
 * ownerId/roomId rather than re-running session-init.
 */
class ChatRepository(
    private val baseUrl: String,
    private val botId: String,
    /** Host apps (or specific integrations) can turn conversation-history fetch off entirely. */
    private val historyEnabled: Boolean = true,
    private val apiService: Chat360ApiService = Chat360ApiService(baseUrl),
    private val wsClient: Chat360WebSocketClient = Chat360WebSocketClient(),
    /** Resumes the same room/session on the next [connect] (cold app launch) instead of always
     * allocating a new one - null disables this and every connect starts fresh, same as before.
     * Deliberately never consulted by [startNewSession] (see its own doc) - that's a real
     * user-initiated "start over". */
    private val sessionStore: SessionStore? = null,
) {
    // encodeDefaults is essential: most wire fields (type/user/replyType/chat_msg_id/...) are
    // Kotlin default values, and kotlinx.serialization omits defaults unless told otherwise -
    // the server silently ignores frames missing them. explicitNulls=false keeps absent
    // optional fields (targetId/currentId/post_data/...) off the wire entirely.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var ownerId: String? = null
    private var roomId: String? = null
    private var currentTargetId: String? = null
    private var lastBotNode: BotNode? = null
    private var pendingInitJumpTargetId: String? = null
    private var suppressReconnect = false
    private var manuallyDisconnected = false
    /** Tracked independently of ReconnectManager's own state - see [ensureReconnecting], which
     * needs to know "is there any point trying right now" without reaching into its internals. */
    private var isSocketOpen = false
    private var reconnectPending = false
    /** The last bot node actually dispatched - a dropped/flaky connection can redeliver the
     * exact same frame, which would otherwise render as a second identical bubble. Compared by
     * full node equality (not just nodeId): a looping flow node (e.g. an LLM/CUSTOMINPUT fallback)
     * legitimately reuses the same nodeId turn after turn with different generated text, and
     * keying on nodeId alone would silently swallow every one of those later replies. */
    private var lastDispatchedNode: BotNode? = null
    /** When [lastDispatchedNode] was set, so the dedup below only catches a same-node frame
     * arriving right on top of the last one (a redelivery artifact) rather than a genuine later
     * reply that just happens to fall back to identical wording/node - see [DUPLICATE_NODE_WINDOW_MS]. */
    private var lastDispatchedAt: Long = 0L

    private var onEvent: (IncomingSocketEvent) -> Unit = {}
    private var onConnected: () -> Unit = {}
    private var onError: (Throwable) -> Unit = {}
    private var onSlowConnectionChanged: (Boolean) -> Unit = {}
    private var onMessageTimedOut: (String) -> Unit = {}
    private var onOpenUrl: (String) -> Unit = {}
    private var onFeedbackRequested: () -> Unit = {}
    private var onRawIncoming: (String) -> Unit = {}
    private var onAppearanceLoaded: (BotAppearanceDetails?, String?) -> Unit = { _, _ -> }
    private var onSessionResumed: (Boolean, AssignedAgent?) -> Unit = { _, _ -> }
    private var onBotSettingsLoaded: (shortcuts: Map<String, String>, languages: List<SessionLanguage>) -> Unit = { _, _ -> }
    /** From session init's `configs.should_ask_feedback` - gates whether LiveChatEnded also closes the socket. */
    private var shouldAskFeedback: Boolean = false

    private val heartbeat = HeartbeatManager(
        scope = repoScope,
        sendPing = { wsClient.send(json.encodeToString(PingMessage(timestamp_int = System.currentTimeMillis()))) },
        onSlowConnectionChanged = { onSlowConnectionChanged(it) },
    )
    private val reconnectManager = ReconnectManager(
        scope = repoScope,
        reconnect = { openSocket() },
    )
    private val ackTracker = AckTracker(
        scope = repoScope,
        onTimeout = { chatMsgId -> onMessageTimedOut(chatMsgId) },
    )

    suspend fun connect(
        onEvent: (IncomingSocketEvent) -> Unit,
        onConnected: () -> Unit,
        onError: (Throwable) -> Unit,
        onSlowConnectionChanged: (Boolean) -> Unit = {},
        onMessageTimedOut: (String) -> Unit = {},
        onAppearanceLoaded: (BotAppearanceDetails?, chatboxName: String?) -> Unit = { _, _ -> },
        /** Called after session init so the local cache can select the matching conversation.
         * Return true when its cached messages were replayed; remote history is then skipped. */
        onConversationStarted: suspend (roomId: String) -> Boolean = { false },
        /** Every server envelope, including history/starter frames, for durable local replay. */
        onRawIncoming: (String) -> Unit = {},
        onOpenUrl: (String) -> Unit = {},
        /** Seeds live-chat state from the session itself (takeover/assigned_user) before any history replays - lets a killed-and-reopened app resume mid-live-chat correctly instead of assuming a fresh bot-flow session. */
        onSessionResumed: (takeover: Boolean, agent: AssignedAgent?) -> Unit = { _, _ -> },
        /** The session ended but is being held open (see LiveChatEnded below) so the UI can
         * show the post-chat survey first. */
        onFeedbackRequested: () -> Unit = {},
        /** The room's shortcut menu (label -> targetId) and available languages, straight from
         * session-init's `bot_settings`. */
        onBotSettingsLoaded: (shortcuts: Map<String, String>, languages: List<SessionLanguage>) -> Unit = { _, _ -> },
    ) {
        this.onEvent = onEvent
        this.onConnected = onConnected
        this.onError = onError
        this.onSlowConnectionChanged = onSlowConnectionChanged
        WindowEventBridge.registerSession { event -> sendWindowEvent(event) }
        this.onMessageTimedOut = onMessageTimedOut
        this.onOpenUrl = onOpenUrl
        this.onFeedbackRequested = onFeedbackRequested
        this.onRawIncoming = onRawIncoming
        this.onAppearanceLoaded = onAppearanceLoaded
        this.onSessionResumed = onSessionResumed
        this.onBotSettingsLoaded = onBotSettingsLoaded

        establishSession(onConversationStarted, sessionStore?.load(botId))
    }

    /**
     * Tears down the current room's socket/session state and re-runs session-init from
     * scratch, so the backend allocates a genuinely new room instead of resuming the one
     * [connect] already established. Unlike a dropped-connection reconnect (which
     * deliberately reuses ownerId/roomId, see the class doc), this is for a user-initiated
     * "start a new conversation" action, where reusing the old room would mix unrelated
     * conversations together in the same room history.
     */
    suspend fun startNewSession(onConversationStarted: suspend (roomId: String) -> Boolean = { false }) {
        Log.i(TAG, "Starting new session (user-initiated) - tearing down room=$roomId")
        teardownForResession()
        establishSession(onConversationStarted, persisted = null)
    }

    /**
     * Reconnects the live socket to a different, already-known room - one this device has
     * connected to before and so has a persisted session for (see [SessionStore.loadForRoom]) -
     * instead of the room [connect]/[startNewSession] last established. Unlike those, this
     * *resumes* [targetRoomId]'s own history/session rather than allocating a new room.
     *
     * There is no way to resume a room without its session token - confirmed against the live
     * API, `room_id` alone is silently ignored and a fresh room gets allocated instead - so this
     * is a no-op (returns false) for any room this device never actually connected to itself
     * (e.g. one only ever seen in another device's history). Callers should fall back to
     * whatever they'd otherwise do when this returns false.
     */
    suspend fun switchToRoom(targetRoomId: String, onConversationStarted: suspend (roomId: String) -> Boolean = { false }): Boolean {
        val persisted = sessionStore?.loadForRoom(botId, targetRoomId) ?: return false
        Log.i(TAG, "Switching to room=$targetRoomId (tearing down room=$roomId)")
        teardownForResession()
        establishSession(onConversationStarted, persisted)
        return true
    }

    /** Shared by [startNewSession] and [switchToRoom] - both tear down the current room's
     * socket/session state before [establishSession] re-runs session-init for a different room. */
    private fun teardownForResession() {
        // Suppresses handleClosed()'s auto-reconnect for this intentional close. Left set
        // until establishSession() is about to reopen the socket (not reset immediately
        // here) since wsClient.close() delivers its onClosed callback asynchronously - if it
        // lands after this function returns, an early reset would let handleClosed() treat
        // it as an unexpected drop and schedule a redundant reconnect of the new room.
        manuallyDisconnected = true
        heartbeat.stop()
        reconnectManager.cancel()
        ackTracker.cancelAll()
        wsClient.close()

        ownerId = null
        roomId = null
        currentTargetId = null
        lastBotNode = null
        pendingInitJumpTargetId = null
        shouldAskFeedback = false
    }

    /** [persisted] is the session offered to the backend to resume - the last-connected one from
     * [connect] (a cold app launch legitimately wants its last conversation back), a specific
     * older room from [switchToRoom], or null from [startNewSession] (an explicit "start over"
     * must never resume the room it's tearing down). */
    private suspend fun establishSession(onConversationStarted: suspend (roomId: String) -> Boolean, persisted: PersistedSession?) {
        try {
            // website_url/current_url must be URL-shaped - the backend 400s on non-URL-shaped
            // values like a bare app identifier, so the bot host itself is sent here.
            val host = baseUrl.substringAfter("://")
            val session = apiService.getSession(
                botId = botId,
                websiteUrl = host,
                currentUrl = "$baseUrl/web_bot/?h=$botId",
                roomId = persisted?.roomId,
                sessionId = persisted?.sessionToken,
            )
            ownerId = session.owner_id
            roomId = session.room_id
            currentTargetId = session.targetId
            Log.i(TAG, "Session established: owner=${session.owner_id} room=${session.room_id}")
            sessionStore?.save(botId, PersistedSession(session.room_id, session.session_token, session.owner_id))
            shouldAskFeedback = session.configs?.should_ask_feedback ?: false
            // An INIT node means the flow hasn't started: after the socket opens, jump to the
            // session's targetId so the bot emits its first message. Confirmed against the live
            // API that `nodeType` stays "INIT" even when resuming a room that already has real
            // history - so this alone can't tell a genuinely fresh room from a resumed one; the
            // hadHistory check below is what actually suppresses the jump on resume.
            if (session.nodeType == "INIT") pendingInitJumpTargetId = session.targetId

            val resumedAgent = session.assigned_user?.let {
                if (it.operator_name.isNullOrBlank() && it.user_designation.isNullOrBlank() && it.avatar.isNullOrBlank()) {
                    null
                } else {
                    AssignedAgent(name = it.operator_name, designation = it.user_designation, avatarUrl = it.avatar)
                }
            }
            onSessionResumed(session.takeover, resumedAgent)
            val shortcuts = runCatching {
                session.bot_settings?.get("bot_shortcuts")
                    ?.let { json.decodeFromJsonElement(MapSerializer(String.serializer(), String.serializer()), it) }
            }.getOrNull().orEmpty()
            val languages = runCatching {
                session.bot_settings?.get("languages")
                    ?.let { json.decodeFromJsonElement(ListSerializer(SessionLanguage.serializer()), it) }
            }.getOrNull().orEmpty()
            onBotSettingsLoaded(shortcuts, languages)

            fetchAppearance(host, onAppearanceLoaded)
            val hadHistory = onConversationStarted(session.room_id)
            if (hadHistory) {
                // A resumed room already has its opening message(s) - re-jumping to targetId
                // would re-request (and duplicate-render) the same first node on every app
                // reopen, on top of the real history just replayed above.
                pendingInitJumpTargetId = null
            } else if (loadConversationStarter(onRawIncoming)) {
                // Conversation-starter teaser content applies when a room has no history yet:
                // show it as the opening bubbles instead of an empty WelcomeSplash, using the
                // exact same wire parsing as any other frame. Suppressing the pending INIT jump
                // while starter content is shown avoids re-requesting (and re-rendering) the
                // exact same first node the starter fetch just displayed.
                pendingInitJumpTargetId = null
            }
            openSocket()
        } catch (e: Exception) {
            onError(e)
        }
    }

    /** Fetches the most recent page for cache-first conversation seeding - only ever called
     * when the caller has no local messages yet (see ChatViewModel.activateConversation's doc).
     * Carries [HistoryResponse.previous_cursor] so the caller can page further back on demand. */
    suspend fun fetchHistory(roomId: String): HistoryResponse =
        if (historyEnabled) apiService.getHistory(roomId) else HistoryResponse()

    /** Pages one step further back from [cursor] (a prior response's `previous_cursor`).
     * Additive only: the caller prepends this page, it never replaces anything already shown. */
    suspend fun fetchMoreHistory(roomId: String, cursor: Int): HistoryResponse =
        if (historyEnabled) apiService.getHistory(roomId, taskType = "PREVIOUS", taskValue = cursor) else HistoryResponse()

    /** Returns true if any history was found (and dispatched), so callers can fall back to conversation-starter content on a genuinely fresh room. */
    private suspend fun loadHistory(onRawIncoming: (String) -> Unit): Boolean {
        val room = roomId ?: return false
        return try {
            val response = apiService.getHistory(room)
            response.history.forEach { item ->
                onRawIncoming(json.encodeToString(item))
                onEvent(item.toIncomingEvent())
            }
            response.history.isNotEmpty()
        } catch (e: Exception) {
            // Non-fatal: a fresh room has no history yet, and a failed fetch shouldn't block
            // connecting - the live socket is the source of truth either way.
            false
        }
    }

    /** Best-effort like loadHistory()/fetchAppearance() - a failed/empty fetch just means no
     * starter bubbles, never blocks connecting. Returns whether any starter content actually
     * rendered, so the caller knows whether the pending INIT jump is now redundant. */
    private suspend fun loadConversationStarter(onRawIncoming: (String) -> Unit): Boolean {
        return try {
            val items = apiService.getFirstMessages(botId)
            items.forEach { item ->
                onRawIncoming(json.encodeToString(item))
                val event = item.toIncomingEvent()
                if (event is IncomingSocketEvent.BotMessage) {
                    lastBotNode = event.node
                    currentTargetId = event.node.targetId ?: currentTargetId
                }
                onEvent(event)
            }
            items.isNotEmpty()
        } catch (e: Exception) {
            // Non-fatal - the real session's own first message still arrives once the socket opens.
            false
        }
    }

    /**
     * Best-effort like loadHistory(): a failed/missing appearance config just means the caller
     * keeps whichever theme preset/custom colors it already resolved - never blocks connecting.
     */
    private suspend fun fetchAppearance(
        host: String,
        onAppearanceLoaded: (BotAppearanceDetails?, String?) -> Unit,
    ) {
        try {
            val response = apiService.getBotAppearance(
                botId = botId,
                websiteUrl = host,
                subdomainUrl = "$baseUrl/web_bot/?h=$botId",
            )
            onAppearanceLoaded(response.details(json), response.chatboxname)
        } catch (e: Exception) {
            onAppearanceLoaded(null, null)
        }
    }

    /** User-triggered "refresh this chat" action - reconnects immediately with the same
     * ownerId/roomId (unlike [startNewSession], which gets a whole new room), bypassing
     * [ReconnectManager]'s backoff delay. A no-op if the session hasn't been established yet
     * (nothing to reconnect to). */
    fun reconnectNow() {
        if (ownerId == null || roomId == null) return
        Log.i(TAG, "Manual reconnect requested (room=$roomId)")
        // See startNewSession()'s doc: manuallyDisconnected stays set across the close so a
        // delayed async onClosed from the old socket can't slip through and schedule a
        // redundant backoff reconnect; openSocket() clears it right before reopening.
        manuallyDisconnected = true
        reconnectManager.cancel()
        wsClient.close()
        openSocket()
    }

    private fun openSocket() {
        val oId = ownerId ?: return
        val rId = roomId ?: return
        // Only meaningful when startNewSession() left it set - a plain connect() call
        // already starts false. Reset here rather than right after wsClient.close() so a
        // delayed async onClosed from the just-closed old socket can't slip through and
        // trigger a redundant reconnect for the room we're about to open.
        manuallyDisconnected = false
        val wsScheme = if (baseUrl.startsWith("https")) "wss" else "ws"
        val host = baseUrl.substringAfter("://")
        val wsUrl = "$wsScheme://$host/ws/chat_updated/$oId/$rId"
        Log.i(TAG, "Opening socket for owner=$oId room=$rId")

        wsClient.connect(
            wsUrl = wsUrl,
            onOpen = {
                Log.i(TAG, "Connected (owner=$oId room=$rId)")
                isSocketOpen = true
                reconnectPending = false
                heartbeat.start()
                reconnectManager.onConnected()
                onConnected()
                pendingInitJumpTargetId?.let { targetId ->
                    pendingInitJumpTargetId = null
                    sendSystemJump(targetId)
                }
            },
            onMessage = { raw -> handleIncoming(raw, onRawIncoming) },
            onClosed = { code, reason -> handleClosed(code, reason) },
            onFailure = { t ->
                handleClosed(null, t.message)
                onError(t)
            },
        )
    }

    private fun handleClosed(code: Int?, reason: String?) {
        isSocketOpen = false
        reconnectPending = false
        heartbeat.stop()
        if (manuallyDisconnected) {
            Log.i(TAG, "Disconnected (manual, code=$code reason=$reason) - no reconnect")
        } else {
            Log.w(TAG, "Disconnected unexpectedly (code=$code reason=$reason) - scheduling reconnect (suppressed=$suppressReconnect)")
            reconnectManager.scheduleReconnect(suppress = suppressReconnect)
        }
    }

    /** A send just found the socket closed - reconnect immediately instead of letting the
     * retry (see [sendTracked]/AckTracker) just keep calling send() into a dead socket for
     * minutes with nothing ever actually re-establishing the connection. Mirrors the web
     * widget's WSService, which only ever completes a send once the socket is genuinely open
     * (queuing otherwise - see its _send/_onOpen). Debounced against an already-open or
     * already-reconnecting socket so repeated failed sends (including AckTracker's own retries
     * of the same message) don't hammer the connection with redundant reopen attempts. */
    private fun ensureReconnecting() {
        if (isSocketOpen || reconnectPending) return
        if (ownerId == null || roomId == null) return
        reconnectPending = true
        Log.i(TAG, "Send found the socket closed - reconnecting now (room=$roomId)")
        manuallyDisconnected = true
        reconnectManager.cancel()
        wsClient.close()
        openSocket()
    }

    private fun handleIncoming(raw: String, onRawIncoming: (String) -> Unit) {
        val envelope = json.decodeFromString(RawSocketEnvelope.serializer(), raw)
        // A slow reply (this bot's flow can take several seconds) can land after the user has
        // already started a new chat or switched rooms, which tears down and replaces roomId -
        // the frame is for a room nothing here is connected to anymore. Dropping it here, keyed
        // off the frame's own room_id, is what actually prevents it from leaking into whatever
        // room is connected by the time it arrives (ChatViewModel's activeConversationId check
        // alone isn't enough - startNewChat sets both active and connected to the new room
        // together, so a stale frame processed in between would pass that check too).
        // ack/pong frames don't carry room_id at all and are unaffected by this.
        if (envelope.room_id != null && envelope.room_id != roomId) {
            Log.w(TAG, "Dropping frame for room=${envelope.room_id} - no longer connected (current room=$roomId)")
            return
        }
        heartbeat.onMessageReceived(isPong = envelope.type == "pong")

        val event = envelope.toIncomingEvent()
        // Agent-authored messages are excluded from this dedup: an agent legitimately repeating
        // themselves shouldn't be swallowed, and admin/operator frames don't carry the same
        // node-id-per-flow-step guarantee bot nodes do. Full node equality (not just nodeId) so a
        // looping flow node that lands on the same nodeId with genuinely new text/content still
        // gets through - only a byte-identical redelivered frame is treated as a duplicate.
        // Gated by [DUPLICATE_NODE_WINDOW_MS] too: a real conversational turn (e.g. the bot's
        // own unrecognized-input fallback landing back on the same node the welcome message
        // used) must never be silently dropped just because it matches byte-for-byte - only a
        // frame arriving right on top of the last one (a redelivery artifact, not a fresh reply)
        // is treated as a duplicate.
        val now = System.currentTimeMillis()
        if (event is IncomingSocketEvent.BotMessage &&
            event.node.author != BotNode.MessageAuthor.AGENT &&
            event.node.nodeId != null &&
            event.node == lastDispatchedNode &&
            (now - lastDispatchedAt) < DUPLICATE_NODE_WINDOW_MS
        ) {
            Log.d(TAG, "Duplicate bot frame dropped (redelivered): nodeId=${event.node.nodeId}")
            return
        }
        onRawIncoming(raw)
        when (event) {
            is IncomingSocketEvent.BotMessage -> {
                Log.i(
                    TAG,
                    "Bot reply received: nodeId=${event.node.nodeId} nodeType=${event.node.nodeType} " +
                        "author=${event.node.author} text=${event.node.text}",
                )
                lastDispatchedNode = event.node
                lastDispatchedAt = now
                lastBotNode = event.node
                currentTargetId = event.node.targetId ?: currentTargetId
                handleWindowEventNode(event.node.content)
                // END-node side effects - not about rendering. urlMessage opens externally in
                // addition to whatever bubble/nothing the node's own text produces; end_session
                // closes the socket.
                event.node.endUrlMessage?.let { onOpenUrl(it) }
                if (event.node.endSessionRequested) disconnect()
            }
            is IncomingSocketEvent.Ack -> {
                Log.d(TAG, "Ack received: chat_msg_id=${event.chatMsgId}")
                ackTracker.acknowledge(event.chatMsgId)
            }
            // The server also uses the echoed end_user message itself as a delivery ack,
            // not only the explicit `ack` type.
            is IncomingSocketEvent.EchoedUserMessage -> {
                Log.d(TAG, "User message echoed/acked: chat_msg_id=${event.chatMsgId} text=${event.text}")
                ackTracker.acknowledge(event.chatMsgId)
            }
            is IncomingSocketEvent.CloseConnection -> {
                Log.w(TAG, "Server requested close_connection (suppressReconnect=${event.suppressReconnect})")
                if (event.suppressReconnect) suppressReconnect = true
            }
            // Closes the socket outright unless the session is configured to ask for
            // post-chat feedback afterward, in which case the prompt surfaces immediately
            // instead of waiting on an in-chrome close affordance.
            is IncomingSocketEvent.LiveChatEnded -> {
                Log.i(TAG, "Live chat ended (shouldAskFeedback=$shouldAskFeedback)")
                if (!shouldAskFeedback) disconnect() else onFeedbackRequested()
            }
            else -> Unit
        }
        onEvent(event)
    }

    /**
     * A WINDOW_EVENT node's shouldSend fires the host's handleWindowEvent callback; its return
     * value is fed straight back through the same "currently receiving" gate as an explicit
     * sendEventToBot() call would use.
     */
    private fun handleWindowEventNode(content: BotContent) {
        if (content !is BotContent.WindowEvent) {
            WindowEventBridge.setReceiving(false)
            return
        }
        WindowEventBridge.setReceiving(content.shouldReceive)
        if (content.shouldSend) {
            val response = WindowEventBridge.dispatchToHost(
                ConfigService.WebEventHandler.handleWindowEvent,
                content.sendData,
            )
            if (response.isNotEmpty()) WindowEventBridge.sendToActiveSession(response)
        }
    }

    /** The inbound half: an event handed to the active session becomes an outgoing message
     * carrying it as `variables`. */
    private fun sendWindowEvent(event: Map<String, String>) {
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = JsonObject(emptyMap()),
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            variables = event,
        )
        sendTracked(outgoing)
    }

    /** Advances the bot flow without a user-authored message - used by IFRAME's postMessage bridge. */
    fun jumpToNode(targetId: String) = sendSystemJump(targetId)

    /** Answers a tap on the shortcuts menu (label -> targetId, from session-init's
     * `bot_settings`) - unlike [jumpToNode], this is user-authored and gets its own chat
     * bubble showing the label. */
    fun sendShortcut(targetId: String, label: String): String {
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = JsonPrimitive(label),
            bot_id = botId,
            targetId = targetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
        )
        return sendTracked(outgoing)
    }

    private fun sendSystemJump(targetId: String) {
        val jump = SystemJumpMessage(
            data = SystemJumpMessage.JumpData(
                target_id = targetId,
                currentUrl = "$baseUrl/web_bot/?h=$botId",
            ),
            bot_id = botId,
            room_id = roomId,
        )
        wsClient.send(json.encodeToString(jump))
    }

    /**
     * Returns the generated chat_msg_id so the UI can tag its optimistic bubble for ack-timeout.
     * Node context (currentId/nodeType/post_data) comes from the last bot node - LLM/webhook
     * nodes won't answer without it.
     */
    fun sendFreeText(text: String): String {
        val sanitized = InputValidators.sanitizeInput(text)
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = JsonPrimitive(sanitized),
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            post_data = JsonPrimitive(sanitized),
            variables = node?.variable?.let { mapOf(it to sanitized) },
        )
        return sendTracked(outgoing)
    }

    /** Answers an EMAIL node - plain sanitized text through the same generic path as free text. */
    fun sendEmail(email: String): String = sendFreeText(email)

    /**
     * Answers a plain (non-international) PHONE node - no dedicated payload shape, no
     * client-side validation, just free text.
     */
    fun sendPhone(value: String): String = sendFreeText(value)

    /**
     * Answers an international PHONE node with `splitVariable` set: country code and national
     * number land in two separate variables, with `doNotUpdateVariable`/`multiple_vars` set so
     * the server keeps them apart.
     */
    fun sendSplitPhone(countryCode: String, nationalNumber: String, countryCodeVar: String): String {
        val node = lastBotNode
        val displayValue = countryCode + nationalNumber
        val variables = buildMap {
            put(countryCodeVar, countryCode)
            node?.variable?.let { put(it, nationalNumber) }
        }
        val outgoing = OutgoingMessage(
            message = JsonPrimitive(displayValue),
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            post_data = JsonPrimitive(displayValue),
            variables = variables,
            doNotUpdateVariable = true,
            multiple_vars = true,
        )
        return sendTracked(outgoing)
    }

    /** Answers the AUTOSUGGESTION sub-case of CUSTOMINPUT - a plain-text reply of the picked choice. */
    fun sendAutoSuggestion(choice: String): String = sendFreeText(choice)

    /** Answers a standalone DATE node - {type:'date', value, format}. */
    fun sendDate(formattedDate: String, format: String): String {
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "date")
                put("value", formattedDate)
                put("format", format)
            },
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            post_data = JsonPrimitive(formattedDate),
            variables = node?.variable?.let { mapOf(it to formattedDate) },
        )
        return sendTracked(outgoing)
    }

    /** Answers a standalone TIME node - plain formatted string through the generic free-text path. */
    fun sendTime(formattedTime: String): String = sendFreeText(formattedTime)

    /** Answers a MULTIPLE_CHECK_BOX node - {type:'checkbox-options', value: Boolean[], text}. */
    fun sendCheckboxOptions(allOptions: List<BotContent.MultiOption.Option>, checkedIndices: Set<Int>): String {
        val node = lastBotNode
        val text = allOptions.filter { checkedIndices.contains(it.index) }.joinToString(", ") { it.text }
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "checkbox-options")
                put("value", JsonArray(allOptions.map { JsonPrimitive(checkedIndices.contains(it.index)) }))
                put("text", text)
            },
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            post_data = JsonPrimitive(text),
            variables = node?.variable?.let { mapOf(it to text) },
        )
        return sendTracked(outgoing)
    }

    /**
     * Answers an IMAGE_BUTTON reply-type button. `submitType == "IMAGE_AND_BUTTON"` echoes the
     * tapped card's image back as a MEDIA-type user message; web_url-type buttons never reach
     * this (the UI opens them externally instead).
     */
    fun sendImageButton(card: BotContent.ImageButtons.Card, button: BotContent.ImageButtons.Button, submitType: String): String {
        val node = lastBotNode
        val message = if (submitType == "IMAGE_AND_BUTTON") {
            buildJsonObject {
                put("type", "media")
                put("mediaLink", card.imageUrl)
                put("message", button.text)
            }
        } else {
            JsonPrimitive(button.text)
        }
        val outgoing = OutgoingMessage(
            message = message,
            bot_id = botId,
            targetId = button.targetId ?: node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            variables = node?.variable?.let { mapOf(it to (button.value ?: button.text)) },
            shouldValidate = false,
        )
        return sendTracked(outgoing)
    }

    /**
     * Answers a TEXT_CAROUSEL card/button/dynamic-pill tap - all share the same
     * {type:'carousel-text-reply', text, clickedIndex} shape, only [targetId] differs by source.
     * "link"-type CTA/card taps never reach this (the UI opens them externally instead).
     */
    fun sendTextCarouselReply(text: String, clickedIndex: Int, targetId: String?): String {
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "carousel-text-reply")
                put("text", text)
                put("clickedIndex", clickedIndex)
            },
            bot_id = botId,
            targetId = targetId ?: node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            variables = node?.variable?.let { mapOf(it to text) },
        )
        return sendTracked(outgoing)
    }

    /**
     * Answers a MULTI_CHOICE node: message = {type:'multichoice-option', value: index+1, text},
     * post_data = display text, currentId = the node's id, targetId = the clicked button's own
     * targetId (falling back to the node's), shouldValidate = false.
     */
    fun sendQuickReply(option: BotContent.MultiChoice.Option): String {
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "multichoice-option")
                put("value", option.index + 1)
                put("text", option.text)
            },
            bot_id = botId,
            targetId = option.targetId ?: node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            post_data = JsonPrimitive(option.text),
            variables = node?.variable?.let { mapOf(it to option.text) },
            shouldValidate = false,
        )
        return sendTracked(outgoing)
    }

    /**
     * Uploads a file then answers the current node with it: message = {type:'file-upload',
     * value, fileName} where value is the uploaded URL(s) joined by "\n", skipPostData (no
     * post_data field), variable set from the node if present.
     */
    suspend fun uploadAndSendFile(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        onProgress: (Int) -> Unit,
    ): String {
        val room = roomId ?: throw IllegalStateException("Not connected")
        val urls = apiService.uploadMedia(room, botId, fileBytes, fileName, mimeType, onProgress)
        val value = urls.joinToString("\n")
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "file-upload")
                put("value", value)
                put("fileName", fileName)
            },
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            variables = node?.variable?.let { mapOf(it to value) },
            shouldValidate = false,
        )
        sendTracked(outgoing)
        return value
    }

    /**
     * Uploads a recorded voice note then answers the current node with it: upload via the same
     * media endpoint as [uploadAndSendFile], but the outgoing message is free text (the
     * transcript, usually empty) carrying voiceUrl/transcript in componentSpecificData rather
     * than a `{type:'file-upload',...}` message body. Returns the uploaded URL so the UI can
     * show the sent bubble immediately without waiting on an echo.
     */
    suspend fun uploadAndSendVoiceMessage(
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        transcript: String,
        onProgress: (Int) -> Unit,
    ): String {
        val room = roomId ?: throw IllegalStateException("Not connected")
        val urls = apiService.uploadMedia(room, botId, fileBytes, fileName, mimeType, onProgress)
        val voiceUrl = urls.firstOrNull() ?: throw IllegalStateException("No voice URL returned from upload")
        val node = lastBotNode
        val sanitized = InputValidators.sanitizeInput(transcript)
        val outgoing = OutgoingMessage(
            message = JsonPrimitive(sanitized),
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            post_data = JsonPrimitive(sanitized),
            variables = node?.variable?.let { mapOf(it to sanitized) },
            componentSpecificData = buildJsonObject {
                put("voiceUrl", voiceUrl)
                put("transcript", sanitized)
                put("msgType", "VOICE_MESSAGE")
            },
        )
        sendTracked(outgoing)
        return voiceUrl
    }

    /**
     * Uploads a MEDIA-type FORM field out-of-band, returning the uploaded URL - the field's own
     * value stays local (part of the draft form) until the whole form is submitted, unlike
     * [uploadAndSendFile] which sends immediately. Uploading eagerly on pick (rather than
     * deferring to submit time) avoids repeating the same network call for no behavioral
     * difference.
     */
    suspend fun uploadFormMedia(fileBytes: ByteArray, fileName: String, mimeType: String, onProgress: (Int) -> Unit): String {
        val room = roomId ?: throw IllegalStateException("Not connected")
        val urls = apiService.uploadMedia(room, botId, fileBytes, fileName, mimeType, onProgress)
        return urls.joinToString("\n")
    }

    /**
     * Answers a RATING node by sending the plain 1-based index string through the normal
     * free-text path - not a distinct wire shape, just free text with the current node's
     * context attached.
     */
    fun sendRating(value: Int): String {
        val node = lastBotNode
        val text = value.toString()
        val outgoing = OutgoingMessage(
            message = JsonPrimitive(text),
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            post_data = JsonPrimitive(text),
            variables = node?.variable?.let { mapOf(it to text) },
        )
        return sendTracked(outgoing)
    }

    /**
     * Answers a FORM node: message = {type: 'form-response', formValue: [...]} with one entry
     * per field in index order, plus a variables map built from each field's own `variable`
     * name. MEDIA fields' [fileNames] entry gets folded into their formValue as
     * "{fileName}:-{uploadedUrl}" (see [uploadFormMedia]).
     */
    fun sendFormResponse(
        values: Map<Int, String>,
        fields: List<BotContent.Form.Field>,
        fileNames: Map<Int, String> = emptyMap(),
    ): String {
        val node = lastBotNode
        val ordered = fields.sortedBy { it.index }
        // A MEDIA field's formValue entry is "{fileName}:-{uploadedUrl}"; its variable is still
        // just the plain URL (values[field.index]).
        val formValue = ordered.map { field ->
            val value = values[field.index].orEmpty()
            if (field.type == BotContent.Form.FieldType.MEDIA && value.isNotBlank()) {
                "${fileNames[field.index].orEmpty()}:-$value"
            } else {
                value
            }
        }
        val variables = ordered.mapNotNull { field ->
            field.variable?.let { it to values[field.index].orEmpty() }
        }.toMap()
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "form-response")
                put("formValue", JsonArray(formValue.map { JsonPrimitive(it) }))
            },
            bot_id = botId,
            targetId = node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
            variables = variables.ifEmpty { null },
        )
        return sendTracked(outgoing)
    }

    /**
     * Submits the post-chat configurable feedback survey: message = {rating, feedback,
     * type:'feedback'}, nodeType: 'feedback', no targetId/currentId/variables - this is an
     * out-of-band message, not an answer to the current flow node. [feedbackText] is the
     * already-concatenated form-field text (every dynamic field's formatted value joined by
     * newlines) - this path never sends a separate structured per-field map.
     */
    fun sendConfigurableFeedback(rating: Int?, feedbackText: String) {
        val sanitizedFeedback = InputValidators.sanitizeInput(feedbackText)
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "feedback")
                put("rating", rating?.toString() ?: "")
                put("feedback", sanitizedFeedback)
            },
            bot_id = botId,
            targetId = null,
            room_id = roomId,
            nodeType = "feedback",
        )
        sendTracked(outgoing)
        disconnect()
    }

    /**
     * Answers a WELCOME_SCREEN card tap. [clickedIndexOneBased] is sent as a STRING (unlike
     * TEXT_CAROUSEL's numeric clickedIndex). [ctaTargetId] overrides the node's own targetId
     * only for `ctaType=="component"` cards; `external_link` cards still submit with the default.
     */
    fun sendWelcomeCard(cardTitle: String, clickedIndexOneBased: Int, ctaTargetId: String? = null): String {
        val node = lastBotNode
        val outgoing = OutgoingMessage(
            message = buildJsonObject {
                put("type", "welcome-card-reply")
                put("text", cardTitle)
                put("clickedIndex", clickedIndexOneBased.toString())
                put("reply_type", "free_text")
            },
            bot_id = botId,
            targetId = ctaTargetId ?: node?.targetId ?: currentTargetId,
            room_id = roomId,
            currentId = node?.nodeId,
            nodeType = node?.nodeType,
        )
        return sendTracked(outgoing)
    }

    private fun sendTracked(outgoing: OutgoingMessage): String {
        Log.i(
            TAG,
            "User message sent: chat_msg_id=${outgoing.chat_msg_id} nodeType=${outgoing.nodeType} " +
                "targetId=${outgoing.targetId} message=${outgoing.message}",
        )
        val payload = json.encodeToString(outgoing)
        if (!wsClient.send(payload)) ensureReconnecting()
        ackTracker.trackSend(outgoing.chat_msg_id) {
            if (!wsClient.send(payload)) ensureReconnecting()
        }
        return outgoing.chat_msg_id
    }

    fun disconnect() {
        Log.i(TAG, "Disconnecting (manual, final) - room=$roomId")
        manuallyDisconnected = true
        heartbeat.stop()
        reconnectManager.cancel()
        ackTracker.cancelAll()
        wsClient.close()
        repoScope.cancel()
        WindowEventBridge.unregisterSession()
    }

    private companion object {
        const val TAG = "Chat360WS"
        /** How soon a byte-identical bot node has to arrive after the last one to be treated as
         * a redelivery artifact rather than a genuine new reply - see [lastDispatchedAt]. */
        const val DUPLICATE_NODE_WINDOW_MS = 2_000L
    }
}
