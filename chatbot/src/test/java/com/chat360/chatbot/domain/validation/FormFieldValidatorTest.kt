package com.chat360.chatbot.domain.validation

import com.chat360.chatbot.model.wire.BotContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormFieldValidatorTest {

    private fun field(
        type: BotContent.Form.FieldType,
        validation: BotContent.Form.FieldValidation? = null,
    ) = BotContent.Form.Field(
        index = 0,
        type = type,
        label = "Field",
        placeholder = null,
        isRequired = validation?.isRequired ?: false,
        options = emptyList(),
        variable = "v",
        validation = validation,
    )

    @Test
    fun `required field blank fails with default message`() {
        val f = field(BotContent.Form.FieldType.TEXT, BotContent.Form.FieldValidation(isRequired = true))
        assertEquals("This field is required", FormFieldValidator.validate(f, ""))
    }

    @Test
    fun `required field blank uses custom errorMessage when present`() {
        val f = field(
            BotContent.Form.FieldType.TEXT,
            BotContent.Form.FieldValidation(isRequired = true, errorMessage = "Please tell us your name"),
        )
        assertEquals("Please tell us your name", FormFieldValidator.validate(f, ""))
    }

    @Test
    fun `optional blank field always passes`() {
        val f = field(BotContent.Form.FieldType.EMAIL, BotContent.Form.FieldValidation(email = true))
        assertNull(FormFieldValidator.validate(f, ""))
    }

    @Test
    fun `EMAIL field blocks the word test before anything else`() {
        val f = field(BotContent.Form.FieldType.EMAIL, BotContent.Form.FieldValidation(isRequired = true, email = true))
        assertEquals("Email cannot contain the word 'Test'", FormFieldValidator.validate(f, "test@example.com"))
    }

    @Test
    fun `EMAIL field with valid non-test address passes`() {
        val f = field(BotContent.Form.FieldType.EMAIL, BotContent.Form.FieldValidation(isRequired = true, email = true))
        assertNull(FormFieldValidator.validate(f, "user@example.com"))
    }

    @Test
    fun `PHONE field blocks the fixed blocklist`() {
        val f = field(BotContent.Form.FieldType.PHONE, BotContent.Form.FieldValidation(isRequired = true, phone = true))
        assertEquals("This phone number is blocked", FormFieldValidator.validate(f, "1231231231"))
    }

    @Test
    fun `PHONE field with a real number passes`() {
        val f = field(BotContent.Form.FieldType.PHONE, BotContent.Form.FieldValidation(isRequired = true, phone = true))
        assertNull(FormFieldValidator.validate(f, "9876543210"))
    }

    @Test
    fun `TEXT field blocks the word test as a name`() {
        val f = field(BotContent.Form.FieldType.TEXT, BotContent.Form.FieldValidation(isRequired = true))
        assertEquals("Name cannot contain the word 'Test'", FormFieldValidator.validate(f, "John test Doe"))
    }

    @Test
    fun `TEXT field with alpha userInputType rejects digits`() {
        val f = field(
            BotContent.Form.FieldType.TEXT,
            BotContent.Form.FieldValidation(isRequired = true, userInputType = "alpha"),
        )
        assertEquals("Only characters and space are allowed'", FormFieldValidator.validate(f, "John123"))
    }

    @Test
    fun `maxCharacters and minCharacters bound TEXT length`() {
        val f = field(
            BotContent.Form.FieldType.TEXT,
            BotContent.Form.FieldValidation(isRequired = true, maxCharacters = 5, minCharacters = 2),
        )
        assertEquals("Invalid input", FormFieldValidator.validate(f, "toolongvalue"))
        assertEquals("Invalid input", FormFieldValidator.validate(f, "a"))
        assertNull(FormFieldValidator.validate(f, "abc"))
    }

    @Test
    fun `NUMBER field rejects non-numeric values`() {
        val f = field(BotContent.Form.FieldType.NUMBER, BotContent.Form.FieldValidation(isRequired = true))
        assertEquals("Invalid input", FormFieldValidator.validate(f, "abc"))
        assertNull(FormFieldValidator.validate(f, "42"))
    }

    @Test
    fun `maxCount and minCount ignore non-numeric values rather than failing on them`() {
        // Ports JS's `+value > maxCount` being false for NaN - a non-numeric value must not trip
        // maxCount/minCount on its own (it still fails separately via the NUMBER type-check).
        val f = field(BotContent.Form.FieldType.TEXT, BotContent.Form.FieldValidation(isRequired = true, maxCount = 10.0))
        assertNull(FormFieldValidator.validate(f, "not-a-number"))
    }

    @Test
    fun `maxCount and minCount bound numeric values`() {
        val f = field(
            BotContent.Form.FieldType.NUMBER,
            BotContent.Form.FieldValidation(isRequired = true, maxCount = 100.0, minCount = 10.0),
        )
        assertEquals("Invalid input", FormFieldValidator.validate(f, "5"))
        assertEquals("Invalid input", FormFieldValidator.validate(f, "500"))
        assertNull(FormFieldValidator.validate(f, "50"))
    }

    @Test
    fun `MEDIA field skips format checks but still enforces required`() {
        val required = field(BotContent.Form.FieldType.MEDIA, BotContent.Form.FieldValidation(isRequired = true))
        assertEquals("This field is required", FormFieldValidator.validate(required, ""))
        assertNull(FormFieldValidator.validate(required, "https://example.com/uploaded.png"))

        val optional = field(BotContent.Form.FieldType.MEDIA)
        assertNull(FormFieldValidator.validate(optional, ""))
    }

    @Test
    fun `numberFormat DECIMAL requires a decimal point, NUMBER forbids one`() {
        val decimalField = field(BotContent.Form.FieldType.NUMBER, BotContent.Form.FieldValidation(numberFormat = "DECIMAL"))
        assertEquals("Invalid input", FormFieldValidator.validate(decimalField, "42"))
        assertNull(FormFieldValidator.validate(decimalField, "42.0"))

        val numberField = field(BotContent.Form.FieldType.NUMBER, BotContent.Form.FieldValidation(numberFormat = "NUMBER"))
        assertEquals("Invalid input", FormFieldValidator.validate(numberField, "42.0"))
        assertNull(FormFieldValidator.validate(numberField, "42"))
    }
}
