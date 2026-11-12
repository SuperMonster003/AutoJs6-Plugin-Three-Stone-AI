package io.github.supermonster003.autojs6.plugin.threestoneai

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/** Top-level shape of a structured JSON reply. */
internal enum class StructuredJsonShape(val opening: Char) {
    OBJECT('{'),
    ARRAY('['),
}

/**
 * Finds the JSON value in a model reply that wraps it in a Markdown fence or adds prose before or
 * after it (roadmap D106, D109). Providers that ignore the response schema, and local models, often
 * do. Pure JVM and read-only: callers still validate the returned text with their own strict rules,
 * and fall back to their original error when this returns null.
 */
internal object StructuredJsonText {
    private const val MAXIMUM_CANDIDATES = 32
    private const val MAXIMUM_DEPTH = 64
    private val OPENING_FENCE = Regex("""^[ \t]*(`{3,}|~{3,})(.*)$""")
    private val CLOSING_FENCE = Regex("""^[ \t]*(`{3,}|~{3,})[ \t]*$""")

    /**
     * Returns the first complete, strictly valid JSON value of one of [shapes] in [text]: the whole
     * trimmed text when it already is one, otherwise the first one inside a Markdown fence, otherwise
     * the first one anywhere in the text. Returns null when there is none (prose only, or truncated).
     */
    fun extract(text: String, shapes: Set<StructuredJsonShape> = StructuredJsonShape.entries.toSet()): String? {
        require(shapes.isNotEmpty()) { "At least one JSON shape is required" }
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && shapes.any { shape -> shape.opening == trimmed.first() } && isStrictJson(trimmed)) {
            return trimmed
        }
        fencedBodies(trimmed).forEach { body ->
            firstValue(body, shapes)?.let { value -> return value }
        }
        return firstValue(trimmed, shapes)
    }

    /** Bodies of the Markdown fences in [text], in order; an unclosed fence runs to the end. */
    private fun fencedBodies(text: String): Sequence<String> = sequence {
        val lines = text.lines()
        var index = 0
        while (index < lines.size) {
            val marker = openingMarker(lines[index])
            if (marker == null) {
                index++
                continue
            }
            val end = (index + 1 until lines.size).firstOrNull { line -> closes(lines[line], marker) } ?: lines.size
            yield(lines.subList(index + 1, end).joinToString("\n"))
            index = end + 1
        }
    }

    private fun openingMarker(line: String): String? {
        val match = OPENING_FENCE.matchEntire(line) ?: return null
        val marker = match.groupValues[1]
        // A backtick fence's info string cannot contain backticks; "```a```" is inline code.
        if (marker.first() == '`' && match.groupValues[2].contains('`')) return null
        return marker
    }

    private fun closes(line: String, opening: String): Boolean {
        val marker = CLOSING_FENCE.matchEntire(line)?.groupValues?.get(1) ?: return false
        return marker.first() == opening.first() && marker.length >= opening.length
    }

    /** The first balanced candidate of [shapes] in [text] that parses as strict JSON. */
    private fun firstValue(text: String, shapes: Set<StructuredJsonShape>): String? {
        var attempts = 0
        for (start in text.indices) {
            if (shapes.none { shape -> shape.opening == text[start] }) continue
            if (++attempts > MAXIMUM_CANDIDATES) return null
            val end = closingIndex(text, start) ?: continue
            val candidate = text.substring(start, end + 1)
            if (isStrictJson(candidate)) return candidate
        }
        return null
    }

    /** Index of the bracket that closes the one at [start], skipping string contents; null if none. */
    private fun closingIndex(text: String, start: Int): Int? {
        val expected = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> expected.addLast('}')
                '[' -> expected.addLast(']')
                '}', ']' -> {
                    if (expected.removeLastOrNull() != char) return null
                    if (expected.isEmpty()) return index
                }
            }
            if (expected.size > MAXIMUM_DEPTH) return null
        }
        return null
    }

    private fun isStrictJson(candidate: String): Boolean = try {
        JsonReader(StringReader(candidate)).use { reader ->
            reader.strictness = Strictness.STRICT
            reader.skipValue()
            reader.peek() == JsonToken.END_DOCUMENT
        }
    } catch (_: Exception) {
        false
    }
}
