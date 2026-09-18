package com.chat360.chatbot.ui.components.messages.content

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.richtext.HtmlTableParser
import com.chat360.chatbot.model.richtext.RichTextParser
import com.chat360.chatbot.ui.text.toAnnotatedString
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** logcat filter: tag:Chat360Table */
private const val TABLE_LOG_TAG = "Chat360Table"

@Composable
fun PlainTextContent(text: String) {
    if (text.isEmpty()) return
    // Anywhere in the message, not just at the very start: the copy is a table followed by a summary
    // paragraph, and the flow may or may not wrap it in a <p>. Everything around the table is kept.
    if (text.contains("<table", ignoreCase = true)) {
        Log.d(TABLE_LOG_TAG, "Raw table response (${text.length} chars): $text")
        val split = HtmlTableParser.split(text)
        if (split != null) {
            val columnCount = split.table.rows.maxOf { it.size }
            Log.d(TABLE_LOG_TAG, "Parsed OK: ${split.table.rows.size} rows x $columnCount cols, headerRows=${split.table.headerRowCount}, before=${split.before.length} chars, after=${split.after.length} chars")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RichTextBlock(split.before)
                TableContent(split.table)
                RichTextBlock(split.after)
            }
            return
        }
        Log.w(TABLE_LOG_TAG, "Contains <table but parsing found no rows - falling back to plain text")
    }
    RichTextBlock(text)
}

/** One run of bot-authored HTML rendered as styled text; renders nothing if it holds no visible text. */
@Composable
private fun RichTextBlock(html: String) {
    if (RichTextParser.parse(html).runs.isEmpty()) return
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Text(
        text = html.toAnnotatedString(linkColor = colors.accent),
        fontFamily = typography.textFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = colors.bubbleAiText,
    )
}
