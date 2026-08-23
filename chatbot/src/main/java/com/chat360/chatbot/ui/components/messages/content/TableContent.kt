package com.chat360.chatbot.ui.components.messages.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chat360.chatbot.model.richtext.HtmlTableParser
import com.chat360.chatbot.model.richtext.RichText
import com.chat360.chatbot.ui.text.toAnnotatedString
import com.chat360.chatbot.ui.theme.Chat360Colors
import com.chat360.chatbot.ui.theme.Chat360Typography
import com.chat360.chatbot.ui.theme.LocalChat360Colors
import com.chat360.chatbot.ui.theme.LocalChat360Typography

private val MaxCellWidth = 220.dp

@Composable
fun TableContent(table: HtmlTableParser.Table) {
    val colors = LocalChat360Colors.current
    val typography = LocalChat360Typography.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        TableGrid(table, colors, typography)
    }
}

@Composable
private fun TableGrid(table: HtmlTableParser.Table, colors: Chat360Colors, typography: Chat360Typography) {
    val rows = table.rows
    val columnCount = rows.maxOf { it.size }
    val density = LocalDensity.current
    val maxCellWidthPx = with(density) { MaxCellWidth.roundToPx() }

    SubcomposeLayout { _ ->
        val looseConstraints = Constraints(maxWidth = maxCellWidthPx)
        val colWidths = IntArray(columnCount)
        for (r in rows.indices) {
            for (c in 0 until columnCount) {
                val placeable = subcompose(TableSlot(r, c, TablePass.WIDTH)) {
                    TableCell(rows[r].getOrNull(c), r < table.headerRowCount, colors, typography)
                }.first().measure(looseConstraints)
                colWidths[c] = maxOf(colWidths[c], placeable.width)
            }
        }

        val rowHeights = IntArray(rows.size)
        for (r in rows.indices) {
            for (c in 0 until columnCount) {
                val exactWidth = Constraints(minWidth = colWidths[c], maxWidth = colWidths[c])
                val placeable = subcompose(TableSlot(r, c, TablePass.HEIGHT)) {
                    TableCell(rows[r].getOrNull(c), r < table.headerRowCount, colors, typography)
                }.first().measure(exactWidth)
                rowHeights[r] = maxOf(rowHeights[r], placeable.height)
            }
        }

        val totalWidth = colWidths.sum()
        val totalHeight = rowHeights.sum()
        layout(totalWidth, totalHeight) {
            var y = 0
            for (r in rows.indices) {
                var x = 0
                for (c in 0 until columnCount) {
                    val cellConstraints = Constraints.fixed(colWidths[c], rowHeights[r])
                    val placeable = subcompose(TableSlot(r, c, TablePass.PLACE)) {
                        TableCell(rows[r].getOrNull(c), r < table.headerRowCount, colors, typography)
                    }.first().measure(cellConstraints)
                    placeable.placeRelative(x, y)
                    x += colWidths[c]
                }
                y += rowHeights[r]
            }
        }
    }
}

private enum class TablePass { WIDTH, HEIGHT, PLACE }
private data class TableSlot(val row: Int, val col: Int, val pass: TablePass)

@Composable
private fun TableCell(cell: RichText?, isHeader: Boolean, colors: Chat360Colors, typography: Chat360Typography) {
    Box(
        modifier = Modifier
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
