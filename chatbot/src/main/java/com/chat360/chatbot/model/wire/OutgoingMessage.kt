package com.chat360.chatbot.model.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Wire shape for a client -> server chat message. `message` is a JsonElement because the
 * backend accepts either a plain string (free text) or a typed object (quick-reply selection,
 * file upload, location, etc.) depending on `replyType`/`msgType` - only free-text is used in
 * POC 1, later POCs add more `message` shapes without changing this envelope.
 */
@Serializable
data class OutgoingMessage(
    val type: String = "message",
    val isLive: Boolean = false,
    val replyType: String = "free_text",
    val user: String = "end_user",
    val userType: String = "end_user",
    val first_name: String = "Anonymous",
    val last_name: String = "User",
    val message: JsonElement,
    val bot_id: String,
    val targetId: String?,
    val chat_msg_id: String = UUID.randomUUID().toString(),
    val room_id: String? = null,
    val currentId: String? = null,
    val nodeType: String? = null,
    val post_data: JsonElement? = null,
    val variables: Map<String, String>? = null,
    val shouldValidate: Boolean? = null,
    val doNotUpdateVariable: Boolean? = null,
    val multiple_vars: Boolean? = null,
    /** Ports createUserMessage()'s `if (componentSpecificData) msg.componentSpecificData = ...` -
     * an opaque per-message-type payload (e.g. VOICE_MESSAGE's {voiceUrl, transcript, msgType}). */
    val componentSpecificData: JsonElement? = null,
) {
    companion object {
        fun freeText(botId: String, targetId: String?, roomId: String?, text: String) =
            OutgoingMessage(
                message = JsonPrimitive(text),
                bot_id = botId,
                targetId = targetId,
                room_id = roomId,
            )
    }
}
