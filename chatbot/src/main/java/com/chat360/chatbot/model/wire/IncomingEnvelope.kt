package com.chat360.chatbot.model.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loose decode target for any server -> client socket frame. Fields are the ones actually read
 * by the dispatch chain below (close/ack/echo/typing/bot) - everything else is discarded rather
 * than guessed at.
 */
@Serializable
data class RawSocketEnvelope(
    val user: String? = null,
    val type: String? = null,
    val data: JsonObject? = null,
    val message: JsonElement? = null,
    val chat_msg_id: String? = null,
    val status: String? = null,
    val targetId: String? = null,
    val error: String? = null,
    // chatgpt_message streaming fields live at this level, sibling to `type`/`user`, not
    // nested inside `data`.
    val stream_id: String? = null,
    val end_stream: Boolean? = null,
    /** The live-chat "end" signal - a boolean flag on any incoming frame. */
    val update_status: Boolean? = null,
    /** Sibling to `type`/`data` on a `chat_inactivity_message` frame, not nested inside `data`. */
    val auto_archival: Boolean? = null,
    /** Server-assigned send time, "dd/MM/yyyy HH:mm:ss" UTC - present on session-init,
     * short-data, and (confirmed directly against a live `/chatbox/messages/{roomId}` response)
     * history and live message frames alike. [timestamp_int] is preferred when present (exact,
     * no string parsing); this is the fallback. Used so a replayed conversation shows each
     * message's real send time instead of "now" (the moment it happened to be replayed) - see
     * [toIncomingEvent]. */
    val time: String? = null,
    /** Unix epoch seconds (fractional), as a JSON string - e.g. "1786565354.987089". Sent
     * alongside [time] on history/live frames; preferred over it since it needs no date-format
     * parsing. */
    val timestamp_int: String? = null,
)

/** Typed result of dispatching a [RawSocketEnvelope] through the dispatch chain. */
sealed interface IncomingSocketEvent {
    data object Pong : IncomingSocketEvent
    data class CloseConnection(val suppressReconnect: Boolean) : IncomingSocketEvent
    data class Ack(val chatMsgId: String?) : IncomingSocketEvent
    /** [text] is only meaningful when replaying history (see toIncomingEvent()) - live echoes
     * only ever drive ack-tracking, since the user's own bubble was already appended
     * optimistically the moment they hit send. */
    data class EchoedUserMessage(val chatMsgId: String?, val text: String?, val timestampMs: Long? = null) : IncomingSocketEvent
    data class TypingStatus(val isTyping: Boolean) : IncomingSocketEvent
    data class BotMessage(val node: BotNode) : IncomingSocketEvent
    /** A `highlight` frame's `assigned_user` - takes precedence over any other msgType the same frame might carry. */
    data class AgentAssigned(val agent: AssignedAgent) : IncomingSocketEvent
    /** `update_status` - ends the live-chat segment (see ChatRepository's shouldAskFeedback-gated close). */
    data object LiveChatEnded : IncomingSocketEvent
    /** A `chat_inactivity_message` frame - [message] only set for its `type:'message'` sub-case; [autoArchive] mirrors `auto_archival` (session is being closed for inactivity). */
    data class InactivityNotice(val message: String?, val autoArchive: Boolean) : IncomingSocketEvent
    data class Unhandled(val raw: RawSocketEnvelope) : IncomingSocketEvent
}

/**
 * Handler chain order matters here, since e.g. a close_connection or ack frame must never fall
 * through and get misread as bot content: close_connection -> ack -> echoed end_user ->
 * typing_status -> update_status -> highlight (assigned_user, which takes precedence over any
 * other msgType the same frame carries) -> bot/agent content.
 */
