package com.chat360.chatbot.domain.validation

import com.chat360.chatbot.model.wire.BotContent

/**
 * Validates a form field value: per-field-type checks first (each with its own message), then
 * the generic required check, then a single OR-chain whose failures all share one generic
 * message. Order is deliberate, including the quirk that the EMAIL/TEXT "Test"-word and
 * alpha-only checks run *before* the required check, so they can fire on values that also
 * happen to be blank.
 */
object FormFieldValidator {

    private val blockedPhoneNumbers: Set<String> = (0..9).map { it.toString().repeat(10) }.toSet() +
        setOf("1122334455", "1231231231", "2345678901", "3456789012", "4567890123", "5678901234", "0101010101")

    /** Returns an error message, or null if [value] passes every rule for this field. */
    fun validate(field: BotContent.Form.Field, value: String): String? {
        val v = field.validation ?: BotContent.Form.FieldValidation()

        // MEDIA fields skip every format/length check in this matrix (they don't apply to a
        // file), but a required upload still can't be left blank; [value] here is the uploaded
        // URL, set once the picker finishes.
        if (field.type == BotContent.Form.FieldType.MEDIA) {
            return if (v.isRequired && value.isBlank()) v.errorMessage ?: "This field is required" else null
        }

        if (field.type == BotContent.Form.FieldType.EMAIL && InputValidators.validateTest(value)) {
            return "Email cannot contain the word 'Test'"
        }
        if (field.type == BotContent.Form.FieldType.PHONE && value in blockedPhoneNumbers) {
            return "This phone number is blocked"
        }
        if (field.type == BotContent.Form.FieldType.TEXT && InputValidators.validateTestName(value.lowercase())) {
            return "Name cannot contain the word 'Test'"
        }
        if (field.type == BotContent.Form.FieldType.TEXT && v.userInputType == "alpha" && !InputValidators.hasOnlyCharacter(value)) {
            return "Only characters and space are allowed'"
        }

        if (v.isRequired && value.isBlank()) return v.errorMessage ?: "This field is required"
        if (!v.isRequired && value.isBlank()) return null

        val numeric = value.toDoubleOrNull()
        val dateRules = v.dateRules
        val parsedDate = if (field.type == BotContent.Form.FieldType.DATE && dateRules != null) {
            DateAvailability.parse(value, dateRules.dateFormat)
        } else {
            null
        }
        val invalid = (v.maxCharacters != null && value.length > v.maxCharacters) ||
            (v.minCharacters != null && value.length < v.minCharacters) ||
            (v.email && !InputValidators.validateEmail(value)) ||
            // A non-numeric value does NOT fail these two checks on its own - the comparison
            // only applies once the value successfully parses as a number.
            (v.maxCount != null && numeric != null && numeric > v.maxCount) ||
            (v.minCount != null && numeric != null && numeric < v.minCount) ||
            (v.phone && !InputValidators.validatePhoneNumber(value, v.allowInternationalNumber)) ||
            (parsedDate != null && dateRules != null && dateRules.isScheduledDate && DateAvailability.isDisabled(dateRules, parsedDate)) ||
            (field.type == BotContent.Form.FieldType.NUMBER && numeric == null) ||
            (v.numberFormat == "DECIMAL" && !value.contains('.')) ||
            (v.numberFormat == "NUMBER" && value.contains('.'))

        return if (invalid) v.errorMessage ?: "Invalid input" else null
    }
}
