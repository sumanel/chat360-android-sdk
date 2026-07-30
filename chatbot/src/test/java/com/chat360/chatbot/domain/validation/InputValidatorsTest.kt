package com.chat360.chatbot.domain.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorsTest {

    @Test
    fun `validateEmail accepts well-formed addresses and rejects malformed ones`() {
        assertTrue(InputValidators.validateEmail("user@example.com"))
        assertTrue(InputValidators.validateEmail("first.last+tag@sub.example.co"))
        assertFalse(InputValidators.validateEmail("not-an-email"))
        assertFalse(InputValidators.validateEmail("missing@domain"))
        assertFalse(InputValidators.validateEmail("@example.com"))
    }

    @Test
    fun `validateTest flags the literal word test in email local part or domain`() {
        assertTrue(InputValidators.validateTest("test@example.com"))
        assertTrue(InputValidators.validateTest("user@test.com"))
        assertFalse(InputValidators.validateTest("attestation@example.com"))
        assertFalse(InputValidators.validateTest("real.user@example.com"))
    }

    @Test
    fun `validateTestName flags standalone word test anywhere in a name`() {
        assertTrue(InputValidators.validateTestName("test"))
        assertTrue(InputValidators.validateTestName("John test Doe"))
        assertFalse(InputValidators.validateTestName("Testimony"))
        assertFalse(InputValidators.validateTestName("John Doe"))
    }

    @Test
    fun `validatePhoneNumber rejects a single repeated digit regardless of format`() {
        assertFalse(InputValidators.validatePhoneNumber("1111111111", international = false))
        assertFalse(InputValidators.validatePhoneNumber("0000000000", international = true))
    }

    @Test
    fun `validatePhoneNumber accepts well-formed 10-digit numbers`() {
        assertTrue(InputValidators.validatePhoneNumber("9876543210", international = false))
        assertTrue(InputValidators.validatePhoneNumber("+919876543210", international = true))
        assertFalse(InputValidators.validatePhoneNumber("12345", international = false))
    }

    @Test
    fun `hasOnlyCharacter accepts letters and spaces only`() {
        assertTrue(InputValidators.hasOnlyCharacter("John Doe"))
        assertFalse(InputValidators.hasOnlyCharacter("John123"))
        assertFalse(InputValidators.hasOnlyCharacter("John_Doe"))
    }

    @Test
    fun `sanitizeInput escapes HTML-significant characters`() {
        assertEqualsSanitized("&lt;script&gt;alert(&quot;x&quot;)&lt;&#x2F;script&gt;", "<script>alert(\"x\")</script>")
        assertEqualsSanitized("Tom &amp; Jerry", "Tom & Jerry")
    }

    private fun assertEqualsSanitized(expected: String, input: String) {
        org.junit.Assert.assertEquals(expected, InputValidators.sanitizeInput(input))
    }
}
