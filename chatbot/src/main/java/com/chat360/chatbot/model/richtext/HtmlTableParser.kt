package com.chat360.chatbot.model.richtext

object HtmlTableParser {
    private val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val cellRegex = Regex("<(t[hd])[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)

    data class Table(val rows: List<List<RichText>>, val headerRowCount: Int)

    fun parse(html: String): Table? {
        val parsedRows = rowRegex.findAll(html).map { rowMatch ->
            cellRegex.findAll(rowMatch.groupValues[1]).map { cellMatch ->
                cellMatch.groupValues[1] to RichTextParser.parse(cellMatch.groupValues[2].trim())
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
        if (parsedRows.isEmpty()) return null

        val headerRowCount = parsedRows.takeWhile { row -> row.all { it.first == "th" } }.size
        return Table(rows = parsedRows.map { row -> row.map { it.second } }, headerRowCount = headerRowCount)
    }
}
