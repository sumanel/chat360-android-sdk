package com.chat360.chatbot.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIdentityGuardTest {
    private val users = mutableMapOf<String, String>()
    private val wiped = mutableListOf<String>()
    private val guard = ChatIdentityGuard(
        lastUser = object : ChatIdentityGuard.LastUserStore {
            override fun get(botId: String) = users[botId]
            override fun set(botId: String, endUserId: String?) {
                if (endUserId == null) users.remove(botId) else users[botId] = endUserId
            }
        },
        wipe = { wiped += it },
    )

    @Test fun `first open with a user clears data left by an older version`() {
        assertTrue(guard.apply("bot", "EMP1"))
        assertEquals(listOf("bot"), wiped)
    }

    @Test fun `first open without a user keeps data`() {
        assertFalse(guard.apply("bot", null))
        assertTrue(wiped.isEmpty())
    }

    @Test fun `same user keeps data and a different user clears it`() {
        guard.apply("bot", "EMP1")
        wiped.clear()
        assertFalse(guard.apply("bot", " EMP1 "))
        assertTrue(wiped.isEmpty())
        assertTrue(guard.apply("bot", "EMP2"))
        assertEquals(listOf("bot"), wiped)
    }

    @Test fun `bots are independent`() {
        guard.apply("a", "EMP1")
        wiped.clear()
        assertTrue(guard.apply("b", "EMP2").also { assertEquals(listOf("b"), wiped) })
        assertFalse(guard.apply("a", "EMP1"))
    }

    @Test fun `reset clears and forgets the user`() {
        guard.apply("bot", "EMP1")
        wiped.clear()
        guard.reset("bot")
        assertEquals(listOf("bot"), wiped)
        assertTrue(guard.apply("bot", "EMP1"))
    }
}
