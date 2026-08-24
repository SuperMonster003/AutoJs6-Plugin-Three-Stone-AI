package io.github.supermonster003.autojs6.plugin.threestoneai.provider

import org.autojs.plugin.ai.provider.api.AiFinishReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingOutputBufferTest {
    @Test
    fun gatesUtf8ChunksOnCreditsAndCompletesAfterDrain() {
        val buffer = StreamingOutputBuffer(
            streaming = true,
            maximumOutputBytes = 32,
            maximumCredits = 2,
            maximumChunkBytes = 5,
        )
        assertFalse(buffer.append("ab你好cd"))
        assertNull(buffer.takeCreditedChunk())
        buffer.grantCredits(2)
        val first = buffer.takeCreditedChunk()!!
        val second = buffer.takeCreditedChunk()!!
        assertEquals(0L, first.sequence)
        assertEquals(1L, second.sequence)
        assertTrue(Utf8Text.byteCount(first.text) <= 5)
        assertTrue(Utf8Text.byteCount(second.text) <= 5)
        buffer.markBackendDone()
        assertTrue(buffer.isReadyForCompletion())
        val snapshot = buffer.snapshot()
        assertEquals("ab你好cd", snapshot.text)
        assertEquals(2L, snapshot.chunkCount)
        assertEquals(AiFinishReason.STOP, snapshot.finishReason)
    }

    @Test
    fun truncatesAtCodePointBoundaryAndMarksLength() {
        val buffer = StreamingOutputBuffer(streaming = false, maximumOutputBytes = 5)
        assertTrue(buffer.append("a你好"))
        buffer.markBackendDone()
        val snapshot = buffer.snapshot()
        assertEquals("a你", snapshot.text)
        assertEquals(4L, snapshot.utf8Bytes)
        assertEquals(AiFinishReason.LENGTH, snapshot.finishReason)
    }

    @Test
    fun rejectsMalformedUtf16InsteadOfProducingMismatchedUtf8Aggregates() {
        val buffer = StreamingOutputBuffer(streaming = true, maximumOutputBytes = 16)
        assertThrows(IllegalArgumentException::class.java) { buffer.append("\uD800") }
        assertThrows(IllegalArgumentException::class.java) { buffer.append("\uDC00") }
    }
}
