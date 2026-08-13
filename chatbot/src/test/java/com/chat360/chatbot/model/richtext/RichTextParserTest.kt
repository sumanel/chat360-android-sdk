package com.chat360.chatbot.model.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextParserTest {

    private fun RichText.text() = runs.filterIsInstance<RichText.TextRun>()

    @Test
    fun `plain text with no tags passes through as a single unstyled run`() {
        val parsed = RichTextParser.parse("Hello there")
        assertEquals(listOf(RichText.TextRun("Hello there")), parsed.runs)
    }

    @Test
    fun `bold and strong both produce a bold run`() {
        assertEquals(true, RichTextParser.parse("<b>x</b>").text().single().bold)
        assertEquals(true, RichTextParser.parse("<strong>x</strong>").text().single().bold)
    }

    @Test
    fun `italic underline and strikethrough tags set their flags`() {
        val italic = RichTextParser.parse("<em>x</em>").text().single()
        assertEquals(true, italic.italic)
        val underline = RichTextParser.parse("<u>x</u>").text().single()
        assertEquals(true, underline.underline)
        val strike = RichTextParser.parse("<del>x</del>").text().single()
        assertEquals(true, strike.strikethrough)
    }

    @Test
    fun `nested tags combine their styles`() {
        val run = RichTextParser.parse("<strong><em>bold italic</em></strong>").text().single()
        assertEquals(true, run.bold)
        assertEquals(true, run.italic)
        assertEquals("bold italic", run.text)
    }

    @Test
    fun `out-of-order closing tags still close both, matching lenient HTML parsing`() {
        // <b><i>x</b></i> - browsers close both b and i at the </b>.
        val parsed = RichTextParser.parse("<b><i>x</i></b>y")
        val runs = parsed.text()
        assertEquals("x", runs[0].text)
        assertEquals(true, runs[0].bold)
        assertEquals(true, runs[0].italic)
        assertEquals("y", runs[1].text)
        assertEquals(false, runs[1].bold)
        assertEquals(false, runs[1].italic)
    }

    @Test
    fun `unmatched closing tag is ignored rather than throwing`() {
        val parsed = RichTextParser.parse("hello</b>world")
        assertEquals("helloworld", parsed.text().joinToString("") { it.text })
    }

    @Test
    fun `br produces a line break`() {
        val parsed = RichTextParser.parse("line1<br>line2")
        assertEquals(
            listOf(RichText.TextRun("line1"), RichText.LineBreak, RichText.TextRun("line2")),
            parsed.runs,
        )
    }

    @Test
    fun `paragraphs become line breaks between them`() {
        val parsed = RichTextParser.parse("<p>one</p><p>two</p>")
        val texts = parsed.text().map { it.text }
        assertEquals(listOf("one", "two"), texts)
        assertTrue(parsed.runs.any { it is RichText.LineBreak })
    }

    @Test
    fun `unordered list items get bullet markers`() {
        val parsed = RichTextParser.parse("<ul><li>first</li><li>second</li></ul>")
        val texts = parsed.text().map { it.text }
        assertEquals(listOf("• ", "first", "• ", "second"), texts)
    }

    @Test
    fun `ordered list items get incrementing numeric markers`() {
        val parsed = RichTextParser.parse("<ol><li>first</li><li>second</li></ol>")
        val texts = parsed.text().map { it.text }
        assertEquals(listOf("1. ", "first", "2. ", "second"), texts)
    }

    @Test
    fun `anchor tag captures href as a link run`() {
        val run = RichTextParser.parse("""<a href="https://example.com">click me</a>""").text().single()
        assertEquals("click me", run.text)
        assertEquals("https://example.com", run.linkUrl)
    }

    @Test
    fun `common html entities are decoded`() {
        val text = RichTextParser.parse("Tom &amp; Jerry &lt;3 &quot;fun&quot;").text().single().text
        assertEquals("Tom & Jerry <3 \"fun\"", text)
    }

    @Test
    fun `numeric entities are decoded`() {
        assertEquals("A", RichTextParser.parse("&#65;").text().single().text)
        assertEquals("A", RichTextParser.parse("&#x41;").text().single().text)
    }

    @Test
    fun `an incomplete trailing tag is kept as literal text instead of throwing`() {
        // Exactly the streaming case: a chunk boundary lands mid-tag before the closing '>' arrives.
        val parsed = RichTextParser.parse("Key features.\n- <stro")
        assertEquals("Key features.\n- <stro", parsed.text().single().text)
    }

    @Test
    fun `a tag split across two chunks resolves correctly once the merged string is parsed`() {
        val merged = "Key features.\n- <stro" + "ng>Design</strong>: modern and sporty"
        val runs = RichTextParser.parse(merged).text()
        assertEquals("Key features.\n- ", runs[0].text)
        assertEquals(false, runs[0].bold)
        assertEquals("Design", runs[1].text)
        assertEquals(true, runs[1].bold)
        assertEquals(": modern and sporty", runs[2].text)
        assertEquals(false, runs[2].bold)
    }

    @Test
    fun `unknown tags are dropped but their inner text is kept`() {
        val parsed = RichTextParser.parse("<weirdtag>hello</weirdtag>")
        assertEquals("hello", parsed.text().joinToString("") { it.text })
    }
}
