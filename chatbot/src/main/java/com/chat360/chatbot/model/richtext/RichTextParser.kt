package com.chat360.chatbot.model.richtext

/**
 * A minimal, permissive single-pass parser for the small HTML subset bot content actually sends
 * (bold/italic/underline/strikethrough, `<a href>` links, `<br>`/`<p>` breaks, `<ul>`/`<ol>`/`<li>`
 * lists). It tracks a real stack of open tags so nesting (`<strong><em>...</em></strong>`) and
 * out-of-order closes (`<b><i>x</b></i>`) resolve the way a lenient HTML parser would, rather than
 * matching tags with a flat regex.
 *
 * Deliberately tolerant of malformed input, since it also has to run mid-stream on a
 * chatgpt_message bubble that hasn't finished arriving yet: an unmatched closing tag is ignored,
 * and a tag whose closing ">" hasn't arrived yet (a chunk boundary can land inside a tag) is kept
 * as literal text instead of throwing - the next chunk completes it and it renders correctly then.
 */
object RichTextParser {

    private val boldTags = setOf("b", "strong")
    private val italicTags = setOf("i", "em")
    private val underlineTags = setOf("u")
    private val strikeTags = setOf("s", "strike", "del")
    private val lineBreakTags = setOf("br", "hr")
    private val paragraphTags = setOf("p", "div", "li")
    private val hrefAttr = Regex("""href\s*=\s*"([^"]*)"""")
    private val hrefAttrSingleQuoted = Regex("""href\s*=\s*'([^']*)'""")
    private val decimalEntity = Regex("""^&#(\d+);$""")
    private val hexEntity = Regex("""^&#x([0-9a-fA-F]+);$""")

    fun parse(html: String): RichText {
        val runs = mutableListOf<RichText.Run>()
        val openTags = mutableListOf<String>()
        val listCounters = mutableListOf<Int>() // -1 for a <ul>, >=0 running count for an <ol>
        var linkUrl: String? = null
        val pendingText = StringBuilder()

        fun flushText() {
            if (pendingText.isEmpty()) return
            runs += RichText.TextRun(
                text = pendingText.toString(),
                bold = openTags.any { it in boldTags },
                italic = openTags.any { it in italicTags },
                underline = openTags.any { it in underlineTags },
                strikethrough = openTags.any { it in strikeTags },
                linkUrl = linkUrl,
            )
            pendingText.clear()
        }

        var i = 0
        while (i < html.length) {
            val c = html[i]
            if (c == '&') {
                val decoded = decodeEntity(html, i)
                if (decoded != null) {
                    pendingText.append(decoded.first)
                    i += decoded.second
                    continue
                }
            }
            if (c != '<') {
                pendingText.append(c)
                i++
                continue
            }

            val tagEnd = html.indexOf('>', i)
            if (tagEnd == -1) {
                // Incomplete tag - most likely a chunk boundary mid-stream. Keep it literal for
                // now; once the closing chunk arrives this same call site re-parses the whole
                // merged string and resolves it properly.
                pendingText.append(html.substring(i))
                break
            }

            val rawTag = html.substring(i + 1, tagEnd)
            i = tagEnd + 1
            val isClosing = rawTag.startsWith("/")
            val selfClosing = rawTag.endsWith("/")
            val body = rawTag.removePrefix("/").removeSuffix("/").trim()
            val nameEnd = body.indexOfFirst { it.isWhitespace() }.let { if (it == -1) body.length else it }
            val tagName = body.substring(0, nameEnd).lowercase()
            if (tagName.isEmpty()) continue // stray "<>" / "</>"

            when {
                tagName in lineBreakTags -> {
                    flushText()
                    runs += RichText.LineBreak
                }
                isClosing -> {
                    flushText()
                    val openIndex = openTags.lastIndexOf(tagName)
                    if (openIndex >= 0) {
                        while (openTags.size > openIndex) openTags.removeAt(openTags.lastIndex)
                    }
                    if (tagName == "a") linkUrl = null
                    if (tagName == "ul" || tagName == "ol") {
                        if (listCounters.isNotEmpty()) listCounters.removeAt(listCounters.lastIndex)
                    }
                    if (tagName in paragraphTags && runs.isNotEmpty()) runs += RichText.LineBreak
                }
                tagName == "a" -> {
                    flushText()
                    linkUrl = hrefAttr.find(body)?.groupValues?.get(1)
                        ?: hrefAttrSingleQuoted.find(body)?.groupValues?.get(1)
                    if (selfClosing) linkUrl = null else openTags += tagName
                }
                tagName == "ul" -> if (!selfClosing) listCounters += -1
                tagName == "ol" -> if (!selfClosing) listCounters += 0
                tagName == "li" -> {
                    flushText()
                    if (runs.isNotEmpty()) runs += RichText.LineBreak
                    val marker = if (listCounters.isNotEmpty() && listCounters.last() >= 0) {
                        listCounters[listCounters.lastIndex] += 1
                        "${listCounters.last()}. "
                    } else {
                        "• "
                    }
                    runs += RichText.TextRun(marker)
                    if (!selfClosing) openTags += tagName
                }
                tagName in paragraphTags -> {
                    if (runs.isNotEmpty() || pendingText.isNotEmpty()) {
                        flushText()
                        runs += RichText.LineBreak
                    }
                    if (!selfClosing) openTags += tagName
                }
                else -> {
                    // A style tag (strong/em/u/...) opening mid-run: flush whatever text came
                    // before it under the *old* style first, so only text after this point picks
                    // up the new tag's style.
                    flushText()
                    if (!selfClosing) openTags += tagName
                }
            }
        }
        flushText()
        while (runs.isNotEmpty() && runs.last() is RichText.LineBreak) runs.removeAt(runs.lastIndex)
        while (runs.isNotEmpty() && runs.first() is RichText.LineBreak) runs.removeAt(0)
        return RichText(runs)
    }

    /** Returns the decoded character(s) and how many source chars they consumed, or null if [at] isn't a recognized entity. */
    private fun decodeEntity(html: String, at: Int): Pair<String, Int>? {
        val end = html.indexOf(';', at)
        if (end == -1 || end - at > 10) return null
        val entity = html.substring(at, end + 1)
        val decoded = when (entity) {
            "&amp;" -> "&"
            "&lt;" -> "<"
            "&gt;" -> ">"
            "&quot;" -> "\""
            "&apos;" -> "'"
            "&nbsp;" -> " "
            else -> {
                val codePoint = decimalEntity.find(entity)?.groupValues?.get(1)?.toIntOrNull()
                    ?: hexEntity.find(entity)?.groupValues?.get(1)?.toIntOrNull(16)
                codePoint?.let { String(Character.toChars(it)) }
            }
        } ?: return null
        return decoded to (end - at + 1)
    }
}
