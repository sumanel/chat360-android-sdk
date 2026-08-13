package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.runtime.Composable
import com.chat360.chatbot.model.richtext.toPlainText
import com.chat360.chatbot.model.wire.BotContent
import com.chat360.chatbot.ui.ChatMessage

/**
 * Dispatches a bot message's [BotContent] to its renderer. Adding a new content type is: a new
 * `BotContent` subtype (model/wire), a new composable in this package, and one branch here -
 * never a change to [ChatMessage] or the row chrome around this.
 */
@Composable
fun BotContentBody(message: ChatMessage, actions: BotContentActions, isLiveChat: Boolean = false) {
    when (val content = message.content) {
        is BotContent.MultiChoice -> MultiChoiceContent(message, content, isLiveChat, actions.onQuickReply)
        is BotContent.Media -> MediaContent(message, content, isLiveChat, actions.onTextCarouselTap)
        is BotContent.Carousel -> CarouselContent(message.text, content)
        is BotContent.FileUploadPrompt -> FileUploadPromptContent(content, isLiveChat, actions.onAttachmentClick, actions.onCameraClick)
        is BotContent.DownloadMedia -> DownloadMediaContent(content)
        is BotContent.Rating -> RatingContent(message, content, isLiveChat, actions.onRatingSelected)
        is BotContent.Form -> FormContent(message, content, isLiveChat, actions.onFormFieldChange, actions.onMediaFieldPicked, actions.onFormSubmit)
        // Never actually reaches the message list - ChatViewModel skips WINDOW_EVENT nodes
        // entirely - kept only so this `when` stays exhaustive as BotContent grows.
        is BotContent.WindowEvent -> Unit
        BotContent.EmailPrompt -> EmailPromptContent(message, isLiveChat, actions.onPromptValueChange, actions.onEmailSubmit)
        // A non-international PhonePrompt has no dedicated renderer - the always-visible bottom
        // input bar already answers it as free text.
        is BotContent.PhonePrompt -> if (content.allowInternational) {
            PhonePromptContent(message, content, isLiveChat, actions.onPromptValueChange, actions.onPhoneSubmit)
        } else {
            PlainTextContent(message.text)
        }
        is BotContent.AutoSuggestion -> AutoSuggestionContent(message, content, isLiveChat, actions.onAutoSuggestionSelected)
        is BotContent.DatePrompt -> DatePromptContent(message, content, isLiveChat, actions.onDateSelected)
        is BotContent.TimePrompt -> TimePromptContent(message, content, isLiveChat, actions.onTimeSubmit)
        is BotContent.MultiOption -> MultiOptionContent(message, content, isLiveChat, actions.onCheckboxToggle, actions.onCheckboxSubmit)
        is BotContent.ImageButtons -> ImageButtonsContent(message, content, isLiveChat, actions.onImageButtonClick)
        is BotContent.TextCarousel -> TextCarouselContent(message, content, isLiveChat, actions.onTextCarouselTap)
        is BotContent.LinkCard -> LinkCardContent(message.text, content)
        BotContent.AgentTransferNotice -> AgentTransferNoticeContent(message.text)
        is BotContent.WelcomeScreen -> WelcomeScreenContent(message, content, isLiveChat, actions.onWelcomeCardSelected)
        is BotContent.IframeContent -> IframeContent(content, actions.onIframeAdvance)
        BotContent.PlainText -> PlainTextContent(message.text)
        is BotContent.Unsupported -> UnsupportedContent(message.text, content)
    }
}

/**
 * Full copy-to-clipboard text for a bot message. [ChatMessage.text] alone is only ever the
 * question/plain-text portion - a MultiChoice/MultiOption reply's option labels are separate
 * fields rendered *instead of* (MultiOption) or *alongside* (MultiChoice) it, so copy needs to
 * reassemble the same text a reader actually sees rather than just that one field.
 */
fun ChatMessage.copyText(): String = when (val content = content) {
    is BotContent.MultiChoice -> buildString {
        append(text.toPlainText())
        content.options.forEach { append('\n'); append(it.text) }
    }
    is BotContent.MultiOption -> buildString {
        content.description?.let { append(it) }
        content.options.forEach {
            if (isNotEmpty()) append('\n')
            append(it.text)
        }
    }
    else -> text.toPlainText()
}
