package com.chat360.chatbot.model.wire

import kotlinx.serialization.Serializable

/**
 * Advances the bot flow to a target node without user input - the widget sends this right
 * after connecting when session-init returns an INIT node, which is what makes the bot emit
 * its first message.
 */
@Serializable
data class SystemJumpMessage(
    val user: String = "bot",
    val data: JumpData,
    val bot_id: String,
    val first_name: String = "System",
    val last_name: String = "Message",
    val curr_id: String? = null,
    val room_id: String? = null,
) {
    @Serializable
    data class JumpData(
        val target_id: String,
        val currentUrl: String,
    )
}
