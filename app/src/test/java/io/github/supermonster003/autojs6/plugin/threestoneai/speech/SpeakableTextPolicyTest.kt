package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpeakableTextPolicyTest {
    private val hints = SpeakableHints(codeBlock = "[code]", table = "[table]")

    private fun read(markdown: String, maximumLength: Int = SpeakableTextPolicy.MAXIMUM_SENTENCE_LENGTH) =
        SpeakableTextPolicy.sentences(markdown, hints, maximumLength)

    @Test
    fun `markers of headings emphasis lists and quotes are not read`() {
        assertEquals(
            listOf("Title", "bold and italic", "item one", "first step", "quoted", "struck"),
            read("# Title\n**bold** and *italic*\n- item one\n1. first step\n> quoted\n~~struck~~"),
        )
    }

    @Test
    fun `links read their text and bare urls only their host`() {
        assertEquals(listOf("See the docs now."), read("See [the docs](https://example.com/a) now."))
        assertEquals(
            listOf("Visit www.example.com.", "Then sign in at example.org."),
            read("Visit https://www.example.com/path?q=1. Then sign in at http://user@example.org:8080/x."),
        )
        assertEquals(listOf("Link: example.com"), read("Link: [https://example.com/long/path](https://example.com/long/path)"))
    }

    @Test
    fun `images and rules are skipped`() {
        assertEquals(listOf("Look here."), read("Look ![diagram](x.png) here."))
        assertEquals(listOf("Above", "Below"), read("Above\n---\nBelow"))
    }

    @Test
    fun `code blocks and tables become one hint each`() {
        assertEquals(
            listOf("Before.", "[code]", "After."),
            read("Before.\n```kotlin\nval a = 1. Not read.\n```\nAfter."),
        )
        assertEquals(
            listOf("Intro", "[table]", "End"),
            read("Intro\n| a | b |\n|---|:---:|\n| 1. x | 2 |\n| 3 | 4 |\nEnd"),
        )
        assertEquals(listOf("Text", "[code]"), read("Text\n~~~\nunclosed code. Still code"))
    }

    @Test
    fun `inline code and math read their rendered text`() {
        assertEquals(listOf("Run npm install now."), read("Run `npm install` now."))
        val math = read("Area is \$\\pi r^2\$ here.").single()
        assertTrue(math, math.startsWith("Area is ") && "π" in math && "$" !in math)
    }

    @Test
    fun `chinese english and japanese sentences split at their own marks`() {
        assertEquals(
            listOf("你好。", "今天天气很好！", "Hello world.", "Is it?", "これはテストです。", "本当？"),
            read("你好。今天天气很好！Hello world. Is it? これはテストです。本当？"),
        )
        assertEquals(
            listOf("他说: “好的。”", "然后走了……", "结束"),
            read("他说: “好的。”然后走了…… 结束"),
        )
        assertEquals(listOf("Really?!", "Yes."), read("Really?! Yes."))
        assertEquals(listOf("كيف حالك؟", "بخير."), read("كيف حالك؟ بخير."))
    }

    @Test
    fun `decimals and abbreviations do not end a sentence`() {
        assertEquals(
            listOf("Pi is 3.14 approx. Mr. Smith arrived.", "Use e.g. this one.", "J. R. wrote it.", "Done."),
            read("Pi is 3.14 approx. Mr. Smith arrived. Use e.g. this one. J. R. wrote it. Done."),
        )
        assertEquals(listOf("Version 2.0 is out.", "Next."), read("Version 2.0 is out. Next."))
    }

    @Test
    fun `lines end sentences and punctuation alone is not read`() {
        assertEquals(listOf("No period", "Second line"), read("No period\nSecond line"))
        assertEquals(emptyList<String>(), read("...\n- \n***\n\n"))
    }

    @Test
    fun `long sentences are split at commas or spaces within the limit`() {
        val long = (1..400).joinToString(", ") { "word$it" } + "."
        val parts = read(long, maximumLength = 100)

        assertTrue(parts.size > 1)
        assertTrue(parts.all { part -> part.length <= 100 })
        assertEquals(long.filterNot(Char::isWhitespace), parts.joinToString("").filterNot(Char::isWhitespace))
        val unbroken = "x".repeat(250)
        assertEquals(listOf("x".repeat(100), "x".repeat(100), "x".repeat(50)), read(unbroken, maximumLength = 100))
    }

    @Test
    fun `streaming reads finished sentences before the line ends`() {
        val segmenter = SpeakableTextSegmenter(hints)

        assertEquals(listOf("Hello world."), segmenter.update("Hello world. How"))
        assertEquals(emptyList<String>(), segmenter.update("Hello world. How are"))
        assertEquals(listOf("How are you?"), segmenter.update("Hello world. How are you? Fi"))
        assertEquals(listOf("Fine"), segmenter.finish("Hello world. How are you? Fine"))
    }

    @Test
    fun `streaming waits where markdown can still change`() {
        val table = SpeakableTextSegmenter(hints)
        assertEquals(emptyList<String>(), table.update("| a | b |\n"))
        assertEquals(emptyList<String>(), table.update("| a | b |\n|--"))
        assertEquals(listOf("[table]"), table.update("| a | b |\n|---|---|\n"))

        val notTable = SpeakableTextSegmenter(hints)
        assertEquals(emptyList<String>(), notTable.update("| a | b |\n"))
        assertEquals(listOf("| a | b |"), notTable.update("| a | b |\nplain"))

        val fence = SpeakableTextSegmenter(hints)
        assertEquals(emptyList<String>(), fence.update("```\ncode. More code.\n"))
        assertEquals(listOf("[code]"), fence.update("```\ncode. More code.\n```\n"))

        val link = SpeakableTextSegmenter(hints)
        assertEquals(listOf("First."), link.update("First. See [the guide. Really"))
        assertEquals(listOf("See the guide.", "Really."), link.finish("First. See [the guide. Really](x)."))
    }

    @Test
    fun `streamed sentences equal the one-shot result for any chunking`() {
        for (sample in SAMPLES) {
            val expected = read(sample)
            val chunkings = buildList {
                add(List(sample.length) { 1 })
                add(sample.split('\n').map { it.length + 1 })
                repeat(12) { seed ->
                    val random = Random(seed)
                    add(List(sample.length) { random.nextInt(1, 9) })
                }
            }
            for (sizes in chunkings) {
                val segmenter = SpeakableTextSegmenter(hints)
                val streamed = ArrayList<String>()
                var end = 0
                for (size in sizes) {
                    end = (end + size).coerceAtMost(sample.length)
                    streamed += segmenter.update(sample.substring(0, end))
                    if (end == sample.length) break
                }
                streamed += segmenter.finish(sample)
                assertEquals("Chunked reading of: $sample", expected, streamed)
            }
        }
    }

    private companion object {
        val SAMPLES = listOf(
            "Hello world. How are you? Fine!\nNext line. 3.14 is pi. Mr. Smith left.",
            "# Plan\n1. First step. Then more.\n2) Second step\n- bullet. two\n> quote. end",
            "See [the guide. Really](https://example.com/a.b). Next sentence. Visit https://x.org/p. Done",
            "It costs \$5. Then \$x^2\$ grows. End.",
            "Wow![img](a.png) yes. Next. ![only image](b.png)\nAfter image.",
            "Intro.\n| a | b |\n|---|---|\n| 1. one | 2 |\nAfter table. More.",
            "Not | a table\n|---|---|\nstill text. Ok.",
            "| lone | row |\nplain text. More.",
            "Code:\n```python\nprint('a. b')\n```\nAfter code. Done.\n```\nunclosed",
            "~~~\nx\n~~~\n~~~\ny",
            "**bold. text** and *ital. ic* plus `code. more` text. End `open",
            "Escaped \\[not a link]. Next. \\\$ dollars. Fin",
            "中文句子。英文 sentence here. 混合！日本語です。そうですか？\r\nWindows line. End\r\n",
            "---\n***\n- \n... \nOnly. Real.",
            "他说: “好的。”然后走了…… 结束",
            "Trailing spaces.   \n\n\nBlank lines above. And below.\n\n",
            "Table at end\n| x | y |\n|:-:|--:|",
            "Table header at end\n| x | y |",
        )
    }
}
