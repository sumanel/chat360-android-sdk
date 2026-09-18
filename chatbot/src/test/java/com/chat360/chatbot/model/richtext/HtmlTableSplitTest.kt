package com.chat360.chatbot.model.richtext

import com.chat360.chatbot.model.wire.IncomingSocketEvent
import com.chat360.chatbot.model.wire.RawSocketEnvelope
import com.chat360.chatbot.model.wire.toIncomingEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for "the paragraph after the table doesn't render": a knowledge-base reply is a
 * `<table>` followed by a summary `<p>`. The table branch used to keep only the cells and throw away
 * everything after `</table>` (and only recognised a message that began with `<p><table`), so the
 * summary vanished. The message below is the real one that was reported.
 */
class HtmlTableSplitTest {

    private val summary = "The Hyundai Venue stands out with its diesel engine option delivering high torque and advanced " +
        "drive modes including Sand, Mud, and Snow traction control, making it a strong, well-rounded choice in this segment."

    /** The reply exactly as reported (newlines between tags, indented cells, trailing summary paragraph). */
    private val venueVsBrezza = """
<table border="1" style="border-collapse:collapse; width:100%;">
<thead>
<tr>
    <th>Parameter</th>
    <th>Hyundai Venue</th>
    <th>Maruti Suzuki Brezza</th>
</tr>
</thead>
<tbody>
<tr>
    <td><strong>Fuel Type Options</strong></td>
    <td>Petrol and Diesel</td>
    <td>Petrol and CNG (bi-fuel)</td>
</tr>
<tr>
    <td><strong>Maximum Power</strong></td>
    <td>116 PS (Diesel), 120 PS (Petrol Turbo GDi)</td>
    <td>110 PS (Petrol)</td>
</tr>
<tr>
    <td><strong>Maximum Torque</strong></td>
    <td>250 Nm (Diesel), Not specified for Petrol</td>
    <td>130 Nm (Petrol)</td>
</tr>
<tr>
    <td><strong>Transmission Options</strong></td>
    <td>7-speed DCT (Petrol Turbo), Manual and Automatic options</td>
    <td>Manual and Automatic options</td>
</tr>
<tr>
    <td><strong>Drive Modes and Traction Control</strong></td>
    <td>Drive Mode Select and Sand, Mud, and Snow traction modes</td>
    <td>Not available</td>
</tr>
</tbody>
</table>
<p>$summary</p>
""".trimStart('\n').trimEnd('\n')

    private fun RichText.plain() = runs.joinToString("") { (it as? RichText.TextRun)?.text ?: "\n" }

    private fun assertVenueTableAndSummary(split: HtmlTableParser.Split?) {
        assertNotNull("the message was not recognised as a table", split)
        split!!
        assertEquals("header row + 5 data rows", 6, split.table.rows.size)
        assertEquals(1, split.table.headerRowCount)
        assertEquals(listOf("Parameter", "Hyundai Venue", "Maruti Suzuki Brezza"), split.table.rows[0].map { it.plain() })
        assertEquals(
            listOf("Fuel Type Options", "Petrol and Diesel", "Petrol and CNG (bi-fuel)"),
            split.table.rows[1].map { it.plain() },
        )
        assertEquals(summary, RichTextParser.parse(split.after).plain().trim())
    }

    @Test
    fun `the paragraph after the table is kept`() {
        assertVenueTableAndSummary(HtmlTableParser.split(venueVsBrezza))
    }

    @Test
    fun `a message that does not begin with p-table is still recognised as a table`() {
        assertTrue(!venueVsBrezza.trimStart().startsWith("<p><table", ignoreCase = true))
        assertNotNull(HtmlTableParser.split(venueVsBrezza))
    }

    @Test
    fun `the bot flow's wrapping p tag around the whole reply does not lose the paragraph`() {
        val wrapped = "<p>$venueVsBrezza</p>"
        val split = HtmlTableParser.split(wrapped)

        assertVenueTableAndSummary(split)
        assertTrue("nothing visible should precede the table", RichTextParser.parse(split!!.before).runs.isEmpty())
    }

    @Test
    fun `literal backslash-n escapes and three quick replies survive the wire parser and still yield the paragraph`() {
        // Exactly what the socket delivers: newlines arrive as the two characters backslash + n.
        val onTheWire = venueVsBrezza.replace("\n", "\\n")
        val data = buildJsonObject {
            put("nodeType", "MULTI_CHOICE")
            put("questionText", onTheWire)
            put("buttons", JsonArray(listOf("Venue vs Nexon", "Venue vs Sonet", "Venue vs Creta").mapIndexed { i, label ->
                buildJsonObject { put("text", label); put("targetId", "t$i") }
            }))
        }

        val event = RawSocketEnvelope(user = "bot", data = data).toIncomingEvent()

        assertTrue("expected a bot message, got $event", event is IncomingSocketEvent.BotMessage)
        val node = (event as IncomingSocketEvent.BotMessage).node
        assertVenueTableAndSummary(HtmlTableParser.split(node.text.orEmpty()))
        val options = (node.content as com.chat360.chatbot.model.wire.BotContent.MultiChoice).options
        assertEquals(listOf("Venue vs Nexon", "Venue vs Sonet", "Venue vs Creta"), options.map { it.text })
    }

    @Test
    fun `text before and after the table is kept along with its formatting and links`() {
        val html = """<p>Here is <b>the</b> comparison:</p><table><tr><th>A</th></tr><tr><td>1</td></tr></table><p>See <a href="https://example.com/spec">the spec</a> for <em>more</em>.</p>"""

        val split = HtmlTableParser.split(html)!!

        val before = RichTextParser.parse(split.before)
        assertEquals("Here is the comparison:", before.plain())
        assertTrue("bold was lost", before.runs.filterIsInstance<RichText.TextRun>().any { it.bold && it.text == "the" })
        val after = RichTextParser.parse(split.after)
        assertEquals("See the spec for more.", after.plain())
        assertTrue("link was lost", after.runs.filterIsInstance<RichText.TextRun>().any { it.linkUrl == "https://example.com/spec" })
        assertTrue("italic was lost", after.runs.filterIsInstance<RichText.TextRun>().any { it.italic && it.text == "more" })
    }

    @Test
    fun `a table with nothing after it leaves no trailing text`() {
        val split = HtmlTableParser.split("<table><tr><th>A</th></tr><tr><td>1</td></tr></table>")!!
        assertEquals("", split.after)
        assertEquals("", split.before)
    }

    @Test
    fun `upper-case tags are recognised too`() {
        val split = HtmlTableParser.split("<TABLE><TR><TH>A</TH></TR><TR><TD>1</TD></TR></TABLE><P>after</P>")

        assertNotNull("an upper-case table was not recognised", split)
        assertEquals(1, split!!.table.headerRowCount)
        assertEquals("after", RichTextParser.parse(split.after).plain().trim())
    }

    @Test
    fun `text with no table is not split`() {
        assertNull(HtmlTableParser.split("<p>Just a normal reply with <b>bold</b>.</p>"))
    }

    @Test
    fun `a table element with no rows falls back instead of swallowing the message`() {
        assertNull(HtmlTableParser.split("<p>Before</p><table></table><p>After</p>"))
    }

    @Test
    fun `only the first table is structured and the rest stays as text after it`() {
        val split = HtmlTableParser.split("<table><tr><td>one</td></tr></table><p>middle</p><table><tr><td>two</td></tr></table>")!!
        assertEquals(1, split.table.rows.size)
        assertTrue(split.after.contains("middle"))
    }
}