fun RawSocketEnvelope.toIncomingEvent(): IncomingSocketEvent {
    val timestampMs = timestamp_int?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: time.parseServerTimestamp()
    if (type == "pong") return IncomingSocketEvent.Pong

    if (type == "close_connection") {
        val messageText = (message as? JsonPrimitive)?.contentOrNull.orEmpty()
        val suppress = error == "CONNECTION_CLOSE" ||
            messageText.lowercase().contains("other window or tab")
        return IncomingSocketEvent.CloseConnection(suppress)
    }

    if (type == "ack" && status == "sent") {
        return IncomingSocketEvent.Ack(chat_msg_id)
    }

    if (user == "end_user") {
        return IncomingSocketEvent.EchoedUserMessage(chat_msg_id, (message as? JsonPrimitive)?.contentOrNull, timestampMs)
    }

    if (type == "typing_status") {
        return IncomingSocketEvent.TypingStatus(status == "typing")
    }

    if (update_status == true) {
        return IncomingSocketEvent.LiveChatEnded
    }

    if (type == "chat_inactivity_message") {
        val isMessage = (data?.get("type") as? JsonPrimitive)?.contentOrNull == "message"
        val text = if (isMessage) (data?.get("message") as? JsonPrimitive)?.contentOrNull else null
        return IncomingSocketEvent.InactivityNotice(text, auto_archival == true)
    }

    // No dedicated content type renders a validation_error specially - the frame's own
    // top-level `message` is displayed as a plain bot bubble, same as any other text-only node.
    if (type == "validation_error") {
        val text = (message as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return IncomingSocketEvent.BotMessage(
            BotNode(
                nodeId = null,
                nodeType = "validation_error",
                targetId = targetId,
                variable = null,
                text = text,
                content = if (text != null) BotContent.PlainText else BotContent.Unsupported("validation_error"),
                timestampMs = timestampMs,
            ),
        )
    }

    val assignedUserObj = (data?.get("assigned_user") as? JsonObject)?.takeIf { it.isNotEmpty() }
    if (assignedUserObj != null) {
        return IncomingSocketEvent.AgentAssigned(assignedUserObj.toAssignedAgent())
    }

    if ((user == "bot" || user == "admin" || user == "operator") && data != null) {
        val node = data.toBotNode(fallbackTargetId = targetId).copy(timestampMs = timestampMs)
        val authored = if (user == "bot") node else node.copy(author = BotNode.MessageAuthor.AGENT)
        // data.nodeType wins over the envelope's own `type` when present, so only treat this as
        // a streaming chunk when the node has no nodeType of its own.
        // chatgpt_message streaming is bot-origin only, so this never applies to admin/operator.
        return IncomingSocketEvent.BotMessage(
            if (user == "bot" && authored.nodeType.isNullOrBlank() && type == "chatgpt_message") {
                authored.copy(streamId = stream_id, streamEnded = end_stream ?: true)
            } else {
                authored
            },
        )
    }

    // A chatgpt_message chunk with no nested `data` at all still carries its text via the
    // envelope's own top-level `message` field.
    if (user == "bot" && type == "chatgpt_message") {
        // Text stays as raw (unrendered) HTML here - a chunk boundary can land inside a tag, so a
        // tag split across two chunks never forms a complete "<...>" in either one on its own.
        // ChatViewModel concatenates every chunk before RichTextParser ever sees the string, so a
        // split tag is always whole again by the time it's parsed.
        val chunkText = (message as? JsonPrimitive)?.contentOrNull?.normalizeNewlines()?.takeIf { it.isNotBlank() }
        return IncomingSocketEvent.BotMessage(
            BotNode(
                nodeId = null,
                nodeType = "chatgpt_message",
                targetId = targetId,
                variable = null,
                text = chunkText,
                content = if (chunkText != null) BotContent.PlainText else BotContent.Unsupported("chatgpt_message"),
                streamId = stream_id,
                streamEnded = end_stream ?: true,
                timestampMs = timestampMs,
            ),
        )
    }

    return IncomingSocketEvent.Unhandled(this)
}

/** The backend sends `time` as "dd/MM/yyyy HH:mm:ss" in UTC (confirmed against the same field
 * on session-init/short-data responses); converted to epoch ms here so downstream formatting
 * with the device's default timezone renders it correctly as local time. Absent or unparseable
 * just yields null, which callers fall back to "now" for. */
private fun String?.parseServerTimestamp(): Long? {
    this ?: return null
    return runCatching {
        val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.parse(this)?.time
    }.getOrNull()
}

/** assigned_user -> {avatar, user_designation, operator_name}. */
private fun JsonObject.toAssignedAgent(): AssignedAgent = AssignedAgent(
    name = string("operator_name")?.takeIf { it.isNotBlank() },
    designation = string("user_designation")?.takeIf { it.isNotBlank() },
    avatarUrl = string("avatar")?.takeIf { it.isNotBlank() },
)

fun JsonObject.toBotNode(fallbackTargetId: String? = null): BotNode {
    val nodeType = string("nodeType")
    val nodeTargetId = string("targetId") ?: fallbackTargetId
    val variables = variablesMap()
    // Buttons whose text is an @variable reference are kept only when every referenced
    // variable has a non-blank value - an LLM/webhook node's placeholder buttons stay hidden
    // until the populated frame arrives.
    val options = (this["buttons"] as? JsonArray)?.mapIndexedNotNull { index, element ->
        val button = element as? JsonObject ?: return@mapIndexedNotNull null
        val rawText = button.string("text") ?: return@mapIndexedNotNull null
        if (!isTextPopulated(rawText, variables)) return@mapIndexedNotNull null
        BotContent.MultiChoice.Option(
            index = index,
            text = resolveVariables(rawText, variables).stripHtml(),
            targetId = button.string("targetId"),
        )
    }.orEmpty()

    val rawText = botDisplayText()
    // LINK bifurcates: with no separate question text, the URL itself becomes the message (a
    // plain linkified bubble); only when question text already exists does the URL become its
    // own separate clickable card alongside it.
    val linkUrl = if (nodeType == "LINK") string("urlMessage")?.let { resolveVariables(it, variables) } else null
    val text = (if (nodeType == "LINK" && rawText.isNullOrBlank()) linkUrl else rawText)
        ?.let { resolveVariables(it, variables) }
        ?.normalizeNewlines()
        ?.takeIf { it.isNotBlank() }

    val content = when {
        // YES_NO reuses MULTI_CHOICE's own extractor/renderer exactly - same shape, same payload.
        (nodeType == "MULTI_CHOICE" || nodeType == "YES_NO") && options.isNotEmpty() ->
            BotContent.MultiChoice(variant = string("choiceType"), options = options)
        nodeType == "MEDIA" -> mediaContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "CAROUSEL" -> carouselContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "FILE_UPLOAD" -> fileUploadContent(variables)
        nodeType == "DOWNLOAD_MEDIA" -> downloadMediaContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "RATING" -> ratingContent()
        nodeType == "FORM" -> formContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "WINDOW_EVENT" -> windowEventContent(variables)
        nodeType == "EMAIL" -> BotContent.EmailPrompt
        nodeType == "PHONE" -> phoneContent()
        // CUSTOMINPUT is two behaviors sharing one nodeType: an explicit AUTOSUGGESTION picker,
        // or (far more commonly) a plain free-text prompt - which our always-visible input bar
        // already answers via the generic PlainText fallback below, no dedicated content needed.
        nodeType == "CUSTOMINPUT" -> autoSuggestionContent(variables)
            ?: (if (text != null) BotContent.PlainText else BotContent.Unsupported(nodeType))
        nodeType == "DATE" -> BotContent.DatePrompt(standaloneDateRules())
        nodeType == "TIME" -> timeContent()
        nodeType == "MULTIPLE_CHECK_BOX" -> multiOptionContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "IMAGE_BUTTON" -> imageButtonContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "TEXT_CAROUSEL" -> textCarouselContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "LINK" -> if (!rawText.isNullOrBlank() && linkUrl != null) BotContent.LinkCard(linkUrl) else BotContent.PlainText
        nodeType == "AGENT_TRANSFER" || nodeType == "TEAM_TRANSFER" -> BotContent.AgentTransferNotice
        // chat_feedback_data forces msgType FEEDBACK for the post-chat "rate this conversation"
        // prompt - distinct trigger from an in-flow RATING node, but the same UI/send path.
        nodeType == "FEEDBACK" -> ratingContent()
        nodeType == "WELCOME_SCREEN" -> welcomeScreenContent(variables) ?: BotContent.Unsupported(nodeType)
        nodeType == "IFRAME" -> iframeContent(nodeTargetId) ?: BotContent.Unsupported(nodeType)
        text != null -> BotContent.PlainText
        else -> BotContent.Unsupported(nodeType)
    }

    return BotNode(
        nodeId = string("id"),
        nodeType = nodeType,
        targetId = nodeTargetId,
        variable = string("variable")?.takeIf { it.isNotBlank() },
        text = text,
        content = content,
        endUrlMessage = if (nodeType == "END") string("urlMessage")?.takeIf { it.isNotBlank() } else null,
        endSessionRequested = nodeType == "END" && boolean("end_session"),
        // stream_id/end_stream are top-level envelope fields, not nested here - see
        // toIncomingEvent()'s chatgpt_message handling, which overlays them after this returns.
    )
}

private fun JsonObject.variablesMap(): Map<String, String> =
    (this["variables"] as? JsonObject)?.mapNotNull { (key, value) ->
        if (key.isBlank() || key == "@") return@mapNotNull null
        key to ((value as? JsonPrimitive)?.contentOrNull ?: "")
    }?.toMap().orEmpty()

/** Replace each known variable name (word-bounded) with its value. */
private fun resolveVariables(text: String, variables: Map<String, String>): String {
    if (!text.contains('@') || variables.isEmpty()) return text
    val pattern = variables.keys.joinToString("|") { Regex.escape(it) + "\\b" }
    return Regex(pattern, RegexOption.IGNORE_CASE).replace(text) { match ->
        variables.entries.firstOrNull { it.key.equals(match.value, ignoreCase = true) }?.value ?: ""
    }
}

private val variableReferenceRegex = Regex("@[A-Za-z0-9_]+")

/** Hide choices whose @variables are empty/unresolved. */
private fun isTextPopulated(text: String, variables: Map<String, String>): Boolean {
    if (text.stripHtml().isBlank()) return false
    if (text.contains('@')) {
        val references = variableReferenceRegex.findAll(text).map { it.value }.toList()
        if (references.isEmpty()) return true
        return references.all { ref ->
            variables.entries.firstOrNull { it.key.equals(ref, ignoreCase = true) }
                ?.value?.isNotBlank() == true
        }
    }
    return resolveVariables(text, variables).stripHtml().isNotBlank()
}

/** A single media item, media_url + optional title/download flag. */
private fun JsonObject.mediaContent(variables: Map<String, String>): BotContent.Media? {
    val rawUrl = string("media_url") ?: return null
    val url = resolveVariables(rawUrl, variables)
    val title = string("media_title")?.let { resolveVariables(it, variables) }
    val dynamicButtons = (this["dynamic_buttons"] as? JsonArray)?.mapNotNull { btn ->
        val b = btn as? JsonObject ?: return@mapNotNull null
        BotContent.TextCarousel.DynamicButton(
            title = b.string("title")?.let { resolveVariables(it, variables) }.orEmpty(),
            targetId = b.string("targetId")?.takeIf { it.isNotBlank() },
            componentUuid = b.string("componentUuid")?.takeIf { it.isNotBlank() },
        )
    }.orEmpty()
    return BotContent.Media(
        url = url,
        kind = mediaKindOf(url),
        title = title,
        downloadDisabled = boolean("disable_download"),
        dynamicButtons = dynamicButtons,
    )
}

/** Parallel arrays (carousel_url/links/captions/headings) -> cards. */
private fun JsonObject.carouselContent(variables: Map<String, String>): BotContent.Carousel? {
    val urlEntries = this["carousel_url"] as? JsonArray ?: return null
    val links = (this["link_carousel"] as? JsonArray)?.stringsOrNull()
    val captions = (this["caption_carousel"] as? JsonArray)?.stringsOrNull()
    val headings = (this["heading_carousel"] as? JsonArray)?.stringsOrNull()

    val cards = urlEntries.mapIndexedNotNull { index, entry ->
        // Each carousel_url entry is itself an array; only element [0] is used.
        val rawUrl = (entry as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: return@mapIndexedNotNull null
        BotContent.Carousel.Card(
            imageUrl = resolveVariables(rawUrl, variables),
            heading = headings?.getOrNull(index)?.let { resolveVariables(it, variables).stripHtml() },
            caption = captions?.getOrNull(index)?.let { resolveVariables(it, variables).stripHtml() },
            link = links?.getOrNull(index)?.let { resolveVariables(it, variables) }?.takeIf { it.isNotBlank() },
        )
    }
    return if (cards.isNotEmpty()) BotContent.Carousel(cards) else null
}

private fun JsonArray.stringsOrNull(): List<String?> = map { (it as? JsonPrimitive)?.contentOrNull }

/** Which extensions the node accepts, keyed off its fileType toggles. */
private fun JsonObject.fileUploadContent(variables: Map<String, String>): BotContent.FileUploadPrompt {
    val extensionsByKey = mapOf(
        "image" to listOf("jpg", "jpeg", "png", "webp", "gif", "tif", "tiff", "bmp", "jfif"),
        "video" to listOf("mp4", "webm", "mov", "ogv", "avi", "hevc", "m4v"),
        "document" to listOf("pdf", "doc", "docx", "odt", "txt", "xlsx", "csv", "zip"),
        "audio" to listOf("mp3", "wav", "ogg", "ogx"),
        "pdf" to listOf("pdf"),
    )
    val extensions = (this["fileType"] as? JsonArray)?.flatMap { entry ->
        val obj = entry as? JsonObject ?: return@flatMap emptyList()
        val enabled = obj.boolean("value")
        val key = obj.string("key")?.lowercase()
        if (enabled && key != null) extensionsByKey[key].orEmpty() else emptyList()
    }?.distinct().orEmpty()
    // No type toggles enabled at all -> default to accepting everything.
    val allowed = extensions.ifEmpty { extensionsByKey.values.flatten().distinct() }
    val prompt = string("questionText")?.let { resolveVariables(it, variables) }?.stripHtml()
    return BotContent.FileUploadPrompt(promptText = prompt, allowedExtensions = allowed, allowCamera = boolean("enableCameraInput"))
}

/** A downloadable link, named either explicitly or from the URL. */
private fun JsonObject.downloadMediaContent(variables: Map<String, String>): BotContent.DownloadMedia? {
    val rawUrl = string("media_url") ?: return null
    val url = resolveVariables(rawUrl, variables)
    val name = string("mediaName") ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
    return BotContent.DownloadMedia(fileUrl = url, fileName = name)
}

/** star_custom -> star (scale = rating_max, default 5), else emoji (fixed scale of 5). */
private fun JsonObject.ratingContent(): BotContent.Rating {
    val style = if (string("rating_type") == "star_custom") "star" else "emoji"
    val scale = if (style == "star") {
        string("rating_max")?.toIntOrNull() ?: 5
    } else {
        5
    }
    return BotContent.Rating(style = style, scale = scale)
}

/**
 * One Field per `elements[]` entry. Field types beyond TEXT/NUMBER/EMAIL/PHONE/SELECT/DATE/MEDIA
 * (e.g. RCS-specific ones) fall through to OTHER so the form still renders and can be extended
 * field-by-field later.
 */
private fun JsonObject.formContent(variables: Map<String, String>): BotContent.Form? {
    val elements = this["elements"] as? JsonArray ?: return null
    val fields = elements.mapIndexedNotNull { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexedNotNull null
        val fieldType = when (obj.string("type")?.uppercase()) {
            "TEXT" -> BotContent.Form.FieldType.TEXT
            "NUMBER" -> BotContent.Form.FieldType.NUMBER
            "EMAIL" -> BotContent.Form.FieldType.EMAIL
            "PHONE" -> BotContent.Form.FieldType.PHONE
            "SELECT" -> BotContent.Form.FieldType.SELECT
            "DATE" -> BotContent.Form.FieldType.DATE
            "MEDIA" -> BotContent.Form.FieldType.MEDIA
            else -> BotContent.Form.FieldType.OTHER
        }
        val options = (obj["options"] as? JsonArray)?.stringsOrNull()
            ?.filterNotNull()
            ?.map { resolveVariables(it, variables) }
            .orEmpty()
        val validation = obj.fieldValidation(dateFormat = obj.string("dateFormat"))
        BotContent.Form.Field(
            index = index,
            type = fieldType,
            label = obj.string("label")?.let { resolveVariables(it, variables) }?.stripHtml()?.takeIf { it.isNotBlank() },
            placeholder = obj.string("placeholder")?.let { resolveVariables(it, variables) },
            // A backend can mark a field required either via validation.isRequired or a flat
            // isRequired - honor either spelling so a backend using only the flat form still
            // enforces required-ness.
            isRequired = validation?.isRequired == true || obj.boolean("isRequired"),
            options = options,
            variable = obj.string("variable")?.takeIf { it.isNotBlank() },
            validation = validation,
        )
    }
    if (fields.isEmpty()) return null
    val submitText = string("submitButtonText")?.let { resolveVariables(it, variables) }?.takeIf { it.isNotBlank() } ?: "Submit"
    return BotContent.Form(fields = fields, submitButtonText = submitText)
}

/** Decodes a form element's `validation` object. */
private fun JsonObject.fieldValidation(dateFormat: String?): BotContent.Form.FieldValidation? {
    val v = this["validation"] as? JsonObject ?: return null
    val dateRules = if (v.containsAnyDateKey()) {
        DateRules(
            isScheduledDate = v.boolean("isScheduledDate"),
            disabledDays = (v["disabledDays"] as? JsonArray)?.map { (it as? JsonPrimitive)?.booleanOrNull == true },
            disabledDates = (v["disableDates"] as? JsonArray)?.stringsOrNull()?.filterNotNull(),
            // FORM's own field names for these two flags (isFutureDisabled/isPrevDisabled)
            // deliberately differ from the standalone DATE node's (isFutureDate/isPrevDate).
            disableFuture = v.boolean("isFutureDisabled"),
            disablePrevious = v.boolean("isPrevDisabled"),
            dateFormat = dateFormat?.takeIf { it.isNotBlank() } ?: "DD MMM YYYY",
        )
    } else {
        null
    }
    return BotContent.Form.FieldValidation(
        isRequired = v.boolean("isRequired"),
        errorMessage = v.string("errorMessage")?.takeIf { it.isNotBlank() },
        userInputType = v.string("userInputType"),
        maxCharacters = v.int("maxCharacters"),
        minCharacters = v.int("minCharacters"),
        email = v.boolean("email"),
        maxCount = v.double("maxCount"),
        minCount = v.double("minCount"),
        phone = v.boolean("phone"),
        allowInternationalNumber = v.boolean("allowInternationalNumber"),
        numberFormat = v.string("numberFormat"),
        dateRules = dateRules,
    )
}

private fun JsonObject.containsAnyDateKey(): Boolean =
    containsKey("isScheduledDate") || containsKey("disableDates") || containsKey("isFutureDisabled") || containsKey("isPrevDisabled")

/** Core rules for a standalone DATE node (see [DateRules]'s doc for the two gaps). */
private fun JsonObject.standaloneDateRules(): DateRules = DateRules(
    isScheduledDate = boolean("isScheduledDate"),
    disabledDays = (this["disabledDays"] as? JsonArray)?.map { (it as? JsonPrimitive)?.booleanOrNull == true },
    disabledDates = (this["disabledDates"] as? JsonArray)?.stringsOrNull()?.filterNotNull(),
    disableFuture = boolean("isFutureDate"),
    disableCurrent = boolean("isCurrentDate"),
    disablePrevious = boolean("isPrevDate"),
    manageWithVariable = boolean("manageWithVariable"),
    variableMode = string("variableMode"),
    variableDates = string("datesVariable")?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() },
    dateFormat = string("dateFormat")?.takeIf { it.isNotBlank() } ?: "DD MMM YYYY",
)

/** disabledSlots is "hh:mm-hh:mm" range strings, expanded to hour->minutes. */
private fun JsonObject.timeContent(): BotContent.TimePrompt {
    val slots = (this["disabledSlots"] as? JsonArray)?.stringsOrNull()?.filterNotNull().orEmpty()
    val disabled = mutableMapOf<Int, MutableList<Int>>()
    slots.forEach { range ->
        val (start, end) = range.split('-').map { it.trim() }.let { it.getOrNull(0) to it.getOrNull(1) }
        val startParts = start?.split(':')?.mapNotNull { it.toIntOrNull() }
        val endParts = end?.split(':')?.mapNotNull { it.toIntOrNull() }
        if (startParts?.size == 2 && endParts?.size == 2) {
            val (startHour, startMin) = startParts
            val (endHour, endMin) = endParts
            var hour = startHour
            var minute = startMin
            while (hour < endHour || (hour == endHour && minute <= endMin)) {
                disabled.getOrPut(hour) { mutableListOf() }.add(minute)
                minute++
                if (minute > 59) {
                    minute = 0
                    hour++
                }
            }
        }
    }
    return BotContent.TimePrompt(disabledSlots = disabled, disablePrevious = boolean("disablePrev"))
}

/**
 * should_send_data defaults true; send_data is an object whose string values go through the
 * same @variable substitution as everything else.
 */
private fun JsonObject.windowEventContent(variables: Map<String, String>): BotContent.WindowEvent {
    val shouldSend = (this["should_send_data"] as? JsonPrimitive)?.booleanOrNull ?: true
    val shouldReceive = (this["should_receive_data"] as? JsonPrimitive)?.booleanOrNull ?: false
    val sendData = (this["send_data"] as? JsonObject)?.mapNotNull { (key, value) ->
        val raw = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        key to resolveVariables(raw, variables)
    }?.toMap().orEmpty()
    return BotContent.WindowEvent(shouldSend = shouldSend, sendData = sendData, shouldReceive = shouldReceive)
}

/** International mode is the only variant with dedicated client-side validation. */
private fun JsonObject.phoneContent(): BotContent.PhonePrompt = BotContent.PhonePrompt(
    allowInternational = boolean("allowInternationalNumber"),
    splitVariable = boolean("split_variable"),
    countryCodeVar = string("country_code_var")?.takeIf { it.isNotBlank() },
)

/** choices is a plain string array, filtered like MULTI_CHOICE's buttons. */
private fun JsonObject.multiOptionContent(variables: Map<String, String>): BotContent.MultiOption? {
    val choices = this["choices"] as? JsonArray ?: return null
    val options = choices.mapIndexedNotNull { index, element ->
        val rawText = (element as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
        if (!isTextPopulated(rawText, variables)) return@mapIndexedNotNull null
        BotContent.MultiOption.Option(index = index, text = resolveVariables(rawText, variables).stripHtml())
    }
    if (options.isEmpty()) return null
    val description = (string("description") ?: string("questionText"))
        ?.let { resolveVariables(it, variables) }
        ?.stripHtml()
        ?.takeIf { it.isNotBlank() }
    return BotContent.MultiOption(description = description, options = options)
}

/**
 * Per-slide buttons come from `img_buttons` (falling back to the legacy `link_carousel_text`,
 * one reply button per slide); each button's targetId is resolved from the flat `buttons` array
 * by cumulative position across all slides.
 */
private fun JsonObject.imageButtonContent(variables: Map<String, String>): BotContent.ImageButtons? {
    val urlEntries = this["carousel_url"] as? JsonArray ?: return null
    val descriptions = (this["link_carousel"] as? JsonArray)?.stringsOrNull()
    val flatButtons = (this["buttons"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    val perSlideButtons: List<List<RawImageButton>> = when {
        this["img_buttons"] is JsonArray -> (this["img_buttons"] as JsonArray).map { slide ->
            (slide as? JsonArray)?.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                RawImageButton(text = obj.string("text").orEmpty(), type = obj.string("type") ?: "reply", value = obj.string("value"), url = obj.string("url"))
            }.orEmpty()
        }
        this["link_carousel_text"] is JsonArray -> (this["link_carousel_text"] as JsonArray).map { entry ->
            val text = (entry as? JsonPrimitive)?.contentOrNull
            if (text != null) listOf(RawImageButton(text = text, type = "reply", value = null, url = null)) else emptyList()
        }
        else -> emptyList()
    }

    var flatIndex = 0
    val cards = urlEntries.mapIndexedNotNull { index, entry ->
        val rawUrl = (entry as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@mapIndexedNotNull null
        val buttons = perSlideButtons.getOrNull(index).orEmpty().map { raw ->
            val targetId = flatButtons.getOrNull(flatIndex)?.string("targetId")
            flatIndex++
            BotContent.ImageButtons.Button(
                text = resolveVariables(raw.text, variables),
                type = raw.type,
                value = (raw.value ?: raw.text).let { resolveVariables(it, variables) },
                url = raw.url?.let { resolveVariables(it, variables) },
                targetId = targetId,
            )
        }
        BotContent.ImageButtons.Card(
            imageUrl = resolveVariables(rawUrl, variables),
            description = descriptions?.getOrNull(index)?.let { resolveVariables(it, variables).stripHtml() },
            buttons = buttons,
        )
    }
    if (cards.isEmpty()) return null
    return BotContent.ImageButtons(cards = cards, submitType = string("submit_type") ?: "BUTTON")
}

private data class RawImageButton(val text: String, val type: String, val value: String?, val url: String?)

/**
 * Both `type1`/`type2` wire generations map onto one card shape here (see
 * [BotContent.TextCarousel]'s doc for the simplification note).
 */
private fun JsonObject.textCarouselContent(variables: Map<String, String>): BotContent.TextCarousel? {
    val rawCards = this["text_cards"] as? JsonArray ?: return null
    val isType2 = string("text_carousel_type") == "type2"
    val cards = rawCards.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val content = obj.string("content")?.let { resolveVariables(it, variables) }?.stripHtml()
        if (!isType2 && content.isNullOrBlank()) return@mapNotNull null
        val ctaButtons = (obj["ctaButtons"] as? JsonArray)?.mapNotNull { btn ->
            val b = btn as? JsonObject ?: return@mapNotNull null
            BotContent.TextCarousel.CtaButton(
                name = b.string("name")?.let { resolveVariables(it, variables) }.orEmpty(),
                type = if (b.string("type") == "component_uuid") "component_uuid" else "link",
                link = b.string("link").orEmpty(),
            )
        }.orEmpty()
        val buttons = (obj["buttons"] as? JsonArray)?.mapNotNull { btn ->
            val b = btn as? JsonObject ?: return@mapNotNull null
            BotContent.TextCarousel.CardButton(
                label = b.string("label")?.let { resolveVariables(it, variables) }.orEmpty(),
                link = b.string("link"),
            )
        }.orEmpty()
        BotContent.TextCarousel.Card(
            name = obj.string("name")?.takeIf { it.isNotBlank() },
            content = content,
            bgColor = obj.string("bgColor"),
            bgImage = obj.string("bgImage")?.takeIf { it.isNotBlank() },
            footerText = obj.string("footerText")?.let { resolveVariables(it, variables) },
            iconText = obj.string("iconText")?.let { resolveVariables(it, variables) },
            iconUrl = obj.string("iconUrl")?.takeIf { it.isNotBlank() },
            redirectType = obj.string("redirectType")?.takeIf { it.isNotBlank() },
            redirectLink = obj.string("redirectLink")?.takeIf { it.isNotBlank() },
            ctaButtons = ctaButtons,
            buttons = buttons,
        )
    }
    if (cards.isEmpty()) return null
    val dynamicButtons = (this["dynamic_buttons"] as? JsonArray)?.mapNotNull { btn ->
        val b = btn as? JsonObject ?: return@mapNotNull null
        BotContent.TextCarousel.DynamicButton(
            title = b.string("title")?.let { resolveVariables(it, variables) }.orEmpty(),
            targetId = b.string("targetId")?.takeIf { it.isNotBlank() },
            componentUuid = b.string("componentUuid")?.takeIf { it.isNotBlank() },
        )
    }.orEmpty()
    return BotContent.TextCarousel(cards = cards, dynamicButtons = dynamicButtons)
}

/** A plain embedded WebView, no postMessage bridge. */
private fun JsonObject.iframeContent(nodeTargetId: String?): BotContent.IframeContent? {
    val src = string("src")?.takeIf { it.isNotBlank() } ?: return null
    return BotContent.IframeContent(
        url = src,
        heightDp = int("height"),
        moveForEvent = string("moveForEvent")?.takeIf { it.isNotBlank() },
        targetId = nodeTargetId,
    )
}

/** Decodes a WELCOME_SCREEN node's cards, title, and description. */
private fun JsonObject.welcomeScreenContent(variables: Map<String, String>): BotContent.WelcomeScreen? {
    val cards = (this["text_cards"] as? JsonArray)?.mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        BotContent.WelcomeScreen.Card(
            name = obj.string("name")?.let { resolveVariables(it, variables) },
            title = obj.string("title")?.let { resolveVariables(it, variables) },
            bgColor = obj.string("bgColor"),
            bgImageUrl = obj.string("bgImageUrl")?.takeIf { it.isNotBlank() },
            ctaEnabled = obj.boolean("ctaEnabled"),
            ctaType = obj.string("ctaType") ?: "external_link",
            ctaLink = obj.string("ctaLink")?.takeIf { it.isNotBlank() },
        )
    }.orEmpty()
    val title = string("title")?.let { resolveVariables(it, variables) }
    val description = string("description")?.let { resolveVariables(it, variables) }
    if (cards.isEmpty() && title.isNullOrBlank() && description.isNullOrBlank()) return null
    return BotContent.WelcomeScreen(
        iconUrl = string("mainIconUrl")?.takeIf { it.isNotBlank() },
        title = title,
        description = description,
        cards = cards,
    )
}

/** The AUTOSUGGESTION sub-case of CUSTOMINPUT: options are a "{&}"-delimited string. */
private fun JsonObject.autoSuggestionContent(variables: Map<String, String>): BotContent.AutoSuggestion? {
    val type = (this["dataType"] as? JsonObject)?.string("type")
    if (type != "AUTOSUGGESTION") return null
    val raw = string("autoSuggestionOptions") ?: return null
    val choices = raw.split("{&}").map { resolveVariables(it, variables).trim() }.filter { it.isNotBlank() }
    if (choices.isEmpty()) return null
    val description = string("autoSuggestionDescription")?.let { resolveVariables(it, variables) }?.stripHtml()
    return BotContent.AutoSuggestion(choices = choices, description = description)
}

private fun mediaKindOf(url: String): BotContent.MediaKind = when {
    Regex("^https?://.*\\.(jpg|jpeg|png|webp|jfif|gif)", RegexOption.IGNORE_CASE).containsMatchIn(url) ->
        BotContent.MediaKind.IMAGE
    Regex("^https?://.*\\.(mp4|webm|ogv)", RegexOption.IGNORE_CASE).containsMatchIn(url) ->
        BotContent.MediaKind.VIDEO
    Regex("^https?://.*\\.(mp3|wav|ogx|ogg|m4a|aac)", RegexOption.IGNORE_CASE).containsMatchIn(url) ->
        BotContent.MediaKind.AUDIO
    else -> BotContent.MediaKind.OTHER
}

private fun JsonObject.boolean(key: String): Boolean {
    val primitive = this[key] as? JsonPrimitive ?: return false
    return primitive.booleanOrNull ?: (primitive.contentOrNull == "1")
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

/**
 * questionText arrives as rich-text HTML (e.g. "<p>Hey there</p>"). Kept raw/unstripped -
 * RichTextParser renders the tags for real (bold/italic/links/lists) instead of showing them
 * literally or discarding them; see [com.chat360.chatbot.ui.components.messages.content.PlainTextContent].
 */
private fun JsonObject.botDisplayText(): String? {
    (this["questionText"] as? JsonPrimitive)?.contentOrNull?.let { return it }
    (this["messages"] as? JsonArray)?.let { messages ->
        val joined = messages.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (joined.isNotEmpty()) return joined.joinToString("\n")
    }
    return null
}

/**
 * Strips tags entirely rather than rendering them - reserved for short auxiliary labels (button
 * text, card headings/captions, populated-variable checks) that render through a plain `Text()`
 * with no rich-text support, not through [botDisplayText]'s RichTextParser path.
 */
private fun String.stripHtml(): String =
    replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("</p>\\s*<p>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()

/** LLM nodes send literal "\n" escape sequences in text. */
private fun String.normalizeNewlines(): String =
    replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n")
