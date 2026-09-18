package com.chat360.chatbot.model.richtext

object HtmlTableParser {
    private val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val cellRegex = Regex("<(t[hd])[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tableRegex = Regex("<table\\b[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    data class Table(val rows: List<List<RichText>>, val headerRowCount: Int)

    /** A message that contains a `<table>`: whatever surrounds it plus the table itself. [before] and
     * [after] are still raw HTML (a wrapping `<p>`, a closing paragraph, inline formatting, links...),
     * ready for [RichTextParser]; either can be empty. */
    data class Split(val before: String, val table: Table, val after: String)

    /**
     * Splits [html] around its first `<table>`, wherever it sits - the bot copy is typically a
     * table followed by a summary paragraph, often wrapped in a `<p>` by the flow. Only the first
     * table is structured; a second one stays in [after] as ordinary text. Null when there is no
     * table with at least one row, so the caller falls back to plain rich text.
     */
    fun split(html: String): Split? {
        val match = tableRegex.find(html) ?: return null
        val table = parse(match.groupValues[1]) ?: return null
        return Split(
            before = html.substring(0, match.range.first).trim(),
            table = table,
            after = html.substring(match.range.last + 1).trim(),
        )
    }

    fun parse(html: String): Table? {
        val parsedRows = rowRegex.findAll(html).map { rowMatch ->
            cellRegex.findAll(rowMatch.groupValues[1]).map { cellMatch ->
                cellMatch.groupValues[1].lowercase() to RichTextParser.parse(cellMatch.groupValues[2].trim())
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
        if (parsedRows.isEmpty()) return null

        val headerRowCount = parsedRows.takeWhile { row -> row.all { it.first == "th" } }.size
        return Table(rows = parsedRows.map { row -> row.map { it.second } }, headerRowCount = headerRowCount)
    }
}
