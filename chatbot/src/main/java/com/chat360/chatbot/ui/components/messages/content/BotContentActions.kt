package com.chat360.chatbot.ui.components.messages.content

import com.chat360.chatbot.model.wire.BotContent

/**
 * All user-interaction callbacks a bot content renderer might need, bundled so adding a new
 * content type's interaction means adding one field here with a default - never touching every
 * call site's parameter list.
 */
data class BotContentActions(
    val onQuickReply: (BotContent.MultiChoice.Option) -> Unit = {},
    val onAttachmentClick: () -> Unit = {},
    val onCameraClick: () -> Unit = {},
    val onRatingSelected: (Int) -> Unit = {},
    val onFormFieldChange: (fieldIndex: Int, value: String) -> Unit = { _, _ -> },
    val onMediaFieldPicked: (fieldIndex: Int, bytes: ByteArray, fileName: String, mimeType: String) -> Unit = { _, _, _, _ -> },
    val onFormSubmit: () -> Unit = {},
    /** Shared by EmailPrompt (value only) and PhonePrompt (country code + national number). */
    val onPromptValueChange: (primary: String, secondary: String) -> Unit = { _, _ -> },
    val onEmailSubmit: () -> Unit = {},
    val onPhoneSubmit: () -> Unit = {},
    val onAutoSuggestionSelected: (index: Int, text: String) -> Unit = { _, _ -> },
    val onDateSelected: (formattedDate: String) -> Unit = {},
    val onTimeSubmit: (formattedTime: String) -> Unit = {},
    val onCheckboxToggle: (index: Int) -> Unit = {},
    val onCheckboxSubmit: () -> Unit = {},
    val onImageButtonClick: (BotContent.ImageButtons.Card, BotContent.ImageButtons.Button) -> Unit = { _, _ -> },
    val onTextCarouselTap: (text: String, clickedIndex: Int, targetId: String?) -> Unit = { _, _, _ -> },
    val onWelcomeCardSelected: (BotContent.WelcomeScreen.Card, Int) -> Unit = { _, _ -> },
    val onIframeAdvance: (targetId: String) -> Unit = {},
)
