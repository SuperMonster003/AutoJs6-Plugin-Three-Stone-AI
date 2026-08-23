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
            "Heading\n• item\nquote\nUse value and code",
            document.text,
        )
        assertSpan(document, MarkdownSpanKind.HEADING_1, "Heading")
        assertSpan(document, MarkdownSpanKind.LIST_ITEM, "• item")
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
