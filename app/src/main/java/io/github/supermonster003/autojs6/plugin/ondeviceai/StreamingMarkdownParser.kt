package io.github.supermonster003.autojs6.plugin.ondeviceai

internal enum class MarkdownSpanKind {
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    INLINE_CODE,
    CODE_BLOCK,
    HEADING_1,
    HEADING_2,
    HEADING_3,
    HEADING_4,
    HEADING_5,
    HEADING_6,
    BLOCK_QUOTE,
    LIST_ITEM,
    LINK,
    HORIZONTAL_RULE,
    INLINE_MATH,
    MATH_TEXT,
    MATH_SUPERSCRIPT,
    MATH_SUBSCRIPT,
}

internal data class MarkdownSpan(
    val start: Int,
    val end: Int,
    val kind: MarkdownSpanKind,
    val metadata: String? = null,
) {
    init {
        require(start >= 0)
        require(end >= start)
    }
}

internal data class MarkdownDocument(
    val text: String,
    val spans: List<MarkdownSpan>,
) {
    init {
        spans.forEach { span -> require(span.end <= text.length) }
    }
}

/**
 * A deliberately bounded Markdown subset optimized for repeatedly parsing an incomplete stream.
 * Open emphasis, code spans, and fenced code blocks are rendered provisionally to the current end,
 * so syntax markers do not flicker while tokens arrive.
 */
internal object StreamingMarkdownParser {
    fun parse(markdown: String): MarkdownDocument {
        if (markdown.isEmpty()) return MarkdownDocument("", emptyList())
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val output = StringBuilder(normalized.length)
        val spans = ArrayList<MarkdownSpan>()
        var visibleLineCount = 0
        var fenceMarker: String? = null
        var fenceStart = -1

        splitLines(normalized).forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            val possibleFence = when {
                trimmed.startsWith("```") -> "```"
                trimmed.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (possibleFence != null) {
                if (fenceMarker == null) {
                    fenceMarker = possibleFence
                    fenceStart = output.length + if (visibleLineCount > 0) 1 else 0
                } else if (fenceMarker == possibleFence) {
                    addSpan(spans, fenceStart, output.length, MarkdownSpanKind.CODE_BLOCK)
                    fenceMarker = null
                    fenceStart = -1
                } else {
                    appendVisibleLine(output, rawLine, visibleLineCount++)
                }
                return@forEach
            }

            if (fenceMarker != null) {
                appendVisibleLine(output, rawLine, visibleLineCount++)
                return@forEach
            }

            val headingLevel = headingLevel(rawLine)
            if (headingLevel > 0) {
                val content = rawLine.dropWhile(Char::isWhitespace).drop(headingLevel + 1)
                appendInlineLine(
                    output = output,
                    spans = spans,
                    inline = parseInline(content),
                    visibleLineIndex = visibleLineCount++,
                    blockKind = MarkdownSpanKind.entries[
                        MarkdownSpanKind.HEADING_1.ordinal + headingLevel - 1
                    ],
                )
                return@forEach
            }

            val quoteContent = blockQuoteContent(rawLine)
            if (quoteContent != null) {
                appendInlineLine(
                    output,
                    spans,
                    parseInline(quoteContent),
                    visibleLineCount++,
                    MarkdownSpanKind.BLOCK_QUOTE,
                )
                return@forEach
            }

            val unorderedContent = unorderedListContent(rawLine)
            if (unorderedContent != null) {
                appendInlineLine(
                    output,
                    spans,
                    prefixDocument("• ", parseInline(unorderedContent)),
                    visibleLineCount++,
                    MarkdownSpanKind.LIST_ITEM,
                )
                return@forEach
            }

            val ordered = orderedListContent(rawLine)
            if (ordered != null) {
                appendInlineLine(
                    output,
                    spans,
                    prefixDocument("${ordered.first}. ", parseInline(ordered.second)),
                    visibleLineCount++,
                    MarkdownSpanKind.LIST_ITEM,
                )
                return@forEach
            }

            if (isHorizontalRule(rawLine)) {
                val start = appendVisibleLine(output, HORIZONTAL_RULE_TEXT, visibleLineCount++)
                addSpan(
                    spans,
                    start,
                    start + HORIZONTAL_RULE_TEXT.length,
                    MarkdownSpanKind.HORIZONTAL_RULE,
                )
                return@forEach
            }

            appendInlineLine(output, spans, parseInline(rawLine), visibleLineCount++)
        }

        if (fenceMarker != null) {
            addSpan(spans, fenceStart, output.length, MarkdownSpanKind.CODE_BLOCK)
        }
        return MarkdownDocument(output.toString(), spans.sortedWith(SPAN_ORDER))
    }

