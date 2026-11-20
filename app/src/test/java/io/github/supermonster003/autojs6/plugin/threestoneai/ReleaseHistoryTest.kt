package io.github.supermonster003.autojs6.plugin.threestoneai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.util.Locale

/** The release history page (plugin-center P21.9, `AUTOJS6_PLUGIN_RELEASE_HISTORY_AGENTS.md`). */
class ReleaseHistoryTest {
    private fun first(tag: String) = ReleaseHistoryAssetPolicy.candidatesFor(Locale.forLanguageTag(tag)).first()

    @Test fun mapsChineseLocalesAndFallsBackToEnglish() {
        assertEquals("doc/CHANGELOG-zh-Hant-HK.md", first("zh-HK"))
        assertEquals("doc/CHANGELOG-zh-Hant-HK.md", first("zh-Hant-MO"))
        assertEquals("doc/CHANGELOG-zh-Hant-TW.md", first("zh-TW"))
        assertEquals("doc/CHANGELOG-zh-Hant-TW.md", first("zh-Hant"))
        assertEquals("doc/CHANGELOG-zh-Hans.md", first("zh-Hans-CN"))
        assertEquals("doc/CHANGELOG-zh-Hans.md", first("zh-Hans-HK"))
        assertEquals("doc/CHANGELOG-zh-Hans.md", first("zh"))
        assertEquals(listOf("doc/CHANGELOG-ar.md", "doc/CHANGELOG-en.md"), ReleaseHistoryAssetPolicy.candidatesFor(Locale.forLanguageTag("ar-EG")))
        assertEquals(listOf("doc/CHANGELOG-en.md"), ReleaseHistoryAssetPolicy.candidatesFor(Locale.forLanguageTag("de-DE")))
        assertEquals(listOf("doc/CHANGELOG-en.md"), ReleaseHistoryAssetPolicy.candidatesFor(Locale.ENGLISH))
    }

    /** A missing, blank or unparsable localized changelog falls back to English; nothing usable gives the empty state. */
    @Test fun skipsMissingBlankOrUnparsableLocalizedAssetsAndReportsCompleteFailure() {
        val candidates = ReleaseHistoryAssetPolicy.candidatesFor(Locale.FRANCE)
        val english = """
            ## v1.0.0

            ###### 2026/10/04

            * `Feature` English note
        """.trimIndent()
        fun load(french: String?) = loadReleaseHistory(candidates) { path ->
            when {
                path.endsWith("-en.md") -> english
                french == null -> throw FileNotFoundException(path)
                else -> french
            }
        }
        assertEquals("English note", load(null).single().items.single().text)
        assertEquals("English note", load(" ").single().items.single().text)
        assertEquals("English note", load("### Historique").single().items.single().text)
        assertEquals("Note", load("## v1.0.0" + System.lineSeparator() + "* `Nouveauté` Note").single().items.single().text)
        assertTrue(loadReleaseHistory(candidates) { " " }.isEmpty())
        assertTrue(loadReleaseHistory(candidates) { throw FileNotFoundException(it) }.isEmpty())
    }
}

class ReleaseHistoryParserTest {
    @Test fun parsesTheGeneratedChangelogDialect() {
        val markdown = """
            ******

            ### Release History

            ******

            ## v1.7.2

            ###### 2026/11/20

            * `Hint` First note
            * `Improvement` Second note

            ## v1.7.1

            ###### 2026/11/14

            * `Feature` Third note
            * Untagged note
        """.trimIndent()
        val entries = ReleaseHistoryParser.parse(markdown)
        assertEquals(listOf("v1.7.2", "v1.7.1"), entries.map { it.version })
        assertEquals("2026/11/20", entries[0].date)
        assertEquals(listOf("Hint" to "First note", "Improvement" to "Second note"), entries[0].items.map { it.tag to it.text })
        assertEquals(listOf("Feature" to "Third note", null to "Untagged note"), entries[1].items.map { it.tag to it.text })
    }

    @Test fun parsesTheSingleHashHeadingToo() {
        val entries = ReleaseHistoryParser.parse(
            """
                # v1.0.0

                * `Feature` Note
            """.trimIndent(),
        )
        assertEquals("v1.0.0", entries.single().version)
        assertEquals(1, entries.single().items.size)
    }

