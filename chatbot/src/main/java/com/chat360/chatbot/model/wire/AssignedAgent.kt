package com.chat360.chatbot.model.wire

/** A human agent assigned to the session (ports the `highlight` node's `assigned_user`). */
data class AssignedAgent(
    val name: String?,
    val designation: String?,
    val avatarUrl: String?,
)
