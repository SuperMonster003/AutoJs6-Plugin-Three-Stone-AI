package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructuredJsonTextTest {
    private val objectOnly = setOf(StructuredJsonShape.OBJECT)
    private val arrayOnly = setOf(StructuredJsonShape.ARRAY)

    @Test
    fun `plain JSON is returned trimmed`() {
        assertEquals("""{"a":1}""", StructuredJsonText.extract("""  {"a":1}  """ + "\n"))
        assertEquals("""[1,2]""", StructuredJsonText.extract("\n[1,2]\n"))
    }

    @Test
    fun `fenced JSON is found with or without a language tag`() {
        val json = """{"answer":"yes","items":[1,2]}"""

        assertEquals(json, StructuredJsonText.extract("```json\n$json\n```"))
        assertEquals(json, StructuredJsonText.extract("```JSON \n$json\n```"))
        assertEquals(json, StructuredJsonText.extract("```\n$json\n```"))
        assertEquals(json, StructuredJsonText.extract("~~~json\n$json\n~~~"))
        assertEquals(json, StructuredJsonText.extract("  ````json\n$json\n````  "))
    }

    @Test
    fun `prose before and after a fence is ignored`() {
        val json = """{"done":true}"""
        val reply = "Sure! Here is the result:\n\n```json\n$json\n```\n\nTell me if you need {more}."

        assertEquals(json, StructuredJsonText.extract(reply))
    }

    @Test
    fun `prose around unfenced JSON is ignored`() {
        val json = """{"done":true}"""

        assertEquals(json, StructuredJsonText.extract("Result: $json (as requested)"))
        assertEquals(json, StructuredJsonText.extract("The format is {done}.\n$json"))
    }

    @Test
    fun `an unclosed fence runs to the end of the text`() {
        val json = """{"done":true}"""

        assertEquals(json, StructuredJsonText.extract("```json\n$json"))
        assertEquals(json, StructuredJsonText.extract("```json\n$json\n``"))
    }

    @Test
    fun `fence and bracket characters inside strings do not end the value`() {
        val json = """{"code":"```js\nlet a = [1, {b: 2}];\n```","note":"say \"}\" here"}"""
        val reply = "```json\n$json\n```"

        assertEquals(json, StructuredJsonText.extract(reply))
    }

    @Test
    fun `a closing fence must use the opening character and be at least as long`() {
        val json = """{"text":"line"}"""
        val reply = "````json\n$json\n```\n````"

        // The inner ``` is not a closing fence for ````, so the body still contains it and the object is found.
        assertEquals(json, StructuredJsonText.extract(reply))
        assertEquals(json, StructuredJsonText.extract("~~~\n$json\n```\n~~~"))
    }

    @Test
    fun `CRLF line endings are accepted`() {
        val json = "{\r\n  \"a\": 1\r\n}"

        assertEquals("{\n  \"a\": 1\n}", StructuredJsonText.extract("```json\r\n$json\r\n```"))
        assertEquals(json, StructuredJsonText.extract("$json\r\n"))
    }

    @Test
    fun `shape filter selects objects or arrays`() {
        val reply = "```json\n[{\"a\":1}]\n```"

        assertEquals("""[{"a":1}]""", StructuredJsonText.extract(reply))
        assertEquals("""[{"a":1}]""", StructuredJsonText.extract(reply, arrayOnly))
        assertEquals("""{"a":1}""", StructuredJsonText.extract(reply, objectOnly))
        assertEquals("[1]", StructuredJsonText.extract("Note: {\"a\":[1]}", arrayOnly))
        assertNull(StructuredJsonText.extract("Plain [words] only", objectOnly))
    }

    @Test
    fun `the first complete strict value wins and invalid candidates are skipped`() {
        val reply = "Draft: {not json}\n{\"a\":1} and {\"b\":2}"

        assertEquals("""{"a":1}""", StructuredJsonText.extract(reply))
        assertEquals("""{"a":1}""", StructuredJsonText.extract("{'a':1}\n{\"a\":1}"))
        assertEquals("""{"a":1}""", StructuredJsonText.extract("{a:1}\n{\"a\":1}"))
    }

    @Test
    fun `a fence without JSON falls back to the rest of the text`() {
        val reply = "```kotlin\nval a = 1\n```\nResult: {\"a\":1}"

        assertEquals("""{"a":1}""", StructuredJsonText.extract(reply))
    }

    @Test
    fun `text without a complete JSON value returns null`() {
        assertNull(StructuredJsonText.extract(""))
        assertNull(StructuredJsonText.extract("   "))
        assertNull(StructuredJsonText.extract("I cannot help with that."))
        assertNull(StructuredJsonText.extract("```json\n{\"a\":1,\"b\":"))
        assertNull(StructuredJsonText.extract("```\n```"))
        assertNull(StructuredJsonText.extract("{\"a\":1]"))
        assertNull(StructuredJsonText.extract("\"just a string\""))
        assertNull(StructuredJsonText.extract("42"))
    }

    @Test
    fun `deeply nested or very noisy text is bounded`() {
        val deep = "[".repeat(100) + "]".repeat(100)
        val noisy = "{x} ".repeat(1_000) + """{"a":1}"""

        assertNull(StructuredJsonText.extract("Result: $deep"))
        assertNull(StructuredJsonText.extract(noisy))
    }
}