    /** The page names each category once and lists its items beneath; the collapsed summary counts them. */
    @Test fun groupsItemsByCategoryInTheOrderTheyFirstAppear() {
        val entry = ReleaseHistoryParser.parse(
            """
                ## v1.2.0

                * `Hint` A
                * `Added` B
                * `Added` C
                * `Fixed` D
                * `Added` E
                * Untagged F
            """.trimIndent(),
        ).single()
        assertEquals(
            listOf("Hint" to listOf("A"), "Added" to listOf("B", "C", "E"), "Fixed" to listOf("D"), null to listOf("Untagged F")),
            entry.groups.map { it.tag to it.items },
        )
        assertEquals(listOf("Hint" to 1, "Added" to 3, "Fixed" to 1), entry.categoryCounts)
    }

    @Test fun ignoresSectionTitlesThatAreNotVersions() {
        assertTrue(ReleaseHistoryParser.parse("### Release History").isEmpty())
    }

    /** Every bundled changelog parses into releases, newest first, each with its date and at least one item. */
    @Test fun theBundledChangelogsParse() {
        val directory = listOf("src/main/assets/doc", "app/src/main/assets/doc").map { java.io.File(it) }.first { it.isDirectory }
        val names = listOf("ar", "en", "es", "fr", "ja", "ko", "ru", "zh-Hans", "zh-Hant-HK", "zh-Hant-TW").map { "CHANGELOG-$it.md" }
        // The page reads these ten (ReleaseHistoryAssetPolicy); the aliases beside them are for older readers.
        val files = names.map { java.io.File(directory, it) }
        files.forEach { assertTrue(it.name, it.isFile) }
        files.forEach { file ->
            val entries = ReleaseHistoryParser.parse(file.readText(Charsets.UTF_8))
            assertTrue(file.name, entries.size > 10)
            assertEquals(file.name, "v1.0.0", entries.last().version)
            entries.forEach { entry ->
                assertTrue("${file.name} ${entry.version}", entry.date != null && entry.items.isNotEmpty())
            }
        }
    }

    // Q160 A: markdown links in a release note show as link text.

    @Test fun aPlainLinkBecomesItsText() {
        val inline = ReleaseHistoryInlineParser.parse("See the [AutoJs6 documentation](https://docs.autojs6.com/#/ai) for details")
        assertEquals("See the AutoJs6 documentation for details", inline.text)
        assertEquals(listOf(ReleaseHistoryLink(8, 29, "https://docs.autojs6.com/#/ai")), inline.links)
        assertEquals("AutoJs6 documentation", inline.text.substring(inline.links[0].start, inline.links[0].end))
    }

    @Test fun theUnderscoresAndBackticksAroundALinkGo() {
        val inline = ReleaseHistoryInlineParser.parse("Crash on Android 7.x _[`issue #575`](https://github.com/SuperMonster003/AutoJs6/issues/575)_")
        assertEquals("Crash on Android 7.x issue #575", inline.text)
        assertEquals(listOf(ReleaseHistoryLink(21, 31, "https://github.com/SuperMonster003/AutoJs6/issues/575")), inline.links)
        assertFalse(inline.text.contains('_') || inline.text.contains('`'))
        // An underscore without its pair stays.
        assertEquals("An unpaired _underscore stays", ReleaseHistoryInlineParser.parse("An unpaired _[underscore](https://example.com) stays").text)
    }

    @Test fun everyLinkOfALineIsKept() {
        val inline = ReleaseHistoryInlineParser.parse(
            "Fixes [#432](https://github.com/a/b/issues/432), _[`#463`](https://github.com/a/b/issues/463)_ and [#520](http://example.com/520).",
        )
        assertEquals("Fixes #432, #463 and #520.", inline.text)
        assertEquals(listOf("#432", "#463", "#520"), inline.links.map { inline.text.substring(it.start, it.end) })
        assertEquals(
            listOf("https://github.com/a/b/issues/432", "https://github.com/a/b/issues/463", "http://example.com/520"),
            inline.links.map { it.url },
        )
    }

    @Test fun aLineWithoutALinkStaysAsItIs() {
        listOf(
            "Replies stream faster in the chat",
            "Keeps `code`, _italics_, [brackets] and (parentheses)",
            "A [relative link](docs/README.md) is not a link",
        ).forEach { line ->
            val inline = ReleaseHistoryInlineParser.parse(line)
            assertSame(line, inline.text)
            assertTrue(inline.links.isEmpty())
        }
    }
}
