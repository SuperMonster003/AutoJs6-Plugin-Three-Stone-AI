package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** The text replacement for one event of a live dictation session. */
internal data class LiveDictationEdit(
    /** Replace `[start, end)` of the current text with [insertion]. */
    val start: Int,
    val end: Int,
    val insertion: String,
    /** The cursor after the replacement. */
    val cursor: Int,
    /** A final only: nothing more fits, so the session ends (roadmap D209). */
    val limitReached: Boolean,
    /** A final only: part of the recognized text was dropped. */
    val truncated: Boolean,
)

/**
 * Where live dictation writes in the composer (roadmap D9, D204, D209). Each utterance goes where the cursor is (the
 * selection is replaced, without focus it is appended); its partial is shown in that place, spaced like the final
 * will be ([DictationInsertion], with a space after the previous sentence's ASCII punctuation, D216) and cut to fit
 * the limit silently; the final replaces the partial. A final that is cut,
 * or that fills the composer to [maximumLength], ends the session. Pure, for unit tests; [LiveDictationComposer] applies
 * the edits to the input box.
 */
internal object LiveDictationEdits {
    /** Shows [partial] in place of `[start, end)`: the selection for the first partial, the previous partial later. */
    fun partial(current: CharSequence, start: Int, end: Int, partial: String, maximumLength: Int): LiveDictationEdit {
        val edit = DictationInsertion.edit(current, start, end, partial, maximumLength, spaceAfterPunctuation = true)
        return when {
            // An empty partial (or one that no longer fits) clears what the previous one showed.
            edit == null || edit.insertion.isEmpty() -> LiveDictationEdit(
                start = start.coerceIn(0, current.length),
                end = end.coerceIn(start.coerceIn(0, current.length), current.length),
                insertion = "",
                cursor = start.coerceIn(0, current.length),
                limitReached = false,
                truncated = false,
            )
            else -> LiveDictationEdit(edit.start, edit.end, edit.insertion, edit.cursor, limitReached = false, truncated = false)
        }
    }

    /** Commits [text] in place of `[start, end)`; null when the final is empty and there is no partial to remove. */
    fun final(current: CharSequence, start: Int, end: Int, text: String, maximumLength: Int): LiveDictationEdit? {
        val edit = DictationInsertion.edit(current, start, end, text, maximumLength, spaceAfterPunctuation = true)
            ?: return if (end > start) {
                // An empty final drops the partial it closes.
                LiveDictationEdit(start, end, "", start, limitReached = false, truncated = false)
            } else {
                null
            }
        val length = current.length - (edit.end - edit.start) + edit.insertion.length
        return LiveDictationEdit(
            start = edit.start,
            end = edit.end,
            insertion = edit.insertion,
            cursor = edit.cursor,
            limitReached = edit.truncated || length >= maximumLength,
            truncated = edit.truncated,
        )
    }
}
