package com.chat360.chatbot.domain.validation

import com.chat360.chatbot.model.wire.DateRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateAvailabilityTest {

    @Test
    fun `disableFuture blocks dates after today`() {
        val rules = DateRules(disableFuture = true)
        assertTrue(DateAvailability.isDisabled(rules, LocalDate.now().plusDays(1)))
        assertFalse(DateAvailability.isDisabled(rules, LocalDate.now()))
        assertFalse(DateAvailability.isDisabled(rules, LocalDate.now().minusDays(1)))
    }

    @Test
    fun `disablePrevious blocks dates before today`() {
        val rules = DateRules(disablePrevious = true)
        assertTrue(DateAvailability.isDisabled(rules, LocalDate.now().minusDays(1)))
        assertFalse(DateAvailability.isDisabled(rules, LocalDate.now()))
    }

    @Test
    fun `isScheduledDate blocks the configured weekday - Sunday index 0`() {
        val sunday = LocalDate.of(2026, 8, 2) // a Sunday
        val rules = DateRules(isScheduledDate = true, disabledDays = listOf(true, false, false, false, false, false, false))
        assertTrue(DateAvailability.isDisabled(rules, sunday))
        assertFalse(DateAvailability.isDisabled(rules, sunday.plusDays(1))) // Monday
    }

    @Test
    fun `isScheduledDate blocks explicit disabledDates`() {
        val rules = DateRules(isScheduledDate = true, disabledDates = listOf("2026-08-15"))
        assertTrue(DateAvailability.isDisabled(rules, LocalDate.of(2026, 8, 15)))
        assertFalse(DateAvailability.isDisabled(rules, LocalDate.of(2026, 8, 16)))
    }

    @Test
    fun `manageWithVariable disable_in_var blocks only listed dates`() {
        val rules = DateRules(manageWithVariable = true, variableMode = "disable_in_var", variableDates = listOf("2026-09-01"))
        assertTrue(DateAvailability.isDisabled(rules, LocalDate.of(2026, 9, 1)))
        assertFalse(DateAvailability.isDisabled(rules, LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun `manageWithVariable disable_not_in_var blocks everything except listed dates`() {
        val rules = DateRules(manageWithVariable = true, variableMode = "disable_not_in_var", variableDates = listOf("2026-09-01"))
        assertFalse(DateAvailability.isDisabled(rules, LocalDate.of(2026, 9, 1)))
        assertTrue(DateAvailability.isDisabled(rules, LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun `parse and format round-trip a dayjs-style pattern`() {
        val date = LocalDate.of(2026, 8, 15)
        val formatted = DateAvailability.format(date, "DD MMM YYYY")
        assertEquals("15 Aug 2026", formatted)
        assertEquals(date, DateAvailability.parse(formatted, "DD MMM YYYY"))
    }
}
