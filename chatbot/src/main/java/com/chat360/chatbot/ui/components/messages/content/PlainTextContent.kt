package com.chat360.chatbot.ui.components.messages.content

import android.util.Log
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.richtext.HtmlTableParser
import com.chat360.chatbot.ui.text.toAnnotatedString
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** logcat filter: tag:Chat360Table */
private const val TABLE_LOG_TAG = "Chat360Table"

@Composable
fun PlainTextContent(text: String) {
    if (text.isEmpty()) return
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    if (text.trimStart().startsWith("<p><table", ignoreCase = true)) {
        Log.d(TABLE_LOG_TAG, "Raw table response (${text.length} chars): $text")
        val table = HtmlTableParser.parse(text)
        if (table != null) {
            val columnCount = table.rows.maxOf { it.size }
            Log.d(TABLE_LOG_TAG, "Parsed OK: ${table.rows.size} rows x $columnCount cols, headerRows=${table.headerRowCount}")
            TableContent(table)
            return
        }
        Log.w(TABLE_LOG_TAG, "Started with <table but parsing found no rows - falling back to plain text")
    }
    Text(
        text = text.toAnnotatedString(linkColor = colors.accent),
        fontFamily = typography.textFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = colors.bubbleAiText,
    )
}
