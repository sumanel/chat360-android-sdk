package com.chat360.chatbot.model.wire

/**
 * The disabled-date rule set shared by standalone DATE prompts and FORM DATE fields. Two rarer
 * rules are intentionally not implemented here (flagged as a known gap, not silently dropped):
 * "referredDate" fencing relative to another variable's date, and the
 * disabledDaysFromCd/onlyEnableDaysFromCd rolling-window-from-today variants.
 */
data class DateRules(
    val isScheduledDate: Boolean = false,
    /** index 0 = Sunday .. 6 = Saturday, only honored when [isScheduledDate] is true. */
    val disabledDays: List<Boolean>? = null,
    val disabledDates: List<String>? = null,
    val disableFuture: Boolean = false,
    val disableCurrent: Boolean = false,
    val disablePrevious: Boolean = false,
    val manageWithVariable: Boolean = false,
    val variableMode: String? = null,
    val variableDates: List<String>? = null,
    /** Dayjs-style pattern as received on the wire (e.g. "DD MMM YYYY") - converted at parse time. */
    val dateFormat: String = "DD MMM YYYY",
)
