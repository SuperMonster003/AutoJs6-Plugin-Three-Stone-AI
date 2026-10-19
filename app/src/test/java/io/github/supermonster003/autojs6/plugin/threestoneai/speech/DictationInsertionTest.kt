package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationInsertionTest {
    private fun apply(current: String, start: Int, end: Int, recognized: String, limit: Int = 16_384): Pair<String, DictationEdit> {
        val edit = requireNotNull(DictationInsertion.edit(current, start, end, recognized, limit))
        return current.replaceRange(edit.start, edit.end, edit.insertion) to edit
    }

    @Test
    fun `inserts at the cursor and moves the cursor after the text`() {
        val (text, edit) = apply("今天天气", 2, 2, "的")

        assertEquals("今天的天气", text)
        assertEquals(3, edit.cursor)
        assertFalse(edit.truncated)
    }

    @Test
    fun `replaces a selection in either direction`() {
        assertEquals("Hello there", apply("Hello world", 6, 11, "there").first)
        assertEquals("Hello there", apply("Hello world", 11, 6, "there").first)
    }

    @Test
    fun `appends when the composer has no cursor`() {
        val (text, edit) = apply("Draft", -1, -1, "more words")

        assertEquals("Draft more words", text)
        assertEquals(text.length, edit.cursor)
    }

    @Test
    fun `adds spaces only between letters or digits of spaced scripts`() {
        assertEquals("one two three", apply("one three", 3, 3, "two").first)
        assertEquals("one two three", apply("one  three", 4, 4, "two").first)
        assertEquals("version 2 ok", apply("version ok", 7, 7, "2").first)
        assertEquals("Привет мир", apply("Привет", 6, 6, "мир").first)
        assertEquals("안녕 하세요", apply("안녕", 2, 2, "하세요").first)
        // Chinese and Japanese are joined directly, also next to Latin text.
        assertEquals("中文English", apply("中文", 2, 2, "English").first)
        assertEquals("abc日本語", apply("abc", 3, 3, "日本語").first)
        assertEquals("これはテスト", apply("これは", 3, 3, "テスト").first)
        // Punctuation and whitespace already separate words.
        assertEquals("Hi, there", apply("Hi, ", 4, 4, "there").first)
        assertEquals("(word)", apply("()", 1, 1, "word").first)
    }

    @Test
    fun `trims the recognized text and ignores blank results`() {
        assertEquals("a b", apply("a", 1, 1, "  b \n").first)
        assertNull(DictationInsertion.edit("a", 1, 1, "   ", 100))
    }

    @Test
    fun `cuts text beyond the limit and reports it`() {
        val (text, edit) = apply("12345", 5, 5, "abcdef", limit = 8)

        assertEquals("12345 ab", text)
        assertTrue(edit.truncated)
        assertEquals(8, text.length)
    }

    @Test
    fun `drops only the trailing separator when that is enough`() {
        val (text, edit) = apply("abcd", 2, 2, "xy", limit = 7)

        assertEquals("ab xycd", text)
        assertFalse(edit.truncated)
    }

    @Test
    fun `never splits a surrogate pair at the limit`() {
        val emoji = String(Character.toChars(0x1F600))
        val (text, edit) = apply("", 0, 0, "a$emoji", limit = 2)

        assertEquals("a", text)
        assertTrue(edit.truncated)
    }

    @Test
    fun `a full composer keeps its text and selection`() {
        val edit = requireNotNull(DictationInsertion.edit("abcd", 1, 3, "zzzzzz", 2))

        assertEquals(edit.start, edit.end)
        assertEquals("", edit.insertion)
        assertTrue(edit.truncated)
        assertEquals("abcd", "abcd".replaceRange(edit.start, edit.end, edit.insertion))
    }

    @Test
    fun `a selection frees room for the replacement`() {
        val (text, edit) = apply("甲乙丙丁", 1, 3, "子丑寅", limit = 5)

        assertEquals("甲子丑寅丁", text)
        assertFalse(edit.truncated)
    }
}
