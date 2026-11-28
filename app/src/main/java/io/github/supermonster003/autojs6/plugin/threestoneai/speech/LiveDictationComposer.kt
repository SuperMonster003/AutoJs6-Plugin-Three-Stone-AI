package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.EditText

/**
 * Applies [LiveDictationEdits] to the input box (roadmap D209): the partial of the current utterance is gray and
 * tracked by spans, so typing elsewhere does not move it; a final replaces it in the normal text color. When the
 * session ends without a final for the partial (a cancel), the text stays as shown (roadmap D215). Main thread only.
 *
 * A voice conversation round (P6-3, Q88) uses [appendOnly]: its gray text goes after the text already there, never in
 * place of a selection, so [discardPartial] can take it out again when the round sends it as the voice message.
 */
internal class LiveDictationComposer(
    private val input: EditText,
    private val partialColor: Int,
    private val maximumLength: Int,
    private val appendOnly: Boolean = false,
) {
    private var partialSpan: PartialMark? = null
    private var colorSpan: ForegroundColorSpan? = null

    /** The shown partial, or null; instrumentation reads it to check what is gray. */
    val partialText: String?
        get() {
            val editable = input.text ?: return null
            val mark = partialSpan ?: return null
            val start = editable.getSpanStart(mark)
            val end = editable.getSpanEnd(mark)
            return if (start in 0..end) editable.subSequence(start, end).toString() else null
        }

    fun partial(text: String) {
        val editable = input.text ?: return
        val (start, end) = pendingRange(editable)
        val edit = LiveDictationEdits.partial(editable, start, end, text, maximumLength)
        clearSpans(editable)
        editable.replace(edit.start, edit.end, edit.insertion)
        if (edit.insertion.isNotEmpty()) {
            val spanEnd = edit.start + edit.insertion.length
            partialSpan = PartialMark().also { mark ->
                editable.setSpan(mark, edit.start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            colorSpan = ForegroundColorSpan(partialColor).also { span ->
                editable.setSpan(span, edit.start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        input.setSelection(edit.cursor.coerceIn(0, editable.length))
    }

    /** Commits [text]; returns the edit so the caller can end the session at the limit, or null when nothing changed. */
    fun final(text: String): LiveDictationEdit? {
        val editable = input.text ?: return null
        val (start, end) = pendingRange(editable)
        val edit = LiveDictationEdits.final(editable, start, end, text, maximumLength)
        clearSpans(editable)
        if (edit == null) return null
        editable.replace(edit.start, edit.end, edit.insertion)
        input.setSelection(edit.cursor.coerceIn(0, editable.length))
        return edit
    }

    /** Keeps a pending partial as ordinary text (the session ended without its final). */
    fun commitPartial() {
        input.text?.let(::clearSpans)
    }

    /** Takes a pending partial out of the input box again, with the spacing shown before it. */
    fun discardPartial() {
        val editable = input.text ?: return
        val mark = partialSpan
        val start = mark?.let(editable::getSpanStart) ?: -1
        val end = mark?.let(editable::getSpanEnd) ?: -1
        clearSpans(editable)
        if (start in 0 until end) {
            editable.delete(start, end)
            input.setSelection(start.coerceIn(0, editable.length))
        }
    }

    /** The partial's range, else the selection (focused) or the end of the text. */
    private fun pendingRange(editable: Spannable): Pair<Int, Int> {
        partialSpan?.let { mark ->
            val start = editable.getSpanStart(mark)
            val end = editable.getSpanEnd(mark)
            if (start in 0..end) return start to end
        }
        val length = editable.length
        if (appendOnly || !input.hasFocus() || input.selectionStart < 0 || input.selectionEnd < 0) return length to length
        val start = minOf(input.selectionStart, input.selectionEnd).coerceIn(0, length)
        val end = maxOf(input.selectionStart, input.selectionEnd).coerceIn(0, length)
        return start to end
    }

    private fun clearSpans(editable: Spannable) {
        partialSpan?.let(editable::removeSpan)
        colorSpan?.let(editable::removeSpan)
        partialSpan = null
        colorSpan = null
    }

    /** Marks the partial; never copied with the text. */
    private class PartialMark : android.text.NoCopySpan
}
