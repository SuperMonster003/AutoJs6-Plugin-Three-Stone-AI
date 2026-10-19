package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import io.github.supermonster003.autojs6.plugin.threestoneai.MarkdownSpanKind
import io.github.supermonster003.autojs6.plugin.threestoneai.StreamingMarkdownParser

/** Localized sentences read in place of content that is not read word by word (roadmap D11). */
internal data class SpeakableHints(
    val codeBlock: String,
    val table: String,
)

/**
 * Turns a Markdown reply into sentences for text-to-speech (roadmap D11, D25).
 *
 * Headings, emphasis, list and quote markers are dropped; links read their text; bare URLs read
 * only their host; images and rules are skipped; inline code and math read their rendered text;
 * a code block or a table becomes one hint sentence. Sentences end at an ideographic full stop,
 * full-width question or exclamation mark, ellipsis, at `.` `?` `!` (and the Arabic question mark)
 * followed by whitespace, unless the period ends a decimal or a common abbreviation, and at every
 * line break. A sentence longer than the engine limit is split again at a comma or whitespace.
 */
internal object SpeakableTextPolicy {
    /** D24: `min(TextToSpeech.getMaxSpeechInputLength(), 1000)`. */
    const val MAXIMUM_SENTENCE_LENGTH = 1_000

    /** Every sentence of a finished reply; the same sequence a streamed reading produces. */
    fun sentences(
        markdown: String,
        hints: SpeakableHints,
        maximumLength: Int = MAXIMUM_SENTENCE_LENGTH,
    ): List<String> = SpeakableTextSegmenter(hints, maximumLength).finish(markdown)

    /** The speakable text of one source line outside code blocks and tables. */
    internal fun lineText(line: String): String {
        val document = StreamingMarkdownParser.parse(line)
        if (document.spans.any { span -> span.kind == MarkdownSpanKind.HORIZONTAL_RULE }) return ""
        val text = document.text
        val removed = ArrayList<IntRange>()
        if (document.spans.any { span -> span.kind == MarkdownSpanKind.LIST_ITEM && span.start == 0 }) {
            LIST_PREFIX.find(text)?.let { match -> removed += match.range }
        }
        document.spans
            .filter { span -> span.kind == MarkdownSpanKind.LINK }
            .filter { span ->
                // The parser renders an image as its alternative text in brackets.
                span.end - span.start >= 2 && text[span.start] == '[' && text[span.end - 1] == ']'
            }
            .forEach { span -> removed += span.start until span.end }
        val kept = StringBuilder(text.length)
        text.forEachIndexed { index, character ->
            if (removed.none { range -> index in range }) kept.append(character)
        }
        return collapseWhitespace(replaceUrls(kept.toString()))
    }

    /**
     * Splits [text] into sentences. Without [complete], only sentences whose end can no longer
     * change are returned; the unfinished rest waits for more text.
     */
    internal fun split(text: String, complete: Boolean): List<String> {
        val sentences = ArrayList<String>()
        var start = 0
        var index = 0
        while (index < text.length) {
            val character = text[index]
            val wide = character in WIDE_TERMINATORS
            if (!wide && character !in NARROW_TERMINATORS) {
                index++
                continue
            }
            var end = index + 1
            while (end < text.length && (text[end] in TERMINATORS || text[end] in CLOSERS)) end++
            if (end == text.length) {
                if (!complete) break
                addSentence(sentences, text.substring(start, end))
                start = end
                index = end
                continue
            }
            val boundary = wide || (
                text[end].isWhitespace() &&
                    !(character == '.' && end == index + 1 && isAbbreviation(text, index))
                )
            if (boundary) {
                addSentence(sentences, text.substring(start, end))
                start = end
            }
            index = end
        }
        if (complete && start < text.length) addSentence(sentences, text.substring(start))
        return sentences
    }

    /** Splits a sentence longer than [maximumLength] at the last comma or whitespace that fits. */
    internal fun limit(sentence: String, maximumLength: Int): List<String> {
        require(maximumLength > 1)
        if (sentence.length <= maximumLength) return listOf(sentence)
        val parts = ArrayList<String>()
        var rest = sentence
        while (rest.length > maximumLength) {
            var cut = (maximumLength downTo 1).firstOrNull { end ->
                val character = rest[end - 1]
                character in SOFT_BREAKS || character.isWhitespace()
            } ?: maximumLength
            if (Character.isHighSurrogate(rest[cut - 1])) cut--
            rest.substring(0, cut).trim().takeIf(String::isNotEmpty)?.let(parts::add)
            rest = rest.substring(cut).trimStart()
        }
        rest.trim().takeIf(String::isNotEmpty)?.let(parts::add)
        return parts
    }

