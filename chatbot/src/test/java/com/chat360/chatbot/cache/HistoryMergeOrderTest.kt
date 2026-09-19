package com.chat360.chatbot.cache

import com.chat360.chatbot.model.wire.RawSocketEnvelope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** A local send the server never matched by id used to be appended after all of history, so an
 * older message rendered below a newer reply ("10:06" above "10:01"). */
class HistoryMergeOrderTest {

    private fun frame(text: String, epochSeconds: Long) =
        RawSocketEnvelope(type = "chatgpt_message", message = JsonPrimitive(text), timestamp_int = epochSeconds.toString())

    private fun order(rows: List<CachedMessageEntity>) = rows.map {
        if (it.kind == "USER") it.payload else it.payload.substringAfter("\"message\":\"").substringBefore("\"")
    }

    @Test
    fun `an unindexed send older than a history reply is placed before it`() = runTest {
        val dao = FakeChatCacheDao()
        val repo = ChatCacheRepository(dao)
        dao.upsertConversation(CachedConversationEntity("c", "bot", "room", "t", 1, 1))
        dao.insertMessage(CachedMessageEntity(conversationId = "c", kind = "USER", payload = "hii", chatMsgId = null, createdAt = 1_003_000))

        repo.replaceRawHistory("c", listOf(frame("first", 1_001), frame("venue reply", 1_006)))

        assertEquals(listOf("first", "hii", "venue reply"), order(dao.messages("c")))
    }

    @Test
    fun `an unindexed send newer than all of history stays last`() = runTest {
        val dao = FakeChatCacheDao()
        val repo = ChatCacheRepository(dao)
        dao.upsertConversation(CachedConversationEntity("c", "bot", "room", "t", 1, 1))
        dao.insertMessage(CachedMessageEntity(conversationId = "c", kind = "USER", payload = "hii", chatMsgId = null, createdAt = 1_010_000))

        repo.replaceRawHistory("c", listOf(frame("first", 1_001), frame("second", 1_006)))

        assertEquals(listOf("first", "second", "hii"), order(dao.messages("c")))
    }
}