    private fun parseInline(source: String): MarkdownDocument {
        if (source.isEmpty()) return MarkdownDocument("", emptyList())
        val output = StringBuilder(source.length)
        val spans = ArrayList<MarkdownSpan>()
        val openMarkers = ArrayList<OpenMarker>()
        var index = 0
        while (index < source.length) {
            if (source[index] == '\\' && index + 1 < source.length && isEscapable(source[index + 1])) {
                output.append(source[index + 1])
                index += 2
                continue
            }

            val link = parseLink(source, index)
            if (link != null) {
                val label = parseInline(link.label)
                val start = output.length
                output.append(label.text)
                label.spans.forEach { span -> spans += span.shifted(start) }
                addSpan(spans, start, output.length, MarkdownSpanKind.LINK, link.destination)
                index = link.endExclusive
                continue
            }

            if (source[index] == '$') {
                if (source.getOrNull(index + 1) == '$') {
                    output.append("$$")
                    index += 2
                    continue
                }
                val closing = findUnescapedDollar(source, index + 1)
                val provisional = closing < 0 && source.getOrNull(index + 1) == '\\'
                if (closing >= 0 || provisional) {
                    val contentEnd = if (closing >= 0) closing else source.length
                    val math = parseInlineMath(source.substring(index + 1, contentEnd))
                    if (math.text.isNotEmpty()) {
                        val start = output.length
                        output.append(math.text)
                        addSpan(spans, start, output.length, MarkdownSpanKind.INLINE_MATH)
                        math.spans.forEach { span -> spans += span.shifted(start) }
                        index = if (closing >= 0) closing + 1 else source.length
                        continue
                    }
                }
            }

            if (source[index] == '`') {
                val markerLength = consecutiveCount(source, index, '`').coerceAtMost(MAXIMUM_CODE_MARKER)
                val marker = "`".repeat(markerLength)
                val closing = source.indexOf(marker, index + markerLength)
                val contentEnd = if (closing >= 0) closing else source.length
                val start = output.length
                output.append(source, index + markerLength, contentEnd)
                addSpan(spans, start, output.length, MarkdownSpanKind.INLINE_CODE)
                index = if (closing >= 0) closing + markerLength else source.length
                continue
            }

            val marker = markerAt(source, index)
            if (marker != null) {
                val matchingIndex = openMarkers.indexOfLast { open -> open.marker == marker.text }
                val canClose = matchingIndex >= 0 && index > 0 && !source[index - 1].isWhitespace()
                if (canClose) {
                    val open = openMarkers.removeAt(matchingIndex)
                    addSpan(spans, open.outputStart, output.length, marker.kind)
                    index += marker.text.length
                    continue
                }
                val nextIndex = index + marker.text.length
                val canOpen = nextIndex >= source.length || !source[nextIndex].isWhitespace()
                if (canOpen) {
                    openMarkers += OpenMarker(marker.text, marker.kind, output.length)
                    index = nextIndex
                    continue
                }
                output.append(marker.text)
                index = nextIndex
                continue
            }

            output.append(source[index])
            index++
        }

        openMarkers.forEach { open ->
            addSpan(spans, open.outputStart, output.length, open.kind)
        }
        return MarkdownDocument(output.toString(), spans.sortedWith(SPAN_ORDER))
    }

    private fun parseInlineMath(source: String): MarkdownDocument {
        val output = StringBuilder(source.length)
        val spans = ArrayList<MarkdownSpan>()
        var index = 0
        while (index < source.length) {
            if (source.startsWith("\\text{", index)) {
                val contentStart = index + MATH_TEXT_PREFIX.length
                val contentEnd = matchingBraceEnd(source, contentStart)
                val end = if (contentEnd >= 0) contentEnd else source.length
                val start = output.length
                output.append(source, contentStart, end)
                addSpan(spans, start, output.length, MarkdownSpanKind.MATH_TEXT)
                index = if (contentEnd >= 0) contentEnd + 1 else source.length
                continue
            }

            val scriptKind = when (source[index]) {
                '^' -> MarkdownSpanKind.MATH_SUPERSCRIPT
                '_' -> MarkdownSpanKind.MATH_SUBSCRIPT
                else -> null
            }
            if (scriptKind != null) {
                val atom = parseMathAtom(source, index + 1)
                if (atom != null) {
                    val start = output.length
                    output.append(atom.text)
                    addSpan(spans, start, output.length, scriptKind)
                    index = atom.endExclusive
                    continue
                }
            }

            if (source[index] == '\\') {
                val commandEnd = (index + 1 until source.length)
                    .firstOrNull { commandIndex -> !source[commandIndex].isLetter() }
                    ?: source.length
                val command = source.substring(index + 1, commandEnd)
                val replacement = MATH_COMMANDS[command]
                if (replacement != null) {
                    output.append(replacement)
                    index = commandEnd
                    continue
                }
                if (index + 1 < source.length && source[index + 1] in MATH_ESCAPABLE) {
                    output.append(source[index + 1])
                    index += 2
                    continue
                }
            }

            if (source[index] != '{' && source[index] != '}') output.append(source[index])
            index++
        }
        return MarkdownDocument(output.toString(), spans.sortedWith(SPAN_ORDER))
    }

