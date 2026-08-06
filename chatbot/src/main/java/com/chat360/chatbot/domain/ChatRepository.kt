package com.chat360.chatbot.domain

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
import com.chat360.chatbot.network.rest.dto.details
import com.chat360.chatbot.network.ws.AckTracker
import com.chat360.chatbot.network.ws.Chat360WebSocketClient
import com.chat360.chatbot.network.ws.HeartbeatManager
import com.chat360.chatbot.network.ws.ReconnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.util.Log

/**
 * Session + connection orchestration. REST session-init must complete before the WebSocket
 * connects (ownerId/roomId only come from that response); reconnects reuse that same
 * ownerId/roomId rather than re-running session-init (matches createSocketUrl() in
 * layout/index.tsx, which reuses `session?.ownerId`/`session?.roomId`).
 */
class ChatRepository(
    private val baseUrl: String,
    private val botId: String,
    /** Host apps (or specific integrations) can turn conversation-history fetch off entirely. */
    private val historyEnabled: Boolean = true,
    private val apiService: Chat360ApiService = Chat360ApiService(baseUrl),
    private val wsClient: Chat360WebSocketClient = Chat360WebSocketClient(),
) {
    // encodeDefaults is essential: most wire fields (type/user/replyType/chat_msg_id/...) are
    // Kotlin default values, and kotlinx.serialization omits defaults unless told otherwise -
    // the server silently ignores frames missing them. explicitNulls=false keeps absent
    // optional fields (targetId/currentId/post_data/...) off the wire entirely, matching how
    // the widget conditionally adds them.
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

    private var onEvent: (IncomingSocketEvent) -> Unit = {}
    private var onConnected: () -> Unit = {}
    private var onError: (Throwable) -> Unit = {}
    private var onSlowConnectionChanged: (Boolean) -> Unit = {}
    private var onMessageTimedOut: (String) -> Unit = {}
    private var onOpenUrl: (String) -> Unit = {}
    private var onFeedbackRequested: () -> Unit = {}
    private var onRawIncoming: (String) -> Unit = {}
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
        /** Mirrors the widget's shouldAskFeedback-gated close: the session ended but is being
         * held open (see LiveChatEnded below) so the UI can show the post-chat survey first. */
        onFeedbackRequested: () -> Unit = {},
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

        try {
            // The widget derives website_url/current_url from the browser's own window.location,
            // which always points at the chat360-hosted page regardless of who embeds it - a
            // native client mimics that with the bot host itself (the backend 400s on
            // non-URL-shaped values like a bare app identifier).
            val host = baseUrl.substringAfter("://")
            val session = apiService.getSession(
                botId = botId,
                websiteUrl = host,
                currentUrl = "$baseUrl/web_bot/?h=$botId",
            )
            ownerId = session.owner_id
            roomId = session.room_id
            currentTargetId = session.targetId
            shouldAskFeedback = session.configs?.should_ask_feedback ?: false
            // An INIT node means the flow hasn't started: after the socket opens, jump to the
            // session's targetId so the bot emits its first message (layout/index.tsx does the
            // same via sendSocketMessage when lastMessage.msgType === 'INIT').
            if (session.nodeType == "INIT") pendingInitJumpTargetId = session.targetId

            Log.d("Sanket", "rooId = $roomId")
            Log.d("Sanket", "session.room_id = ${session.room_id}")
            Log.d("Sanket", "ownerId = ${session.owner_id}")
            Log.d("Sanket", "targetId = ${session.targetId}")

            val resumedAgent = session.assigned_user?.let {
                if (it.operator_name.isNullOrBlank() && it.user_designation.isNullOrBlank() && it.avatar.isNullOrBlank()) {
                    null
                } else {
                    AssignedAgent(name = it.operator_name, designation = it.user_designation, avatarUrl = it.avatar)
                }
            }
            onSessionResumed(session.takeover, resumedAgent)

            fetchAppearance(host, onAppearanceLoaded)
            val hadCachedHistory = onConversationStarted(session.room_id)
            val hadHistory = hadCachedHistory || if (historyEnabled) loadHistory(onRawIncoming) else false
            // ConversationStarter/index.tsx shows its teaser bubbles independently of the real
            // session, floating outside the (not-yet-opened) chat launcher on the host page. This
            // SDK has no such closed-launcher state - a host app only shows the chat screen once
            // it's ready to be a real session - so the closest equivalent moment is "this room
            // has no history yet": show the starter content as the opening bubbles instead of an
            // empty WelcomeSplash, using the exact same wire parsing as any other frame.
            if (!hadHistory) loadConversationStarter(onRawIncoming)
            openSocket()
        } catch (e: Exception) {
            onError(e)
        }
    }

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

    /** Best-effort like loadHistory()/fetchAppearance() - a failed/empty fetch just means no starter bubbles, never blocks connecting. */
    private suspend fun loadConversationStarter(onRawIncoming: (String) -> Unit) {
        try {
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
        } catch (e: Exception) {
            // Non-fatal - the real session's own first message still arrives once the socket opens.
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

    private fun openSocket() {
        val oId = ownerId ?: return
        val rId = roomId ?: return
        val wsScheme = if (baseUrl.startsWith("https")) "wss" else "ws"
        val host = baseUrl.substringAfter("://")
        val wsUrl = "$wsScheme://$host/ws/chat_updated/$oId/$rId"

        wsClient.connect(
            wsUrl = wsUrl,
            onOpen = {
                heartbeat.start()
                reconnectManager.onConnected()
                onConnected()
                pendingInitJumpTargetId?.let { targetId ->
                    pendingInitJumpTargetId = null
                    sendSystemJump(targetId)
                }
            },
            onMessage = { raw -> handleIncoming(raw, onRawIncoming) },
            onClosed = { _, _ -> handleClosed() },
            onFailure = { t ->
                handleClosed()
                onError(t)
            },
        )
    }

    private fun handleClosed() {
        heartbeat.stop()
        if (!manuallyDisconnected) {
            reconnectManager.scheduleReconnect(suppress = suppressReconnect)
        }
    }

    private fun handleIncoming(raw: String, onRawIncoming: (String) -> Unit) {
        val envelope = json.decodeFromString(RawSocketEnvelope.serializer(), raw)
        heartbeat.onMessageReceived(isPong = envelope.type == "pong")

        val event = envelope.toIncomingEvent()
        onRawIncoming(raw)
        when (event) {
            is IncomingSocketEvent.BotMessage -> {
                lastBotNode = event.node
                currentTargetId = event.node.targetId ?: currentTargetId
                handleWindowEventNode(event.node.content)
                // END-node side effects - not about rendering (isURLActionMessage/handleEndSession
                // in MessageHandlers.ts). urlMessage opens externally in addition to whatever
                // bubble/nothing the node's own text produces; end_session closes the socket.
                event.node.endUrlMessage?.let { onOpenUrl(it) }
                if (event.node.endSessionRequested) disconnect()
            }
            is IncomingSocketEvent.Ack -> ackTracker.acknowledge(event.chatMsgId)
            // The server also uses the echoed end_user message itself as a delivery ack
            // (handleEndUserMessage in MessageHandlers.ts), not only the explicit `ack` type.
            is IncomingSocketEvent.EchoedUserMessage -> ackTracker.acknowledge(event.chatMsgId)
            is IncomingSocketEvent.CloseConnection -> {
                if (event.suppressReconnect) suppressReconnect = true
            }
            // Mirrors handleStatusUpdate in MessageHandlers.ts: closes the socket outright unless
            // the session is configured to ask for post-chat feedback afterward - the widget
            // gates that survey behind a header close-icon tap; this SDK has no equivalent
            // in-chrome close affordance, so it surfaces the prompt immediately instead.
            is IncomingSocketEvent.LiveChatEnded -> {
                if (!shouldAskFeedback) disconnect() else onFeedbackRequested()
            }
            else -> Unit
        }
        onEvent(event)
    }

    /**
     * Mirrors WebCommunicationBridge.postMessage's exact chain (see ChatFragment.kt, now
     * replaced): a WINDOW_EVENT node's shouldSend fires the host's handleWindowEvent callback;
     * its return value is fed straight back through the same "currently receiving" gate as an
     * explicit sendEventToBot() call would use (that's how the original WebView bridge's
     * CHAT360_WINDOW_EVENT_RESPONSE -> window.receiveFromApp -> window.onAppEvent chain behaved).
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
     * carrying it as `variables`, matching onMoveForward(targetId, {variableValues: data}). */
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
     * Node context (currentId/nodeType/post_data) comes from the last bot node - sendUserMessage
     * in the widget always attaches it, and LLM/webhook nodes won't answer without it.
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
     * Answers a plain (non-international) PHONE node - identical to free text in the widget too
     * (no dedicated payload shape, no client-side validation).
     */
    fun sendPhone(value: String): String = sendFreeText(value)

    /**
     * Answers an international PHONE node with `splitVariable` set: country code and national
     * number land in two separate variables (mirrors Phone/index.tsx's split-variable branch),
     * and `doNotUpdateVariable`/`multiple_vars` are set exactly as the widget sends them.
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

    /** Answers a standalone DATE node - {type:'date', value, format} (mirrors Date/index.tsx's onSubmit). */
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
     * tapped card's image back as a MEDIA-type user message (mirrors Carousel/index.tsx:73-104);
     * web_url-type buttons never reach this (the UI opens them externally instead).
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
     * Answers a MULTI_CHOICE node. Payload mirrors createMultiChoicePayload + sendUserMessage
     * in the widget: message = {type:'multichoice-option', value: index+1, text}, post_data =
     * display text, currentId = the node's id, targetId = the clicked button's own targetId
     * (falling back to the node's), shouldValidate = false.
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
     * Uploads a file (mirrors uploadFile() in fetchServices.ts) then answers the current node
     * with it, matching FileUpload.tsx's submitFile(): message = {type:'file-upload', value,
     * fileName} where value is the uploaded URL(s) joined by "\n", skipPostData (no post_data
     * field), variable set from the node if present.
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
     * Uploads a recorded voice note then answers the current node with it - mirrors UserInput's
     * sendVoiceMessage(): upload via the same media endpoint as [uploadAndSendFile], but the
     * outgoing message is free text (the transcript, usually empty) carrying voiceUrl/transcript
     * in componentSpecificData rather than a `{type:'file-upload',...}` message body. Returns the
     * uploaded URL so the UI can show the sent bubble immediately without waiting on an echo.
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
     * [uploadAndSendFile] which sends immediately (mirrors Form.tsx uploading each file field
     * before building formValue at submit time, not as each file is picked... in practice this
     * uploads on pick and holds the URL, since re-uploading at submit time would just repeat the
     * same network call for no behavioral difference).
     */
    suspend fun uploadFormMedia(fileBytes: ByteArray, fileName: String, mimeType: String, onProgress: (Int) -> Unit): String {
        val room = roomId ?: throw IllegalStateException("Not connected")
        val urls = apiService.uploadMedia(room, botId, fileBytes, fileName, mimeType, onProgress)
        return urls.joinToString("\n")
    }

    /**
     * Answers a RATING node. Ratings/index.tsx's onSubmit sends the plain 1-based index string
     * through the normal sendUserMessage path - not a distinct wire shape, just free text with
     * the current node's context attached.
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
     * Answers a FORM node. Mirrors Form/index.tsx's handleSubmit(): message = {type:
     * 'form-response', formValue: [...]} with one entry per field in index order, plus a
     * variables map built from each field's own `variable` name. MEDIA fields' [fileNames] entry
     * gets folded into their formValue as "{fileName}:-{uploadedUrl}" (see [uploadFormMedia]).
     */
    fun sendFormResponse(
        values: Map<Int, String>,
        fields: List<BotContent.Form.Field>,
        fileNames: Map<Int, String> = emptyMap(),
    ): String {
        val node = lastBotNode
        val ordered = fields.sortedBy { it.index }
        // A MEDIA field's formValue entry is "{fileName}:-{uploadedUrl}"; its variable is still
        // just the plain URL (values[field.index]) - matches Form.tsx's file-field handling.
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
     * Submits the post-chat configurable feedback survey - mirrors ConfigurableFeedbackForm's
     * session-submit path exactly: message = {rating, feedback, type:'feedback'}, nodeType:
     * 'feedback', no targetId/currentId/variables (skipTargetId/skipCurrId/doNotUpdateVariable
     * in the source - this is an out-of-band message, not an answer to the current flow node).
     * [feedbackText] is the already-concatenated form-field text (source's getFeedbackText():
     * every dynamic field's formatted value joined by newlines) - the session path never sends a
     * separate structured per-field map, only the 'flow'/in-message variant does.
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
     * Answers a WELCOME_SCREEN card tap. [clickedIndexOneBased] is sent as a STRING (source: `clickedIndex: \`${cardNumber}\``,
     * unlike TEXT_CAROUSEL's numeric clickedIndex) - matches `onCardClick` in
     * `WelcomeScreen/index.tsx` exactly. [ctaTargetId] overrides the node's own targetId only
     * for `ctaType=="component"` cards; `external_link` cards still submit with the default.
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
        wsClient.send(json.encodeToString(outgoing))
        ackTracker.trackSend(outgoing.chat_msg_id)
        return outgoing.chat_msg_id
    }

    fun disconnect() {
        manuallyDisconnected = true
        heartbeat.stop()
        reconnectManager.cancel()
        ackTracker.cancelAll()
        wsClient.close()
        repoScope.cancel()
        WindowEventBridge.unregisterSession()
    }
}
