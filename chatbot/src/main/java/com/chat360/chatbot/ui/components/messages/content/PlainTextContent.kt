package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.richtext.HtmlTableParser
import com.chat360.chatbot.ui.text.toAnnotatedString
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

@Composable
fun PlainTextContent(text: String) {
    if (text.isEmpty()) return
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    if (text.trimStart().startsWith("<table", ignoreCase = true)) {
        val table = HtmlTableParser.parse(text)
        if (table != null) {
            TableContent(table)
            return
        }
    }
    Text(
        text = text.toAnnotatedString(linkColor = colors.accent),
        fontFamily = typography.textFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = colors.bubbleAiText,
    )
}
