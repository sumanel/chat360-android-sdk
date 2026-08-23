package com.chat360.chatbot.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.chat360.chatbot.model.richtext.RichText
import com.chat360.chatbot.model.richtext.RichTextParser

fun String.toAnnotatedString(linkColor: Color): AnnotatedString =
    RichTextParser.parse(this).toAnnotatedString(linkColor)

fun RichText.toAnnotatedString(linkColor: Color): AnnotatedString = buildAnnotatedString {
    runs.forEach { run ->
        when (run) {
            is RichText.LineBreak -> append('\n')
            is RichText.TextRun -> appendTextRun(run, linkColor)
        }
    }
}

private fun AnnotatedString.Builder.appendTextRun(run: RichText.TextRun, linkColor: Color) {
    val decorations = buildList {
        if (run.underline) add(TextDecoration.Underline)
        if (run.strikethrough) add(TextDecoration.LineThrough)
    }
    val style = SpanStyle(
        fontWeight = if (run.bold) FontWeight.Bold else null,
        fontStyle = if (run.italic) FontStyle.Italic else null,
        textDecoration = if (decorations.isNotEmpty()) TextDecoration.combine(decorations) else null,
        color = if (run.linkUrl != null) linkColor else Color.Unspecified,
    )
    if (run.linkUrl != null) {
        withLink(LinkAnnotation.Url(run.linkUrl, TextLinkStyles(style = style))) {
            append(run.text)
        }
    } else {
        withStyle(style) { append(run.text) }
    }
}
