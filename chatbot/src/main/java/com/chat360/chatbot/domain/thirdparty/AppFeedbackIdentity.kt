package com.chat360.chatbot.domain.thirdparty

/**
 * Who an application-wide feedback is from: the dealer and employee the host passed in `meta` (`dealer_id` / `emp_id`),
 * with the end-user id as the employee of last resort. Blank counts as unset.
 */
data class AppFeedbackIdentity(val dealerId: String?, val empId: String?) {
    companion object {
        fun resolve(meta: Map<String, String>?, endUserId: String?): AppFeedbackIdentity {
            fun clean(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() }
            return AppFeedbackIdentity(
                dealerId = clean(meta?.get("dealer_id")),
                empId = clean(meta?.get("emp_id")) ?: clean(endUserId),
            )
        }
    }
}
