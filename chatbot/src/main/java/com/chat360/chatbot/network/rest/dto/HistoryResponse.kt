package com.chat360.chatbot.network.rest.dto

import com.chat360.chatbot.model.wire.RawSocketEnvelope
import kotlinx.serialization.Serializable

/**
 * `history` items are decoded as [RawSocketEnvelope] because history entries and live messages
 * share one wire shape and are parsed through the same path.
 */
@Serializable
data class HistoryResponse(
    val history: List<RawSocketEnvelope> = emptyList(),
    val current_length: Int? = null,
    val next_cursor: Int? = null,
    val previous_cursor: Int? = null,
)
