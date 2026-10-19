package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading queue, message menu, automatic reading and the status bar (P5-3). */
class ReadAloudStateTest {
    @Test
    fun `reading finishes after the input ended and every utterance completed`() {
        val queue = ReadingQueue()
        queue.begin(7L)
        val first = queue.enqueue(listOf("One.", " ", "Two."))

        assertEquals(listOf("1:7:0", "1:7:1"), first.map(Utterance::id))
        assertFalse(queue.completed(first[0].id))
        assertFalse(queue.endInput())
        assertTrue(queue.active)
        assertTrue(queue.completed(first[1].id))
        assertFalse(queue.active)
        assertNull(queue.messageId)
    }

    @Test
    fun `a reply with nothing to read finishes when its input ends`() {
        val queue = ReadingQueue()
        queue.begin(3L)

        assertTrue(queue.endInput())
        assertEquals(emptyList<Utterance>(), queue.enqueue(listOf("late")))
    }

    @Test
    fun `callbacks from an earlier reading of the same message are ignored`() {
        val queue = ReadingQueue()
        queue.begin(5L)
        val stale = queue.enqueue(listOf("Old."))
        assertEquals(5L, queue.clear())
        queue.begin(5L)
        val fresh = queue.enqueue(listOf("New."))
        queue.endInput()

        assertFalse(stale.single().id == fresh.single().id)
        assertFalse(queue.completed(stale.single().id))
        assertTrue(queue.active)
        assertTrue(queue.completed(fresh.single().id))
    }

    @Test
    fun `menu offers reading only for finished non-empty replies with an engine`() {
        val reply = ReadableReply(messageId = 1L, isAssistant = true, complete = true, text = "Answer")

        assertEquals(ReadAloudMenuItem.READ, ReadAloudPolicy.menuItem(reply, null, engineAvailable = true))
        assertEquals(ReadAloudMenuItem.READ, ReadAloudPolicy.menuItem(reply, 2L, engineAvailable = true))
        assertEquals(ReadAloudMenuItem.STOP, ReadAloudPolicy.menuItem(reply, 1L, engineAvailable = true))
        assertNull(ReadAloudPolicy.menuItem(reply, null, engineAvailable = false))
        assertNull(ReadAloudPolicy.menuItem(reply.copy(text = " \n"), null, engineAvailable = true))
        assertNull(ReadAloudPolicy.menuItem(reply.copy(complete = false), null, engineAvailable = true))
        assertNull(ReadAloudPolicy.menuItem(reply.copy(isAssistant = false), null, engineAvailable = true))
        // A streaming reply that is being read can still be stopped from its menu.
        assertEquals(
            ReadAloudMenuItem.STOP,
            ReadAloudPolicy.menuItem(reply.copy(complete = false), 1L, engineAvailable = true),
        )
    }

    @Test
    fun `new replies are read when asked for and an engine exists`() {
        assertFalse(ReadAloudPolicy.readsNewReply(false, false, false, engineAvailable = true))
        assertTrue(ReadAloudPolicy.readsNewReply(true, false, false, engineAvailable = true))
        assertTrue(ReadAloudPolicy.readsNewReply(false, true, false, engineAvailable = true))
        assertTrue(ReadAloudPolicy.readsNewReply(false, false, true, engineAvailable = true))
        assertFalse(ReadAloudPolicy.readsNewReply(true, true, true, engineAvailable = false))
    }

    @Test
    fun `reading languages put a voice command language before the interface language`() {
        assertEquals(listOf("zh-CN"), ReadAloudPolicy.languageTags("zh-CN", null))
        assertEquals(listOf("en-US", "zh-CN"), ReadAloudPolicy.languageTags("zh-CN", "en-US"))
        assertEquals(listOf("zh-CN"), ReadAloudPolicy.languageTags("zh-CN", "zh-CN"))
    }

    @Test
    fun `status bar shows reading or the conversation phase`() {
        assertNull(VoiceStatusPolicy.status(VoicePhase.IDLE, reading = false))
        assertEquals(
            VoiceStatus(VoiceStatusLabel.READING, VoiceStatusAction.STOP_READING, tapInterrupts = false),
            VoiceStatusPolicy.status(VoicePhase.IDLE, reading = true),
        )
        for (reading in listOf(false, true)) {
            assertEquals(VoiceStatusLabel.LISTENING, VoiceStatusPolicy.status(VoicePhase.LISTENING, reading)?.label)
            assertEquals(VoiceStatusLabel.THINKING, VoiceStatusPolicy.status(VoicePhase.THINKING, reading)?.label)
            val speaking = VoiceStatusPolicy.status(VoicePhase.SPEAKING, reading)
            assertEquals(VoiceStatusLabel.SPEAKING, speaking?.label)
            assertEquals(true, speaking?.tapInterrupts)
            for (phase in listOf(VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING)) {
                assertEquals(VoiceStatusAction.EXIT_CONVERSATION, VoiceStatusPolicy.status(phase, reading)?.action)
            }
        }
    }
}
