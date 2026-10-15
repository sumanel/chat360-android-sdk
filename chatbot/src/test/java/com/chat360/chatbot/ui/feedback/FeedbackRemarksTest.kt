package com.chat360.chatbot.ui.components.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Thumbs down needs a reason (20+ characters); thumbs up asks for one too, but it is optional. */
class FeedbackRemarksTest {

    // --- dislike: mandatory ---

    @Test
    fun `a dislike reason under the minimum is not valid`() = assertFalse(remarksAreValid(trimmedLength = 19, minLength = DISLIKE_MIN_REMARKS_LENGTH))

    @Test
    fun `a dislike reason at the minimum is valid`() = assertTrue(remarksAreValid(trimmedLength = 20, minLength = DISLIKE_MIN_REMARKS_LENGTH))

    @Test
    fun `a dislike has a 20 character minimum`() = assertEquals(20, DISLIKE_MIN_REMARKS_LENGTH)

    // --- like: optional ---

    @Test
    fun `a like needs no characters`() = assertEquals(0, LIKE_MIN_REMARKS_LENGTH)

    @Test
    fun `an empty like reason is valid`() = assertTrue(remarksAreValid(trimmedLength = 0, minLength = LIKE_MIN_REMARKS_LENGTH))

    @Test
    fun `a short like reason is valid`() = assertTrue(remarksAreValid(trimmedLength = 3, minLength = LIKE_MIN_REMARKS_LENGTH))

    // --- what is sent ---

    @Test
    fun `typed text is trimmed before it is sent`() = assertEquals("Very helpful", remarksToSend("  Very helpful \n"))

    @Test
    fun `blank text is sent as no remarks at all`() {
        assertNull(remarksToSend(""))
        assertNull(remarksToSend("   \n "))
    }
}
