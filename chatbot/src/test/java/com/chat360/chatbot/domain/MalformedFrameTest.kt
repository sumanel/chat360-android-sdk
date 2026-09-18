package com.chat360.chatbot.domain

import org.junit.Assert.assertNull
import org.junit.Test

/** A frame the parser can't read used to throw out of the main-thread socket callback and crash
 * the host app; it must be dropped instead. */
class MalformedFrameTest {

    private fun feed(raw: String): Throwable? {
        val repo = ChatRepository(baseUrl = "http://x", botId = "b")
        val method = ChatRepository::class.java.getDeclaredMethod("handleIncoming", String::class.java, Function1::class.java)
        method.isAccessible = true
        return try {
            method.invoke(repo, raw, { _: String -> })
            null
        } catch (e: java.lang.reflect.InvocationTargetException) {
            e.cause
        }
    }

    @Test fun `plain text does not crash`() = assertNull(feed("not json"))
    @Test fun `an html proxy error page does not crash`() = assertNull(feed("<html><body>502 Bad Gateway</body></html>"))
    @Test fun `an empty frame does not crash`() = assertNull(feed(""))
    @Test fun `a json array does not crash`() = assertNull(feed("[1,2]"))
    @Test fun `wrongly typed fields do not crash`() = assertNull(feed("""{"type":123,"room_id":{"a":1}}"""))
    @Test fun `a truncated frame does not crash`() = assertNull(feed("""{"type":"bot_message","room_id":"r1","message":{"tex"""))
}
