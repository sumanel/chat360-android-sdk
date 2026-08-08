package com.chat360.chatbot.network.rest.dto

import com.chat360.chatbot.model.wire.FeedbackConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `json_info` arrives as a JSON-*encoded string* inside the outer object (confirmed against a
 * real `/api/chatbox/{botId}/chatboxappeareance` response), not a nested object - decode it
 * separately once this outer shape is parsed.
 */
@Serializable
data class BotAppearanceResponse(
    val chatboxname: String? = null,
    val json_info: String? = null,
)

/** Only the subset of json_info's ~30 tokens currently mapped to Chat360 colors/branding. */
@Serializable
data class BotAppearanceDetails(
    val botBackgroundColor: String? = null,
    val primaryBackgroundColor: String? = null,
    val botMessageBoxColor: String? = null,
    val userMessageBoxColor: String? = null,
    val botTextColor: String? = null,
    val userTextColor: String? = null,
    val buttonColor: String? = null,
    val buttonTxtColor: String? = null,
    val statusColor: String? = null,
    val chatboxHeader: ChatboxHeader? = null,
    /** The bot-owner-authored post-chat survey definition - see [FeedbackConfig]. */
    val feedback_config: FeedbackConfig? = null,
) {
    @Serializable
    data class ChatboxHeader(
        val primarychatboxHeaderColor: String? = null,
        val secondaryChatboxHeaderColor: String? = null,
        val chatboxAvatar: String? = null,
    )
}

/** Decodes json_info, tolerating a malformed/missing blob (returns null) rather than failing the fetch. */
fun BotAppearanceResponse.details(json: Json): BotAppearanceDetails? {
    val raw = json_info ?: return null
    return try {
        json.decodeFromString(BotAppearanceDetails.serializer(), raw)
    } catch (e: Exception) {
        null
    }
}
