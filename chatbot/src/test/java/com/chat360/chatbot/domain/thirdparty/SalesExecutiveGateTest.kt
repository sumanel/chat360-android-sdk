package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Real time on purpose (`runBlocking`, not `runTest`): the gate's timeout guards real network I/O, and a test
 * scheduler's virtual clock would fire it instantly, cancelling the request before it is even sent.
 *
 * The sales-executive gate closes the chat ONLY on a successful reply that says INACTIVE. Every other outcome -
 * a failing, missing, slow or nonsensical check - must let the user through with the bot flow untouched.
 * The replies below are the real ones from staging.
 */
class SalesExecutiveGateTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ThirdPartyTasksApiService

    private val details = mapOf("dealer_code" to "W4300", "emp_code" to "EMP1101")

    /** Onboarded as INACTIVE - the reply the server gave for a brand-new executive. */
    private val inactiveReply = """{"success":true,"message":"Sales Executive onboarded as INACTIVE.","is_new":true,"status_downgraded_to_inactive":false,"sales_executive":{"id":12,"emp_code":"EMP1101","name":"","role":null,"dealer_code":"W4300","dealer_name":"Hindustan Hyundai","status":"INACTIVE"}}"""

    /** A known, active executive - what staging answers for EMP1101 today. */
    private val activeReply = """{"success":true,"message":"Sales Executive validated successfully.","is_new":false,"status_downgraded_to_inactive":false,"sales_executive":{"id":12,"emp_code":"EMP1101","name":"","role":"Trainer","dealer_code":"W4300","dealer_name":"Hindustan Hyundai","status":"ACTIVE"}}"""

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = ThirdPartyTasksApiService(server.url("/").toString())
    }

    @After
    fun tearDown() = runCatching { server.shutdown() }.let { }

    private fun gate(map: Map<String, String> = details, timeoutMs: Long = 3_000) = SalesExecutiveGate(api, "client-1", map, timeoutMs)

    // --- the request ---

    @Test
    fun `it posts the details as JSON with a Client-Id header and no bearer token`() = runBlocking {
        server.enqueue(MockResponse().setBody(activeReply))

        gate(details + mapOf("name" to "Ravi Kumar", "status" to "INACTIVE")).blockedMessage()

        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/api/third-party-tasks/sales-exectives", request.path)
        assertEquals("client-1", request.getHeader("Client-Id"))
        assertNull("the server rejects the underscored spelling", request.getHeader("client_id"))
        assertNull(request.getHeader("Authorization"))
        // Without this content type the live server ignores the body and reports both fields as required.
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals(
            mapOf("dealer_code" to "W4300", "emp_code" to "EMP1101", "name" to "Ravi Kumar", "status" to "INACTIVE"),
            body.mapValues { (it.value as JsonPrimitive).content },
        )
    }

    @Test
    fun `optional fields are optional - only dealer_code and emp_code are needed`() = runBlocking {
        server.enqueue(MockResponse().setBody(activeReply))

        gate().blockedMessage()

        val body = Json.parseToJsonElement(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()) as JsonObject
        assertEquals(setOf("dealer_code", "emp_code"), body.keys)
    }

    @Test
    fun `without a dealer_code and emp_code it never asks`() = runBlocking {
        assertNull(gate(mapOf("emp_code" to "EMP1101")).blockedMessage())
        assertNull(gate(mapOf("dealer_code" to "W4300", "emp_code" to "  ")).blockedMessage())
        assertNull(gate(emptyMap()).blockedMessage())

        assertEquals(0, server.requestCount)
    }

    // --- when it closes the chat ---

    @Test
    fun `an INACTIVE executive closes the chat with the server's message`() = runBlocking {
        server.enqueue(MockResponse().setBody(inactiveReply))

        assertEquals("Sales Executive onboarded as INACTIVE.", gate().blockedMessage())
    }

    @Test
    fun `the status is compared without regard to case`() = runBlocking {
        server.enqueue(MockResponse().setBody(inactiveReply.replace("\"INACTIVE\"}", "\"inactive\"}")))

        assertNotNull(gate().blockedMessage())
    }

    @Test
    fun `a blank server message falls back to a default so the screen is never empty`() = runBlocking {
        server.enqueue(MockResponse().setBody(inactiveReply.replace("Sales Executive onboarded as INACTIVE.", "")))

        assertEquals(SalesExecutiveGate.DEFAULT_MESSAGE, gate().blockedMessage())
    }

    // --- everything else lets the user through ---

    @Test
    fun `an ACTIVE executive is let through`() = runBlocking {
        server.enqueue(MockResponse().setBody(activeReply))

        assertNull(gate().blockedMessage())
    }

    @Test
    fun `INACTIVE on an unsuccessful reply is not trusted`() = runBlocking {
        server.enqueue(MockResponse().setBody(inactiveReply.replace("\"success\":true", "\"success\":false")))

        assertNull(gate().blockedMessage())
    }

    @Test
    fun `an unknown or missing status is let through`() = runBlocking {
        server.enqueue(MockResponse().setBody(activeReply.replace("\"ACTIVE\"", "\"SUSPENDED\"")))
        server.enqueue(MockResponse().setBody("""{"success":true,"message":"ok","sales_executive":{}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"message":"ok"}"""))

        assertNull(gate().blockedMessage())
        assertNull(gate().blockedMessage())
        assertNull(gate().blockedMessage())
    }

    @Test
    fun `every kind of failure lets the user through`() = runBlocking {
        val failures = listOf(
            // what the live server answers for validation errors, an unconfigured client and the wrong header spelling
            MockResponse().setResponseCode(400).setBody("""{"success":false,"errors":{"emp_code":["This field is required."]}}"""),
            MockResponse().setResponseCode(400).setBody("""{"success":false,"message":"Sales executive onboarding is not configured for this client."}"""),
            MockResponse().setResponseCode(400).setBody("""{"detail":"client_id header is required"}"""),
            MockResponse().setResponseCode(404).setBody("<!DOCTYPE html><html>Page not found</html>"), // endpoint not deployed
            MockResponse().setResponseCode(401),
            MockResponse().setResponseCode(500),
            MockResponse().setResponseCode(200).setBody("<html>a proxy error page</html>"),
            MockResponse().setResponseCode(200).setBody("""["not","an","object"]"""),
            MockResponse().setResponseCode(200).setBody("{ this is not json"),
            MockResponse().setResponseCode(200).setBody(""),
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START),
        )
        for (reply in failures) {
            server.enqueue(reply)
            assertNull("blocked on: ${reply.status}", gate().blockedMessage())
        }
    }

    @Test
    fun `a server that is unreachable lets the user through`() = runBlocking {
        server.shutdown()

        assertNull(gate().blockedMessage())
    }

    @Test
    fun `a slow server lets the user through once the timeout passes instead of stalling the chat`() = runBlocking {
        server.enqueue(MockResponse().setBody(inactiveReply).setBodyDelay(3, TimeUnit.SECONDS))
        val started = System.currentTimeMillis()

        val message = gate(timeoutMs = 300).blockedMessage()

        assertNull("a reply that arrives after the timeout must not block", message)
        assertTrue("waited ${System.currentTimeMillis() - started}ms for a 300ms timeout", System.currentTimeMillis() - started < 2_000)
    }

    // --- remembering the answer ---

    @Test
    fun `once the server says the executive is active it is not asked again this session`() = runBlocking {
        server.enqueue(MockResponse().setBody(activeReply))
        val gate = gate()

        assertNull(gate.blockedMessage())
        assertNull(gate.blockedMessage())
        assertNull(gate.blockedMessage())

        assertEquals("re-asking on every foreground would only add traffic", 1, server.requestCount)
    }

    @Test
    fun `a block is re-checked every time, so an executive who is activated gets in`() = runBlocking {
        server.enqueue(MockResponse().setBody(inactiveReply))
        server.enqueue(MockResponse().setBody(activeReply))
        val gate = gate()

        assertNotNull(gate.blockedMessage())
        assertNull("an admin activated the executive", gate.blockedMessage())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failure is not remembered as a block, and not as a pass either`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody(inactiveReply))
        val gate = gate()

        assertNull(gate.blockedMessage())
        assertNotNull("the next check must really ask again", gate.blockedMessage())
    }
}
