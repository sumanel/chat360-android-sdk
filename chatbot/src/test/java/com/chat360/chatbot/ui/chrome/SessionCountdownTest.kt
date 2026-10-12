package com.chat360.chatbot.ui.components.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The session timer disappears when the hour is up instead of sitting on 00:00 until the user sends again. */
class SessionCountdownTest {
    private val start = 1_000_000_000_000L

    @Test
    fun `a fresh session shows the full hour`() = assertEquals("60:00", formatSessionCountdown(start, nowMs = start))

    @Test
    fun `it counts down`() = assertEquals("59:59", formatSessionCountdown(start, nowMs = start + 1_000))

    @Test
    fun `the last second still shows`() = assertEquals("00:01", formatSessionCountdown(start, nowMs = start + 3_599_000))

    @Test
    fun `when the hour is up there is nothing to show`() = assertNull(formatSessionCountdown(start, nowMs = start + 3_600_000))

    @Test
    fun `a session that ended long ago shows nothing`() = assertNull(formatSessionCountdown(start, nowMs = start + 10 * 3_600_000))
}
