package com.chat360.chatbot.ui.components.messages

import org.junit.Assert.assertEquals
import org.junit.Test

/** A dislike is final: once a message is disliked it can be neither liked nor un-disliked. */
class ReactionTapTest {

    // --- nothing chosen yet ---

    @Test
    fun `an unrated message opens the like popup on thumbs up`() = assertEquals(ReactionTap.OPEN_LIKE_POPUP, likeTap(current = null))

    @Test
    fun `an unrated message opens the dislike popup on thumbs down`() = assertEquals(ReactionTap.OPEN_DISLIKE_POPUP, dislikeTap(current = null))

    // --- after a dislike ---

    @Test
    fun `after a dislike thumbs up does nothing`() = assertEquals(ReactionTap.IGNORE, likeTap(current = false))

    @Test
    fun `after a dislike tapping thumbs down again does not undo it`() = assertEquals(ReactionTap.IGNORE, dislikeTap(current = false))

    // --- after a like ---

    @Test
    fun `a liked message can still be unliked`() = assertEquals(ReactionTap.CLEAR, likeTap(current = true))

    @Test
    fun `a liked message can be changed to a dislike, which then asks for its reason`() =
        assertEquals(ReactionTap.OPEN_DISLIKE_POPUP, dislikeTap(current = true))

    // --- the whole sequence ---

    @Test
    fun `dislike then like then dislike changes nothing`() {
        var rating: Boolean? = null
        fun apply(tap: ReactionTap, newRating: Boolean?) {
            rating = when (tap) {
                ReactionTap.IGNORE -> rating
                ReactionTap.CLEAR -> null
                else -> newRating
            }
        }
        apply(dislikeTap(rating), false)
        apply(likeTap(rating), true)
        apply(dislikeTap(rating), false)
        apply(likeTap(rating), true)
        assertEquals("still disliked", false, rating)
    }
}
