package com.chat360.chatbot.network.rest.dto

import kotlinx.serialization.Serializable

/** GET .../hyundai/under-maintenance/ - `is_active` is true while maintenance mode is ON (bot
 * access blocked for every dealer), false once it's been turned back off. */
@Serializable
data class MaintenanceStatusResponse(
    val is_active: Boolean = false,
    val activated_on: String? = null,
    val deactivated_on: String? = null,
    val activated_by: String? = null,
)
