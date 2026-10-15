package com.chat360.chatbot.domain.thirdparty

import com.chat360.chatbot.config.FeatureConfig
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyHttpException
import com.chat360.chatbot.network.rest.thirdparty.ThirdPartyTasksApiService
import com.chat360.chatbot.ui.components.feedback.APP_FEEDBACK_MIN_REMARKS_LENGTH
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The application-wide feedback: the drawer's feedback icon opens a popup, and its text goes to
 * `POST /api/third-party-tasks/app-feedback` together with the room, dealer and employee.
 */
class AppFeedbackTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ThirdPartyTasksApiService

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = ThirdPartyTasksApiService(server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = MessageFeedbackRepository(api, ThirdPartyTokenManager(api, clientId = "client-1", apiKey = "api-key-1"))

    private fun tokenResponse(token: String) =
        MockResponse().setResponseCode(200).setBody("""{"success":true,"data":{"bearer_token":"$token","token_type":"Bearer","expires_in":3600}}""")

    private fun bodyOf(request: okhttp3.mockwebserver.RecordedRequest): JsonObject = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    // --- the request ---

    @Test
    fun `it posts to the app-feedback endpoint with the bearer token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        api.submitAppFeedback("room-1", "Search results are very helpful", "D1", "EMP101", bearerToken = "tok-1")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/third-party-tasks/app-feedback", request.path)
        assertEquals("Bearer tok-1", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `the body carries the room, the feedback, the dealer and the employee`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        api.submitAppFeedback("room-1", "Search results are very helpful", "D1", "EMP101", bearerToken = "tok-1")

        val body = bodyOf(server.takeRequest())
        assertEquals("room-1", body["room_id"]!!.jsonPrimitive.content)
        assertEquals("Search results are very helpful", body["feedback"]!!.jsonPrimitive.content)
        assertEquals("D1", body["dealer_id"]!!.jsonPrimitive.content)
        assertEquals("EMP101", body["emp_id"]!!.jsonPrimitive.content)
        assertEquals("no other fields are sent", setOf("room_id", "feedback", "dealer_id", "emp_id"), body.keys)
    }

    @Test
    fun `a dealer or employee the host never set is left out`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        api.submitAppFeedback("room-1", "Nice", dealerId = null, empId = "EMP101", bearerToken = "tok-1")

        val body = bodyOf(server.takeRequest())
        assertFalse(body.containsKey("dealer_id"))
        assertEquals("EMP101", body["emp_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a server error throws`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            api.submitAppFeedback("room-1", "Nice", "D1", "EMP101", bearerToken = "tok-1")
            fail("a 500 must not look like success")
        } catch (e: ThirdPartyHttpException) {
            assertEquals(500, e.httpCode)
        }
    }

    // --- the repository (token + result) ---

    @Test
    fun `the repository sends with its own token, trimmed, and reports success`() = runTest {
        server.enqueue(tokenResponse("token-1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val ok = repository().submitAppFeedback("room-1", "  Very helpful \n", "D1", "EMP101")

        assertTrue(ok)
        server.takeRequest() // auth/token
        val request = server.takeRequest()
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertEquals("Very helpful", bodyOf(request)["feedback"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the repository reports a failure`() = runTest {
        server.enqueue(tokenResponse("token-1"))
        server.enqueue(MockResponse().setResponseCode(500))

        assertFalse(repository().submitAppFeedback("room-1", "Nice", "D1", "EMP101"))
    }

    @Test
    fun `the repository retries once with a fresh token after a 401`() = runTest {
        server.enqueue(tokenResponse("token-1"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(tokenResponse("token-2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertTrue(repository().submitAppFeedback("room-1", "Nice", "D1", "EMP101"))
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `blank feedback is never sent`() = runTest {
        assertFalse(repository().submitAppFeedback("room-1", "  \n ", "D1", "EMP101"))
        assertEquals(0, server.requestCount)
    }

    // --- who the feedback is from ---

    @Test
    fun `the meta supplies the dealer and employee`() {
        val identity = AppFeedbackIdentity.resolve(meta = mapOf("dealer_id" to "D1", "emp_id" to "12345"), endUserId = "EMP5")
        assertEquals("D1", identity.dealerId)
        assertEquals("12345", identity.empId)
    }

    @Test
    fun `the end user id is the employee of last resort`() {
        val identity = AppFeedbackIdentity.resolve(meta = null, endUserId = "EMP5")
        assertNull(identity.dealerId)
        assertEquals("EMP5", identity.empId)
    }

    @Test
    fun `blank values count as not set`() {
        val identity = AppFeedbackIdentity.resolve(meta = mapOf("dealer_id" to "D1", "emp_id" to " "), endUserId = " EMP5 ")
        assertEquals("D1", identity.dealerId)
        assertEquals("EMP5", identity.empId)
    }

    // --- the popup and the menu icon ---

    @Test
    fun `the app feedback popup needs some text`() = assertEquals(1, APP_FEEDBACK_MIN_REMARKS_LENGTH)

    @Test
    fun `the menu icon is off until the host turns it on`() {
        assertFalse(FeatureConfig().showAppFeedback)
        assertTrue(FeatureConfig(showAppFeedback = true).showAppFeedback)
    }
}
