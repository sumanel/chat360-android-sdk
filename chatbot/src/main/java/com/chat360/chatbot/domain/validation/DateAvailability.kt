package com.chat360.chatbot.domain.validation

import com.chat360.chatbot.model.wire.DateRules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ports the disabled-date rule evaluation shared by DATE prompts (standalone `Date/index.tsx`
 * and FORM DATE fields' `excludeDate`) - one evaluator so both call sites agree.
 */
object DateAvailability {

    fun isDisabled(rules: DateRules, date: LocalDate): Boolean {
        val today = LocalDate.now()
        if (rules.disableFuture && date.isAfter(today)) return true
        if (rules.disableCurrent && date.isEqual(today)) return true
        if (rules.disablePrevious && date.isBefore(today)) return true
        if (rules.isScheduledDate) {
            // LocalDate.dayOfWeek: Monday=1..Sunday=7; dayjs .day(): Sunday=0..Saturday=6.
            val dayIndex = date.dayOfWeek.value % 7
            if (rules.disabledDays?.getOrNull(dayIndex) == true) return true
            if (rules.disabledDates.orEmpty().any { sameDay(it, date) }) return true
        }
        if (rules.manageWithVariable && !rules.variableDates.isNullOrEmpty()) {
            val inVariable = rules.variableDates.any { sameDay(it, date) }
            return when (rules.variableMode) {
                "disable_in_var" -> inVariable
                "disable_not_in_var" -> !inVariable
                else -> false
            }
        }
        return false
    }

    /** Parses a field's typed value against its own dayjs-style [pattern] (e.g. "DD MMM YYYY"). */
    fun parse(value: String, pattern: String): LocalDate? = runCatching {
        LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern(toJavaPattern(pattern), Locale.ENGLISH))
    }.getOrNull()

    /** Formats [date] using the same dayjs-style [pattern] a DATE node's own value round-trips through. */
    fun format(date: LocalDate, pattern: String): String =
        date.format(DateTimeFormatter.ofPattern(toJavaPattern(pattern), Locale.ENGLISH))

    private fun toJavaPattern(pattern: String): String = pattern
        .replace("YYYY", "yyyy")
        .replace("YY", "yy")
        .replace("DD", "dd")
        .replace("D", "d")

    private fun sameDay(raw: String, date: LocalDate): Boolean {
        val parsed = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() ?: return false
        return parsed.isEqual(date)
    }
}