    private fun addSentence(sentences: MutableList<String>, raw: String) {
        val sentence = raw.trim()
        // Lone punctuation such as "..." or "-" has nothing to say.
        if (sentence.any(Char::isLetterOrDigit)) sentences += sentence
    }

    private fun isAbbreviation(text: String, dotIndex: Int): Boolean {
        var start = dotIndex
        while (start > 0 && (text[start - 1].isLetter() || text[start - 1] == '.')) start--
        val token = text.substring(start, dotIndex).lowercase()
        if (token.isEmpty()) return false
        if (token.length == 1 && token[0] in 'a'..'z') return true
        return token in ABBREVIATIONS
    }

    private fun replaceUrls(text: String): String = URL.replace(text) { match ->
        val url = match.value
        val trailing = url.takeLastWhile { character -> character in URL_TRAILING }
        val core = url.dropLast(trailing.length)
        val authority = core.substringAfter("://").substringBefore('/').substringBefore('?')
            .substringBefore('#').substringAfterLast('@').substringBefore(':')
        authority + trailing
    }

    private fun collapseWhitespace(text: String): String = WHITESPACE.replace(text, " ").trim()

    private val LIST_PREFIX = Regex("^(?:- |\\d{1,4}\\. )")
    private val URL = Regex("(?i)https?://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+")
    private val URL_TRAILING = setOf('.', ',', ';', ':', '!', '?', ')', ']', Char(0x27), Char(0x22))
    private val WHITESPACE = Regex("\\s+")
    private val WIDE_TERMINATORS = setOf(
        Char(0x3002), // ideographic full stop
        Char(0xFF1F), // full-width question mark
        Char(0xFF01), // full-width exclamation mark
        Char(0x2026), // horizontal ellipsis
        Char(0xFF61), // half-width ideographic full stop
    )
    private val NARROW_TERMINATORS = setOf('.', '?', '!', Char(0x061F))
    private val TERMINATORS = WIDE_TERMINATORS + NARROW_TERMINATORS
    private val CLOSERS = setOf(
        Char(0x22), Char(0x27), ')', ']', '}',
        Char(0x201D), Char(0x2019), // right double and single quotation marks
        Char(0x300D), Char(0x300F), // right corner brackets
        Char(0xFF09), Char(0x3011), Char(0x300B), Char(0x3009), // full-width and CJK closing brackets
        Char(0x00BB), // right guillemet
    )
    private val SOFT_BREAKS = setOf(
        ',', ';', ':',
        Char(0x3001), Char(0xFF0C), Char(0xFF1B), Char(0xFF1A), // CJK enumeration comma, comma, semicolon, colon
        Char(0x060C), Char(0x061B), // Arabic comma and semicolon
    )
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "e.g", "i.e", "cf", "fig",
        "no", "vol", "approx", "inc", "ltd", "co", "pp", "sra", "srta", "mme", "mlle",
        "т.е", "т.д", "т.п", "др",
    )
}

/**
 * Streams a growing Markdown reply into sentences (roadmap D25). Pass the whole reply text so far
 * to [update] while it streams and once more to [finish]. The sentences returned over all calls
 * equal [SpeakableTextPolicy.sentences] of the final text, however the text was chunked: a
 * sentence is returned only when no continuation can change it.
 *
 * Block structure follows [StreamingMarkdownParser]: fenced code blocks become one hint when they
 * close (or when the reply ends), and a table becomes one hint once its header and delimiter rows
 * are complete. Only a header row that starts with `|` opens a table here, so an unfinished line
 * that starts otherwise can be read before its line ends. Within an unfinished line, text from
 * the first `[` (link or image) or `$` (math) onward waits for the line to end.
 */
