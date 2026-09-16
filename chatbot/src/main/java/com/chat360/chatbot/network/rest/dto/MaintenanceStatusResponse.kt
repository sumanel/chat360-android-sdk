package com.chat360.chatbot.network.rest.dto

import kotlinx.serialization.Serializable

/** GET .../third-party-tasks/maintainance - `is_active` is true while maintenance mode is ON
 * (bot access blocked for every dealer), false once it's been turned back off. [message] is a
 * server-authored status string present either way (e.g. "System is running normally" when off) -
 * the one shown to the user when [is_active] is true. */
@Serializable
data class MaintenanceStatusResponse(
    val is_active: Boolean = false,
    val activated_on: String? = null,
    val deactivated_on: String? = null,
    val activated_by: String? = null,
    val message: String? = null,
)
