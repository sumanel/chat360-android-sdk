package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import com.chat360.chatbot.ui.theme.Chat360Branding
import com.chat360.chatbot.ui.theme.withWelcome
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The server-configured welcome copy: the request, how the reply is read, and how it combines with what the host app set. */
class WelcomeTextTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ThirdPartyTasksApiService

    private class MemoryStore : WelcomeTextStore {
        var saved: WelcomeText? = null
        override fun load(clientId: String) = saved
        override fun save(clientId: String, welcomeText: WelcomeText?) { saved = welcomeText }
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = ThirdPartyTasksApiService(server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    // --- the request and the reply ---

    @Test
    fun `it asks for the client's welcome text with a Client-Id header and no bearer token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"heading":"Hi","text":"Ask me anything","client_id":"client-1"}"""))

        api.fetchWelcomeText("client-1")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/third-party-tasks/welcome-text", request.path)
        assertEquals("client-1", request.getHeader("Client-Id"))
        // The live server rejects the underscored spelling ("client_id header is required"), so it must never be sent.
        assertNull(request.getHeader("client_id"))
        assertNull("no bearer token is needed for this endpoint", request.getHeader("Authorization"))
    }

    @Test
    fun `a bare reply with heading and text is read`() = runTest {
        server.enqueue(MockResponse().setBody("""{"heading":"Welcome to Hyundai","text":"I can help you choose a car.","client_id":"client-1"}"""))

        assertEquals(WelcomeText("Welcome to Hyundai", "I can help you choose a car."), api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `a reply wrapped in data like the other third-party endpoints is read too`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"heading":"Welcome","text":"Hello there","client_id":"client-1"}}"""))

        assertEquals(WelcomeText("Welcome", "Hello there"), api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `data as a list of objects is read, whether it holds one entry or several`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":[{"heading":"List heading","text":"List text"}]}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"data":[{"heading":"First","text":"One"},{"heading":"Second","text":"Two"}]}"""))

        assertEquals(WelcomeText("List heading", "List text"), api.fetchWelcomeText("client-1"))
        assertEquals("the first entry wins", WelcomeText("First", "One"), api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `in a list, an empty entry is skipped in favour of the first one that has content`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"heading":"","text":""},"not an object",{"heading":"Real heading","text":"Real text"}]}"""))

        assertEquals(WelcomeText("Real heading", "Real text"), api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `a list whose entries are all empty means nothing is configured`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true,"data":[{"heading":"","text":""},{}]}"""))

        assertNull(api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `a field that is blank counts as not configured, the other one is still used`() = runTest {
        server.enqueue(MockResponse().setBody("""{"heading":"   ","text":"Only the subtitle"}"""))

        assertEquals(WelcomeText(null, "Only the subtitle"), api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `the server's empty reply, data as an empty array, means nothing is configured`() = runTest {
        // Exactly what staging returns for a client with no welcome text set.
        server.enqueue(MockResponse().setBody("""{"success":true,"data":[]}"""))

        assertNull(api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `both fields empty or null means nothing is configured`() = runTest {
        server.enqueue(MockResponse().setBody("""{"heading":"","text":null,"client_id":"client-1"}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{}}"""))

        assertNull(api.fetchWelcomeText("client-1"))
        assertNull(api.fetchWelcomeText("client-1"))
    }

    @Test
    fun `the endpoint not being deployed yet, or any error, throws instead of pretending there is no text`() = runTest {
        for (reply in listOf(
            MockResponse().setResponseCode(404).setBody("<!DOCTYPE html><html>Page not found</html>"),
            MockResponse().setResponseCode(401).setBody("""{"success":false}"""),
            MockResponse().setResponseCode(500),
            MockResponse().setBody("<html>a proxy error page</html>"),
            MockResponse().setBody("""["not","an","object"]"""),
        )) {
            server.enqueue(reply)
            try {
                api.fetchWelcomeText("client-1")
                fail("expected a failure for: ${reply.status}")
            } catch (expected: Exception) {
                // any failure - the repository treats them all the same way
            }
        }
    }

    // --- the repository and its cache ---

    @Test
    fun `a good reply is cached and returned`() = runTest {
        server.enqueue(MockResponse().setBody("""{"heading":"Hi","text":"There"}"""))
        val store = MemoryStore()

        val result = WelcomeTextRepository(api, "client-1", store).refresh()

        assertEquals(WelcomeText("Hi", "There"), result.getOrNull())
        assertEquals(WelcomeText("Hi", "There"), store.saved)
    }

    @Test
    fun `a failure keeps the cached welcome, so a flaky connection never wipes a working one`() = runTest {
        val store = MemoryStore().apply { saved = WelcomeText("Cached heading", "Cached text") }
        server.enqueue(MockResponse().setResponseCode(404))
        val repository = WelcomeTextRepository(api, "client-1", store)

        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals(WelcomeText("Cached heading", "Cached text"), repository.cached())
    }

    @Test
    fun `the server clearing its welcome text clears the cache, so a stale one does not linger`() = runTest {
        val store = MemoryStore().apply { saved = WelcomeText("Old heading", "Old text") }
        server.enqueue(MockResponse().setBody("""{"heading":"","text":""}"""))
        val repository = WelcomeTextRepository(api, "client-1", store)

        val result = repository.refresh()

        assertTrue("an empty reply is a success, not a failure", result.isSuccess)
        assertNull(result.getOrNull())
        assertNull(repository.cached())
    }

    // --- how it combines with the host app's own text ---
    // The branding passed in has already been resolved from the host app's welcomeTitle / welcomeSubtitle
    // (falling back to the theme default) - see Chat360Theme.

    private val hostBranding = Chat360Branding(botTitle = "Bot", logo = null, welcomeHeading = "Host heading", disclaimerText = "Host subtitle")

    @Test
    fun `server text wins over the host app's text`() {
        val result = hostBranding.withWelcome(WelcomeText("Server heading", "Server subtitle"))

        assertEquals("Server heading", result.welcomeHeading)
        assertEquals("Server subtitle", result.disclaimerText)
    }

    @Test
    fun `nothing from the server leaves the host app's text`() {
        assertEquals(hostBranding, hostBranding.withWelcome(null))
        assertEquals(hostBranding, hostBranding.withWelcome(WelcomeText(null, null)))
        assertEquals(hostBranding, hostBranding.withWelcome(WelcomeText("", "  ")))
    }

    @Test
    fun `each line falls back on its own`() {
        val onlyHeading = hostBranding.withWelcome(WelcomeText("Server heading", null))
        assertEquals("Server heading", onlyHeading.welcomeHeading)
        assertEquals("Host subtitle", onlyHeading.disclaimerText)

        val onlyText = hostBranding.withWelcome(WelcomeText(null, "Server subtitle"))
        assertEquals("Host heading", onlyText.welcomeHeading)
        assertEquals("Server subtitle", onlyText.disclaimerText)
    }

    @Test
    fun `other branding is untouched`() {
        val result = hostBranding.copy(botTitle = "Custom bot", inputPlaceholder = "Ask…").withWelcome(WelcomeText("H", "T"))

        assertEquals("Custom bot", result.botTitle)
        assertEquals("Ask…", result.inputPlaceholder)
    }
}
