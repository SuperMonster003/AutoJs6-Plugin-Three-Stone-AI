package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** The text replacement for one dictation result. */
internal data class DictationEdit(
    /** Replace `[start, end)` of the current text with [insertion]. */
    val start: Int,
    val end: Int,
    val insertion: String,
    /** The cursor position after the replacement. */
    val cursor: Int,
    /** Part of the recognized text was dropped to stay within the input limit. */
    val truncated: Boolean,
)

/**
 * Where dictated text goes in the composer (roadmap D9, D23): at the cursor, replacing a selection,
 * never sent automatically. A space is added on a side where the recognized text meets a letter or
 * digit of a script that separates words with spaces (Latin, Cyrillic, Greek, Hangul, Arabic and
 * others); Chinese, Japanese and similar scripts are joined directly. The result never exceeds the
 * composer limit; text beyond it is cut at a code point boundary and reported.
 */
internal object DictationInsertion {
    fun edit(
        current: CharSequence,
        selectionStart: Int,
        selectionEnd: Int,
        recognized: String,
        maximumLength: Int,
    ): DictationEdit? {
        val text = recognized.trim()
        if (text.isEmpty()) return null
        val length = current.length
        // A composer without focus reports -1; dictation then appends.
        val (start, end) = if (selectionStart < 0 || selectionEnd < 0) {
            length to length
        } else {
            minOf(selectionStart, selectionEnd).coerceIn(0, length) to
                maxOf(selectionStart, selectionEnd).coerceIn(0, length)
        }
        val leading = start > 0 && joinsWithSpace(codePointBefore(current, start), text.codePointAt(0))
        val trailing = end < length &&
            joinsWithSpace(text.codePointBefore(text.length), Character.codePointAt(current, end))
        val capacity = maximumLength - (length - (end - start))
        val prefix = if (leading) " " else ""
        val suffix = if (trailing) " " else ""
        val insertion: String
        val truncated: Boolean
        when {
            prefix.length + text.length + suffix.length <= capacity -> {
                insertion = prefix + text + suffix
                truncated = false
            }
            prefix.length + text.length <= capacity -> {
                // Only the separating space after the text is dropped.
                insertion = prefix + text
                truncated = false
            }
            else -> {
                val kept = cutAtCodePoint(text, capacity - prefix.length)
                // Nothing of the recognized text fits: leave the composer, selection included, unchanged.
                if (kept.isEmpty()) return DictationEdit(end, end, "", end, truncated = true)
                insertion = prefix + kept
                truncated = true
            }
        }
        return DictationEdit(
            start = start,
            end = end,
            insertion = insertion,
            cursor = start + insertion.length,
            truncated = truncated,
        )
    }

    /** Whether a space belongs between two adjacent code points from separate pieces of text. */
    fun joinsWithSpace(before: Int, after: Int): Boolean = isSpacedWord(before) && isSpacedWord(after)

    private fun isSpacedWord(codePoint: Int): Boolean {
        if (!Character.isLetterOrDigit(codePoint)) return false
        return when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.BOPOMOFO,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO,
            Character.UnicodeScript.KHMER,
            Character.UnicodeScript.MYANMAR,
            Character.UnicodeScript.TIBETAN,
            Character.UnicodeScript.YI,
            -> false
            else -> true
        }
    }

    private fun codePointBefore(text: CharSequence, index: Int): Int = Character.codePointBefore(text, index)

    private fun cutAtCodePoint(text: String, limit: Int): String {
        if (limit <= 0) return ""
        if (text.length <= limit) return text
        var end = limit
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end)
    }
}
