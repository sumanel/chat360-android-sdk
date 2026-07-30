package com.chat360.chatbot.domain.validation

/** Ports the exact regexes/checks from the widget's `src/utils/common.ts` - the server-side
 * contract for what counts as a valid email/phone/name doesn't change just because the client
 * is native now. */
object InputValidators {

    private val emailRegex = Regex(
        "^(([^<>()\\[\\]\\\\.,;:\\s@\"]+(\\.[^<>()\\[\\]\\\\.,;:\\s@\"]+)*)|(\".+\"))@" +
            "((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$",
    )
    private val testEmailRegex = Regex("^(test@)|(@test\\..*)", RegexOption.IGNORE_CASE)
    private val testNameRegex = Regex("(^|\\s)test(\\s|$)", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("^\\+?([0-9]{2})\\)?[-. ]?([0-9]{4})[-. ]?([0-9]{4})$")
    private val phoneRegexInternational = Regex("^\\+?([0-9]{2})\\)?[- ]?([0-9]{4})[- ]?([0-9]{4})[- ]?([0-9]{0,3})$")
    private val alphaOnlyRegex = Regex("^[a-zA-Z\\s]+$")

    fun validateEmail(email: String): Boolean = emailRegex.matches(email.lowercase())

    /** Blocks the literal word "test" as an email local-part/domain - a widget-specific guard, not a real format rule. */
    fun validateTest(value: String): Boolean = testEmailRegex.containsMatchIn(value.lowercase())

    /** Blocks the standalone word "test" anywhere in a name field. */
    fun validateTestName(value: String): Boolean = testNameRegex.containsMatchIn(value.lowercase())

    /** Rejects phone numbers made of a single repeated digit (e.g. "1111111111") before format-checking. */
    fun validatePhoneNumber(phone: String, international: Boolean): Boolean {
        val containsSameCharacters = phone.isNotEmpty() && phone.all { it == phone[0] }
        if (containsSameCharacters) return false
        val regex = if (international) phoneRegexInternational else phoneRegex
        return regex.matches(phone.lowercase())
    }

    fun hasOnlyCharacter(value: String): Boolean = alphaOnlyRegex.matches(value)

    /** HTML-entity-escapes a value before it goes out over the wire (ports sanitizeInput()). */
    fun sanitizeInput(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("/", "&#x2F;")
}
