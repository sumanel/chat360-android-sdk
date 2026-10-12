package com.chat360.chatbot.ui.components.chrome

import com.chat360.chatbot.cache.CachedConversationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * The history list is ordered by last activity, so it has to be sectioned by last activity too. Sectioning by
 * creation date put a chat that had just been used at the top of "Yesterday" (or wherever it was created) instead
 * of the top of the list.
 */
class HistoryGroupingTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 20, 15, 0, 0) }.timeInMillis
    private val day = 24 * 60 * 60 * 1000L

    private fun chat(id: String, createdAt: Long, updatedAt: Long) =
        CachedConversationEntity(id = id, botId = "bot", roomId = "room-$id", title = id, createdAt = createdAt, updatedAt = updatedAt)

    private fun sectionOf(id: String, groups: List<Pair<String, List<CachedConversationEntity>>>) =
        groups.firstOrNull { (_, items) -> items.any { it.id == id } }?.first

    @Test
    fun `a chat created days ago but used just now is under Today`() {
        val groups = groupConversationsByDate(
            listOf(chat("old-but-active", createdAt = now - 10 * day, updatedAt = now - 60_000)),
            nowMs = now,
        )

        assertEquals("Today", sectionOf("old-but-active", groups))
    }

    @Test
    fun `the section order follows the list order - the most recently used chat is first overall`() {
        val newest = chat("used-now", createdAt = now - 3 * day, updatedAt = now - 1_000)
        val yesterday = chat("yesterday", createdAt = now - day, updatedAt = now - day)
        val groups = groupConversationsByDate(listOf(newest, yesterday), nowMs = now)

        assertEquals(listOf("Today", "Yesterday"), groups.map { it.first })
        assertEquals("used-now", groups.first().second.first().id)
    }

    @Test
    fun `chats fall into the section of their last activity`() {
        val groups = groupConversationsByDate(
            listOf(
                chat("a", createdAt = 1, updatedAt = now - 2 * day),
                chat("b", createdAt = 1, updatedAt = now - 20 * day),
                chat("c", createdAt = 1, updatedAt = now - 90 * day),
            ),
            nowMs = now,
        )

        assertEquals("Previous 7 Days", sectionOf("a", groups))
        assertEquals("Previous 30 Days", sectionOf("b", groups))
        assertEquals("Older", sectionOf("c", groups))
        assertNull(sectionOf("missing", groups))
    }
}
