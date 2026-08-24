package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAiSseReaderTest {
    @Test
    fun readerHandlesBomLineEndingsCommentsAndMultilineData() {
        val source = Buffer().writeUtf8(
            "\uFEFF: keepalive\r" +
                "event: message\r\n" +
                "data: {\"first\":1,\r\n" +
                "data: \"second\":2}\r\n\r\n" +
                "data: [DONE]\n\n",
        )

        OnlineAiSseReader(source).use { reader ->
            val first = reader.readEvent() ?: error("Missing first event")
            assertEquals("message", first.event)
            assertEquals("{\"first\":1,\n\"second\":2}", first.data)
            assertFalse(first.isDone)

            val done = reader.readEvent() ?: error("Missing terminal event")
            assertTrue(done.isDone)
            assertEquals("[DONE]", done.data)
            assertNull(reader.readEvent())
        }
    }

    @Test
    fun readerDispatchesFinalUnterminatedEvent() {
        OnlineAiSseReader(Buffer().writeUtf8("data: final")).use { reader ->
            assertEquals("final", reader.readEvent()?.data)
            assertNull(reader.readEvent())
        }
    }

    @Test
    fun readerEnforcesPerEventAndTotalByteLimits() {
        val eventFailure = assertThrows(OnlineAiFailureException::class.java) {
            OnlineAiSseReader(
                source = Buffer().writeUtf8("data: too-long\n\n"),
                maximumEventBytes = 8L,
                maximumTotalBytes = 64L,
            ).use { it.readEvent() }
        }
        assertEquals(OnlineAiFailureReason.RESPONSE_TOO_LARGE, eventFailure.reason)

        val totalFailure = assertThrows(OnlineAiFailureException::class.java) {
            OnlineAiSseReader(
                source = Buffer().writeUtf8("data:a\n\ndata:b\n\n"),
                maximumEventBytes = 12L,
                maximumTotalBytes = 12L,
            ).use { reader ->
                reader.readEvent()
                reader.readEvent()
            }
        }
        assertEquals(OnlineAiFailureReason.RESPONSE_TOO_LARGE, totalFailure.reason)
    }
}
