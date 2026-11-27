package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap P6-2 (D9, D204, D209): sentences in a row, spacing, selections and the input limit. */
class LiveDictationEditsTest {
    /** The input box: text, selection, and the range of the gray partial. */
    private class Composer(var text: String, var start: Int = text.length, var end: Int = start, val limit: Int = 16_384) {
        var partial: IntRange? = null
        var ended = false

        fun partial(value: String) {
            val (from, to) = range()
            val edit = LiveDictationEdits.partial(text, from, to, value, limit)
            apply(edit)
            partial = if (edit.insertion.isEmpty()) null else edit.start until edit.start + edit.insertion.length
        }

        fun final(value: String): LiveDictationEdit? {
            val (from, to) = range()
            val edit = LiveDictationEdits.final(text, from, to, value, limit)
            partial = null
            edit?.let(::apply)
            if (edit?.limitReached == true) ended = true
            return edit
        }

        private fun range(): Pair<Int, Int> = partial?.let { it.first to it.last + 1 } ?: (start to end)

        private fun apply(edit: LiveDictationEdit) {
            text = text.substring(0, edit.start) + edit.insertion + text.substring(edit.end)
            start = edit.cursor
            end = edit.cursor
        }
    }

    @Test
    fun `sentences in a row are spaced between words of spaced scripts`() {
        val composer = Composer("")
        composer.partial("Hello")
        assertEquals("Hello", composer.text)
        composer.final("Hello there.")
        composer.partial("How")
        // The partial is shown spaced like its final.
        assertEquals("Hello there. How", composer.text)
        assertEquals("How", composer.text.substring(composer.partial!!.first + 1, composer.partial!!.last + 1))
        composer.partial("How are")
        composer.final("How are you?")
        composer.final("Fine")

        assertEquals("Hello there. How are you? Fine", composer.text)
        assertEquals(composer.text.length, composer.start)
        assertFalse(composer.ended)
    }

    @Test
    fun `punctuation spacing is for live dictation only`() {
        // The sheet of P5 keeps its rule: no space after punctuation.
        assertEquals(" ", LiveDictationEdits.final("Done.", 5, 5, "Next", 100)!!.insertion.take(1))
        assertEquals("Next", DictationInsertion.edit("Done.", 5, 5, "Next", 100)!!.insertion)
        // Full-width marks and a following Chinese sentence join directly.
        assertEquals("我们", LiveDictationEdits.final("好的。", 3, 3, "我们", 100)!!.insertion)
        assertEquals("我们", LiveDictationEdits.final("Done.", 5, 5, "我们", 100)!!.insertion)
    }

    @Test
    fun `chinese sentences join directly and mixed text keeps the spacing rules`() {
        val composer = Composer("")
        composer.final("今天天气很好.")
        composer.final("我们出去走走")
        composer.final("OK")

        assertEquals("今天天气很好.我们出去走走OK", composer.text)

        val mixed = Composer("请打开")
        mixed.final("Wi-Fi")
        mixed.final("settings")
        assertEquals("请打开Wi-Fi settings", mixed.text)
    }

    @Test
    fun `the first utterance replaces the selection and later ones follow the cursor`() {
        val composer = Composer("Hello old world", start = 6, end = 9)
        composer.partial("ne")
        assertEquals("Hello ne world", composer.text)
        composer.final("new")
        assertEquals("Hello new world", composer.text)
        assertEquals(9, composer.start)

        // The user taps elsewhere before the next sentence: it goes there.
        composer.start = 0
        composer.end = 0
        composer.final("Oh")
        assertEquals("Oh Hello new world", composer.text)
    }

    @Test
    fun `an emptied partial and an empty final leave no text behind`() {
        val composer = Composer("Hi")
        composer.partial("there")
        assertEquals("Hi there", composer.text)
        composer.partial("")
        assertEquals("Hi", composer.text)
        assertNull(composer.partial)

        composer.partial("um")
        val edit = composer.final("")
        assertEquals("Hi", composer.text)
        assertEquals("", edit!!.insertion)
        assertNull("No final and no partial: nothing to do", LiveDictationEdits.final("Hi", 2, 2, "  ", 100))
    }

    @Test
    fun `a final that is cut ends the session`() {
        val composer = Composer("x".repeat(16_380))
        composer.partial("abcdefgh")
        // The partial is cut silently: the space and three letters fit.
        assertEquals(16_384, composer.text.length)
        val edit = composer.final("abcdefgh")!!

        assertEquals("x".repeat(16_380) + " abc", composer.text)
        assertTrue(edit.truncated)
        assertTrue(edit.limitReached)
        assertTrue(composer.ended)
    }

    @Test
    fun `a final that exactly fills the input box ends the session without cutting`() {
        val composer = Composer("x".repeat(16_380))
        val edit = composer.final("abc")!!

        assertEquals(16_384, composer.text.length)
        assertFalse(edit.truncated)
        assertTrue(edit.limitReached)

        val roomy = Composer("x".repeat(16_000))
        val fits = roomy.final("abc")!!
        assertFalse(fits.truncated)
        assertFalse(fits.limitReached)
    }

    @Test
    fun `a selection makes room under the limit`() {
        val composer = Composer("a".repeat(16_384), start = 0, end = 10)
        val edit = composer.final("hello")!!

        assertEquals("hello " + "a".repeat(16_374), composer.text)
        assertFalse(edit.truncated)
        // Ten selected characters gave way to six: 16,380, below the limit.
        assertEquals(16_380, composer.text.length)
        assertFalse(edit.limitReached)
    }
}