internal class SpeakableTextSegmenter(
    private val hints: SpeakableHints,
    private val maximumLength: Int = SpeakableTextPolicy.MAXIMUM_SENTENCE_LENGTH,
) {
    private var consumed = 0
    private var fence: String? = null
    private var tableOpen = false
    private var lineSentencesEmitted = 0
    private var finished = false

    fun update(markdown: String): List<String> = advance(markdown, final = false)

    fun finish(markdown: String): List<String> = advance(markdown, final = true)

    private fun advance(markdown: String, final: Boolean): List<String> {
        if (finished) return emptyList()
        val text = markdown.replace("\r\n", "\n").replace('\r', '\n')
        if (consumed > text.length) consumed = text.length
        val out = ArrayList<String>()
        while (true) {
            val lineEnd = text.indexOf('\n', consumed)
            val complete = lineEnd >= 0
            if (!complete && !final) {
                readUnfinishedLine(text.substring(consumed), out)
                break
            }
            val end = if (complete) lineEnd else text.length
            val line = text.substring(consumed, end)
            val next = if (complete) end + 1 else end
            val openFence = fence
            when {
                openFence != null -> {
                    if (line.trimStart().startsWith(openFence)) {
                        fence = null
                        emit(out, hints.codeBlock)
                    }
                    consumed = next
                }
                tableOpen -> {
                    if (StreamingMarkdownParser.tableRowCells(line) != null) {
                        consumed = next
                    } else {
                        // The row that ends the table is read as ordinary text.
                        tableOpen = false
                        continue
                    }
                }
                else -> {
                    val marker = fenceMarker(line)
                    val cells = tableHeaderCells(line)
                    when {
                        marker != null -> {
                            fence = marker
                            lineSentencesEmitted = 0
                            consumed = next
                        }
                        cells != null -> when (val decision = tableDecision(text, next, complete, final, cells.size)) {
                            TableDecision.Wait -> break
                            TableDecision.Text -> {
                                readLine(line, out)
                                consumed = next
                            }
                            is TableDecision.Table -> {
                                emit(out, hints.table)
                                tableOpen = true
                                lineSentencesEmitted = 0
                                consumed = decision.consumedUntil
                            }
                        }
                        else -> {
                            readLine(line, out)
                            consumed = next
                        }
                    }
                }
            }
            if (!complete) break
        }
        if (final) {
            if (fence != null) emit(out, hints.codeBlock)
            fence = null
            finished = true
        }
        return out
    }

    private fun readLine(line: String, out: MutableList<String>) {
        val sentences = SpeakableTextPolicy.split(SpeakableTextPolicy.lineText(line), complete = true)
        sentences.drop(lineSentencesEmitted).forEach { sentence -> emit(out, sentence) }
        lineSentencesEmitted = 0
    }

    private fun readUnfinishedLine(rest: String, out: MutableList<String>) {
        if (fence != null || tableOpen) return
        val trimmed = rest.trimStart()
        // An empty start may still become a fence or a table header.
        if (trimmed.isEmpty() || trimmed.startsWith('|')) return
        if (FENCES.any { fence -> trimmed.startsWith(fence) || fence.startsWith(trimmed) }) return
        var cut = rest.indexOfFirst { character -> character == '[' || character == '$' }
        if (cut < 0) cut = rest.length
        if (cut > 0 && rest[cut - 1] == '!') cut--
        val sentences = SpeakableTextPolicy.split(
            SpeakableTextPolicy.lineText(rest.substring(0, cut)),
            complete = false,
        )
        if (sentences.size > lineSentencesEmitted) {
            sentences.drop(lineSentencesEmitted).forEach { sentence -> emit(out, sentence) }
            lineSentencesEmitted = sentences.size
        }
    }

    private fun emit(out: MutableList<String>, sentence: String) {
        out += SpeakableTextPolicy.limit(sentence, maximumLength)
    }

    private fun fenceMarker(line: String): String? {
        val trimmed = line.trimStart()
        return FENCES.firstOrNull(trimmed::startsWith)
    }

    private fun tableHeaderCells(line: String): List<String>? =
        if (line.trimStart().startsWith('|')) StreamingMarkdownParser.tableRowCells(line) else null

    private fun tableDecision(
        text: String,
        next: Int,
        headerComplete: Boolean,
        final: Boolean,
        columns: Int,
    ): TableDecision {
        if (!headerComplete) return TableDecision.Text
        val nextEnd = text.indexOf('\n', next)
        if (nextEnd >= 0) {
            return if (StreamingMarkdownParser.isTableDelimiter(text.substring(next, nextEnd), columns)) {
                TableDecision.Table(nextEnd + 1)
            } else {
                TableDecision.Text
            }
        }
        val partial = text.substring(next)
        if (final) {
            return if (StreamingMarkdownParser.isTableDelimiter(partial, columns)) {
                TableDecision.Table(text.length)
            } else {
                TableDecision.Text
            }
        }
        // Only '|', ':', '-' and spaces can form a delimiter row; anything else settles it now.
        return if (partial.all { character -> character in DELIMITER_CHARACTERS }) {
            TableDecision.Wait
        } else {
            TableDecision.Text
        }
    }

    private sealed interface TableDecision {
        data object Wait : TableDecision
        data object Text : TableDecision
        data class Table(val consumedUntil: Int) : TableDecision
    }

    private companion object {
        val FENCES = listOf("```", "~~~")
        val DELIMITER_CHARACTERS = setOf('|', ':', '-', ' ')
    }
}