    private fun parseMathAtom(source: String, start: Int): ParsedMathAtom? {
        if (start >= source.length) return null
        if (source[start] == '{') {
            val end = matchingBraceEnd(source, start + 1)
            val contentEnd = if (end >= 0) end else source.length
            val parsed = parseInlineMath(source.substring(start + 1, contentEnd))
            return ParsedMathAtom(
                text = parsed.text,
                endExclusive = if (end >= 0) end + 1 else source.length,
            ).takeIf { it.text.isNotEmpty() }
        }
        return ParsedMathAtom(source[start].toString(), start + 1)
    }

    private fun matchingBraceEnd(source: String, contentStart: Int): Int {
        var depth = 1
        var index = contentStart
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return index
            }
            index++
        }
        return -1
    }

    private fun findUnescapedDollar(source: String, start: Int): Int {
        var index = start
        while (index < source.length) {
            if (source[index] == '$') {
                var slashCount = 0
                var previous = index - 1
                while (previous >= 0 && source[previous] == '\\') {
                    slashCount++
                    previous--
                }
                if (slashCount % 2 == 0) return index
            }
            index++
        }
        return -1
    }

    private fun parseLink(source: String, start: Int): ParsedLink? {
        val image = source.startsWith("![", start)
        if (!image && source[start] != '[') return null
        val labelStart = start + if (image) 2 else 1
        val separator = source.indexOf("](", labelStart)
        if (separator < 0 || source.substring(labelStart, separator).contains('\n')) return null
        val destinationEnd = source.indexOf(')', separator + 2)
        if (destinationEnd < 0) return null
        val destination = source.substring(separator + 2, destinationEnd).trim()
        if (destination.isEmpty() || destination.contains('\n')) return null
        val label = source.substring(labelStart, separator).ifEmpty { destination }
        return ParsedLink(
            label = if (image) "[$label]" else label,
            destination = destination,
            endExclusive = destinationEnd + 1,
        )
    }

    private fun markerAt(source: String, index: Int): Marker? {
        if (source.startsWith("**", index)) return Marker("**", MarkdownSpanKind.BOLD)
        if (source.startsWith("__", index) && !insideWord(source, index, 2)) {
            return Marker("__", MarkdownSpanKind.BOLD)
        }
        if (source.startsWith("~~", index)) return Marker("~~", MarkdownSpanKind.STRIKETHROUGH)
        if (source[index] == '*') return Marker("*", MarkdownSpanKind.ITALIC)
        if (source[index] == '_' && !insideWord(source, index, 1)) {
            return Marker("_", MarkdownSpanKind.ITALIC)
        }
        return null
    }

    private fun insideWord(source: String, index: Int, markerLength: Int): Boolean {
        val before = source.getOrNull(index - 1)
        val after = source.getOrNull(index + markerLength)
        return before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true
    }

    private fun appendInlineLine(
        output: StringBuilder,
        spans: MutableList<MarkdownSpan>,
        inline: MarkdownDocument,
        visibleLineIndex: Int,
        blockKind: MarkdownSpanKind? = null,
    ) {
        val start = appendVisibleLine(output, inline.text, visibleLineIndex)
        inline.spans.forEach { span -> spans += span.shifted(start) }
        blockKind?.let { kind -> addSpan(spans, start, output.length, kind) }
    }

    private fun appendVisibleLine(output: StringBuilder, line: String, visibleLineIndex: Int): Int {
        if (visibleLineIndex > 0) output.append('\n')
        return output.length.also { output.append(line) }
    }

    private fun prefixDocument(prefix: String, document: MarkdownDocument): MarkdownDocument =
        MarkdownDocument(
            text = prefix + document.text,
            spans = document.spans.map { span -> span.shifted(prefix.length) },
        )

    private fun headingLevel(line: String): Int {
        val content = line.dropWhile(Char::isWhitespace)
        val count = content.takeWhile { character -> character == '#' }.length
        return count.takeIf {
            it in 1..MAXIMUM_HEADING_LEVEL && content.getOrNull(it)?.isWhitespace() == true
        } ?: 0
    }

    private fun blockQuoteContent(line: String): String? {
        val content = line.dropWhile(Char::isWhitespace)
        if (!content.startsWith('>')) return null
        return content.drop(1).removePrefix(" ")
    }

    private fun unorderedListContent(line: String): String? {
        val content = line.dropWhile(Char::isWhitespace)
        if (content.length < 2 || content[0] !in UNORDERED_MARKERS || !content[1].isWhitespace()) {
            return null
        }
        return content.drop(2).dropWhile(Char::isWhitespace)
    }

    private fun orderedListContent(line: String): Pair<String, String>? {
        val content = line.dropWhile(Char::isWhitespace)
        val digits = content.takeWhile(Char::isDigit)
        if (digits.isEmpty() || digits.length > MAXIMUM_ORDERED_DIGITS) return null
        val delimiter = content.getOrNull(digits.length)
        if (delimiter != '.' && delimiter != ')') return null
        if (content.getOrNull(digits.length + 1)?.isWhitespace() != true) return null
        return digits to content.drop(digits.length + 2).dropWhile(Char::isWhitespace)
    }

    private fun isHorizontalRule(line: String): Boolean {
        val compact = line.filterNot(Char::isWhitespace)
        return compact.length >= 3 && compact.all { character -> character == compact.first() } &&
            compact.first() in UNORDERED_MARKERS
    }

    private fun splitLines(text: String): List<String> {
        val lines = ArrayList<String>()
        var start = 0
        while (true) {
            val end = text.indexOf('\n', start)
            if (end < 0) {
                lines += text.substring(start)
                return lines
            }
            lines += text.substring(start, end)
            start = end + 1
            if (start == text.length) {
                lines += ""
                return lines
            }
        }
    }

    private fun consecutiveCount(source: String, start: Int, character: Char): Int {
        var index = start
        while (index < source.length && source[index] == character) index++
        return index - start
    }

    private fun isEscapable(character: Char): Boolean = character in ESCAPABLE_CHARACTERS

    private fun addSpan(
        spans: MutableList<MarkdownSpan>,
        start: Int,
        end: Int,
        kind: MarkdownSpanKind,
        metadata: String? = null,
    ) {
        if (end > start) spans += MarkdownSpan(start, end, kind, metadata)
    }

    private fun MarkdownSpan.shifted(offset: Int) = copy(start = start + offset, end = end + offset)

    private data class Marker(
        val text: String,
        val kind: MarkdownSpanKind,
    )

    private data class OpenMarker(
        val marker: String,
        val kind: MarkdownSpanKind,
        val outputStart: Int,
    )

    private data class ParsedLink(
        val label: String,
        val destination: String,
        val endExclusive: Int,
    )

    private data class ParsedMathAtom(
        val text: String,
        val endExclusive: Int,
    )

    private val SPAN_ORDER = compareBy<MarkdownSpan>(MarkdownSpan::start, MarkdownSpan::end)
    private val UNORDERED_MARKERS = setOf('-', '+', '*')
    private val ESCAPABLE_CHARACTERS = setOf(
        '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '~', '$',
    )
    private val MATH_ESCAPABLE = setOf('\\', '{', '}', '$', '_', '^', '%', '#', '&')
    private val MATH_COMMANDS = mapOf(
        "alpha" to "α",
        "beta" to "β",
        "gamma" to "γ",
        "delta" to "δ",
        "theta" to "θ",
        "lambda" to "λ",
        "mu" to "μ",
        "pi" to "π",
        "sigma" to "σ",
        "phi" to "φ",
        "omega" to "ω",
        "times" to "×",
        "cdot" to "·",
        "le" to "≤",
        "leq" to "≤",
        "ge" to "≥",
        "geq" to "≥",
        "neq" to "≠",
        "infty" to "∞",
        "sqrt" to "√",
    )
    private const val MAXIMUM_CODE_MARKER = 3
    private const val MAXIMUM_HEADING_LEVEL = 6
    private const val MAXIMUM_ORDERED_DIGITS = 4
    private const val HORIZONTAL_RULE_TEXT = "────────"
    private const val MATH_TEXT_PREFIX = "\\text{"
}
