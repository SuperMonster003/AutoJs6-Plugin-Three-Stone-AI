package io.github.supermonster003.autojs6.plugin.threestoneai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandPolicyTest {
    @Test
    fun `missing or blank text only opens the chat`() {
        assertNull(VoiceCommandPolicy.cleanText(null))
        assertNull(VoiceCommandPolicy.cleanText(""))
        assertNull(VoiceCommandPolicy.cleanText(" \n\t "))
        assertEquals(
            VoiceCommandDisposition.OPEN,
            VoiceCommandPolicy.disposition(VoiceCommand(null, null, 0L), targetReady = true, lastImmediateSendAtMillis = null),
        )
    }

    @Test
    fun `control characters become spaces and line breaks stay`() {
        assertEquals("a b\nc\nd e", VoiceCommandPolicy.cleanText("a\u0000b\r\nc\rd\u0007e"))
        assertEquals("x y", VoiceCommandPolicy.cleanText("  x\u2028y  "))
        // A lone surrogate is replaced; a pair is kept.
        val pair = String(Character.toChars(0x1F600))
        assertEquals("a $pair", VoiceCommandPolicy.cleanText("a\uD800$pair"))
    }

    @Test
    fun `text is cut to the composer limit at a code point`() {
        assertEquals("abcde", VoiceCommandPolicy.cleanText("abcdefgh", maximumLength = 5))
        val pair = String(Character.toChars(0x1F600))
        assertEquals("abcd", VoiceCommandPolicy.cleanText("abcd$pair", maximumLength = 5))
        assertEquals(16_384, VoiceCommandPolicy.cleanText("x".repeat(20_000))?.length)
    }

    @Test
    fun `language tags are validated and normalized`() {
        assertEquals("en-US", VoiceCommandPolicy.languageTag("en-US"))
        assertEquals("zh-CN", VoiceCommandPolicy.languageTag(" zh-cn "))
        assertEquals("zh-Hans-CN", VoiceCommandPolicy.languageTag("zh-Hans-CN"))
        assertNull(VoiceCommandPolicy.languageTag(null))
        assertNull(VoiceCommandPolicy.languageTag(""))
        assertNull(VoiceCommandPolicy.languageTag("und"))
        assertNull(VoiceCommandPolicy.languageTag("en_US"))
        assertNull(VoiceCommandPolicy.languageTag("e"))
        assertNull(VoiceCommandPolicy.languageTag("en-US; DROP"))
        assertNull(VoiceCommandPolicy.languageTag("a".repeat(8) + "-b".repeat(20)))
    }

    @Test
    fun `immediate sends are at least ten seconds apart`() {
        assertTrue(VoiceCommandPolicy.sendAllowed(5_000L, null))
        assertFalse(VoiceCommandPolicy.sendAllowed(15_000L, 6_000L))
        assertTrue(VoiceCommandPolicy.sendAllowed(16_000L, 6_000L))
        // Elapsed time restarts at boot: an earlier send "after" now was before a restart.
        assertTrue(VoiceCommandPolicy.sendAllowed(1_000L, 900_000L))
    }

    @Test
    fun `dispositions follow target and rate limit`() {
        val command = VoiceCommand("Hello", "en-US", 20_000L)

        assertEquals(VoiceCommandDisposition.SEND, VoiceCommandPolicy.disposition(command, true, null))
        assertEquals(VoiceCommandDisposition.SEND, VoiceCommandPolicy.disposition(command, true, 10_000L))
        assertEquals(VoiceCommandDisposition.PREFILL_RATE_LIMITED, VoiceCommandPolicy.disposition(command, true, 10_001L))
        assertEquals(VoiceCommandDisposition.PREFILL_NO_TARGET, VoiceCommandPolicy.disposition(command, false, null))
        assertEquals(VoiceCommandDisposition.PREFILL_NO_TARGET, VoiceCommandPolicy.disposition(command, false, 19_000L))
    }

    @Test
    fun `the inbox drops stale commands and keeps the newest few`() {
        VoiceCommandInbox.takeAll(nowMillis = 0L)
        repeat(6) { index -> VoiceCommandInbox.post(VoiceCommand("c$index", null, 1_000L + index)) }
        VoiceCommandInbox.post(VoiceCommand("old", null, 0L))

        val taken = VoiceCommandInbox.takeAll(nowMillis = 61_000L)

        assertEquals(listOf("c3", "c4", "c5"), taken.map(VoiceCommand::text))
        assertTrue(VoiceCommandInbox.takeAll(nowMillis = 61_000L).isEmpty())
    }
}
