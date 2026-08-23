package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.richtext.HtmlTableParser
import com.chat360.chatbot.model.richtext.RichText
import com.chat360.chatbot.ui.text.toAnnotatedString
import com.chat360.chatbot.ui.theme.Chat360Colors
import com.chat360.chatbot.ui.theme.Chat360Typography
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

/** Every cell in a column shares this width, so a short "Price" column stays narrow while a long
 * "Description" column gets room - keeps the grid looking like an actual table instead of N equal
 * slabs, without needing a multi-pass measure of the real (Compose) layout. */
private val MinCellWidth = 96.dp
private val MaxCellWidth = 220.dp
private val MaxFirstColumnWidth = 160.dp
private const val CharWidthDp = 8

/** Renders a [HtmlTableParser]-parsed `<table>` as a bordered grid with aligned columns and a
 * shaded header row, horizontally scrollable once its natural width exceeds the bubble. */
@Composable
fun TableContent(table: HtmlTableParser.Table) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    val columnCount = table.rows.maxOf { it.size }
    val columnWidths = columnWidths(table, columnCount)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        Column {
            table.rows.forEachIndexed { r, row ->
                // IntrinsicSize.Min sizes the row to its tallest wrapped cell, and each cell's
                // fillMaxHeight() below then stretches to match - otherwise a short cell's
                // border/background would stop above the row's actual bottom edge.
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    for (c in 0 until columnCount) {
                        TableCell(
                            cell = row.getOrNull(c),
                            width = columnWidths[c],
                            isHeader = r < table.headerRowCount,
                            colors = colors,
                            typography = typography,
                        )
                    }
                }
            }
        }
    }
}

/** One fixed width per column, from that column's longest cell (by character count, a cheap
 * stand-in for measured text width), clamped to a sane range. */
private fun columnWidths(table: HtmlTableParser.Table, columnCount: Int): List<Dp> =
    (0 until columnCount).map { c ->
        val maxChars = table.rows.maxOf { row -> row.getOrNull(c)?.plainLength() ?: 0 }
        val cap = if (c == 0) MaxFirstColumnWidth else MaxCellWidth
        (maxChars * CharWidthDp).dp.coerceIn(MinCellWidth, cap)
    }

private fun RichText.plainLength(): Int = runs.filterIsInstance<RichText.TextRun>().sumOf { it.text.length }

@Composable
private fun TableCell(cell: RichText?, width: Dp, isHeader: Boolean, colors: Chat360Colors, typography: Chat360Typography) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (isHeader) colors.backgroundSunken else colors.cardBackground)
            .border(0.5.dp, colors.cardBorder)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = (cell ?: RichText(emptyList())).toAnnotatedString(colors.accent),
            fontFamily = typography.textFamily,
            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.bubbleAiText,
        )
    }
}
