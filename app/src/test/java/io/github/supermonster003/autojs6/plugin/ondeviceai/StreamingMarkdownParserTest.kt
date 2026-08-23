package io.github.supermonster003.autojs6.plugin.ondeviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMarkdownParserTest {
    @Test
    fun `renders complete and open emphasis without exposing markers`() {
        val complete = StreamingMarkdownParser.parse("A **bold** and *italic* answer")
        val streaming = StreamingMarkdownParser.parse("A **partial")

        assertEquals("A bold and italic answer", complete.text)
        assertSpan(complete, MarkdownSpanKind.BOLD, "bold")
        assertSpan(complete, MarkdownSpanKind.ITALIC, "italic")
        assertEquals("A partial", streaming.text)
        assertSpan(streaming, MarkdownSpanKind.BOLD, "partial")
    }

    @Test
    fun `renders headings lists quotes links and inline code`() {
        val document = StreamingMarkdownParser.parse(
            "# Heading\n- **item**\n> quote\nUse [value](https://example.com) and `code`",
        )

        assertEquals(
            "Heading\n- item\nquote\nUse value and code",
            document.text,
        )
        assertSpan(document, MarkdownSpanKind.HEADING_1, "Heading")
        assertSpan(document, MarkdownSpanKind.LIST_ITEM, "- item")
        assertSpan(document, MarkdownSpanKind.BOLD, "item")
        assertSpan(document, MarkdownSpanKind.BLOCK_QUOTE, "quote")
        assertSpan(document, MarkdownSpanKind.LINK, "value")
        assertSpan(document, MarkdownSpanKind.INLINE_CODE, "code")
    }

    @Test
    fun `an unclosed fenced block is rendered provisionally as code`() {
        val document = StreamingMarkdownParser.parse("Before\n```kotlin\nval answer = 42")

        assertEquals("Before\nval answer = 42", document.text)
        assertSpan(document, MarkdownSpanKind.CODE_BLOCK, "val answer = 42")
        assertEquals(
            "kotlin",
            document.spans.single { span -> span.kind == MarkdownSpanKind.CODE_BLOCK }.metadata,
        )
    }

    @Test
    fun `parses adaptive table cells and column alignments`() {
        val document = StreamingMarkdownParser.parse(
            "| Name | Result | Notes |\n| :--- | ---: | :---: |\n| **A** | 42 | local |\n| B | 7 | wraps here |",
        )

        assertEquals("Name\tResult\tNotes\nA\t42\tlocal\nB\t7\twraps here", document.text)
        val table = document.spans.single { span -> span.kind == MarkdownSpanKind.TABLE }
        assertEquals("SEC", table.metadata)
        assertEquals(document.text, document.text.substring(table.start, table.end))
        assertSpan(document, MarkdownSpanKind.BOLD, "A")
    }

    @Test
    fun `horizontal rule remains a structural full-width block`() {
        val document = StreamingMarkdownParser.parse("Before\n---\nAfter")

        assertEquals("Before\n────────\nAfter", document.text)
        assertSpan(document, MarkdownSpanKind.HORIZONTAL_RULE, "────────")
    }

    @Test
    fun `escaped markers and underscores inside words remain literal`() {
        val document = StreamingMarkdownParser.parse("\\*literal\\* file_name")

        assertEquals("*literal* file_name", document.text)
        assertTrue(document.spans.isEmpty())
    }

    @Test
    fun `nested emphasis ranges can overlap`() {
        val document = StreamingMarkdownParser.parse("**bold and *italic***")

        assertEquals("bold and italic", document.text)
        assertSpan(document, MarkdownSpanKind.BOLD, "bold and italic")
        assertSpan(document, MarkdownSpanKind.ITALIC, "italic")
    }

    @Test
    fun `renders text commands inside complete and streaming inline math`() {
        val complete = StreamingMarkdownParser.parse("Use \$\\text{local model}\$ now")
        val streaming = StreamingMarkdownParser.parse("Use \$\\text{partial")

        assertEquals("Use local model now", complete.text)
        assertSpan(complete, MarkdownSpanKind.INLINE_MATH, "local model")
        assertSpan(complete, MarkdownSpanKind.MATH_TEXT, "local model")
        assertEquals("Use partial", streaming.text)
        assertSpan(streaming, MarkdownSpanKind.INLINE_MATH, "partial")
        assertSpan(streaming, MarkdownSpanKind.MATH_TEXT, "partial")
    }

    @Test
    fun `renders common math symbols and scripts while preserving currency`() {
        val document = StreamingMarkdownParser.parse(
            "Value \$x^2 + y_1 \\le \\pi\$; price \$5; escaped \\\$7",
        )

        assertEquals("Value x2 + y1 ≤ π; price \$5; escaped \$7", document.text)
        assertSpan(document, MarkdownSpanKind.MATH_SUPERSCRIPT, "2")
        assertSpan(document, MarkdownSpanKind.MATH_SUBSCRIPT, "1")
        assertSpan(document, MarkdownSpanKind.INLINE_MATH, "x2 + y1 ≤ π")
    }

    private fun assertSpan(
        document: MarkdownDocument,
        kind: MarkdownSpanKind,
        expectedText: String,
    ) {
        val matching = document.spans.filter { span -> span.kind == kind }
        assertTrue(
            "Expected $kind span for '$expectedText' in ${document.spans}",
            matching.any { span -> document.text.substring(span.start, span.end) == expectedText },
        )
    }
}
