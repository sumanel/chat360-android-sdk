package com.chat360.chatbot.model.richtext

/**
 * A parsed, structural representation of the small "chat-safe" HTML subset bot content actually
 * sends (bold/italic/underline/strikethrough, links, line breaks, lists). Deliberately kept free
 * of any Compose/Android type so [RichTextParser] is plain-Kotlin unit-testable; a thin adapter
 * in the ui layer turns this into an `AnnotatedString` for rendering.
 */
data class RichText(val runs: List<Run>) {
    sealed interface Run

    data class TextRun(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val linkUrl: String? = null,
    ) : Run

    /** A paragraph/list-item/`<br>` boundary - rendered as a newline. */
    data object LineBreak : Run
}
