package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import io.github.supermonster003.autojs6.plugin.ondeviceai.provider.StreamingOutputBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationBackendAbstractionTest {
    @Test
    fun fakeBackendExercisesStreamingWithoutLoadingNativeModel() {
        val output = StreamingOutputBuffer(streaming = true, maximumOutputBytes = 64)
        val expectedStatistics = GenerationStatistics(inputTokens = 8L, outputTokens = 2L, durationMillis = 125L)
        val backend: GenerationBackend = FakeBackend(listOf("hello ", "world"), expectedStatistics)
        backend.start(
            GenerationRequest(
                history = emptyList(),
                prompt = GenerationMessage(GenerationRole.USER, listOf("prompt")),
                maximumOutputTokens = null,
                samplingOptions = null,
                reportUsage = true,
            ),
            object : GenerationListener {
                override fun onTextDelta(text: String) {
                    assertFalse(output.append(text))
                }

                override fun onCompleted(statistics: GenerationStatistics?) {
                    assertEquals(expectedStatistics, statistics)
                    assertEquals(10L, statistics?.totalTokens)
                    output.markBackendDone()
                }

                override fun onFailed(error: Throwable, statistics: GenerationStatistics?) {
                    throw AssertionError(error)
                }
            },
        )
        output.grantCredits(2)
        assertEquals("hello ", output.takeCreditedChunk()?.text)
        assertEquals("world", output.takeCreditedChunk()?.text)
        assertTrue(output.isReadyForCompletion())
        assertEquals("hello world", output.snapshot().text)
        backend.close()
        assertTrue((backend as FakeBackend).closed)
    }

    @Test
    fun generationStatisticsRejectInvalidCountersAndOverflow() {
        assertThrows(IllegalArgumentException::class.java) { GenerationStatistics(-1L, 0L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { GenerationStatistics(0L, -1L, 0L) }
        assertThrows(IllegalArgumentException::class.java) { GenerationStatistics(0L, 0L, -1L) }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationStatistics(Long.MAX_VALUE, 1L, 0L)
        }
    }

    @Test
    fun persistentBackendReceivesOnlyTheNewPromptOnLaterTurns() {
        val backend = RecordingPersistentBackend()
        val listener = object : GenerationListener {
            override fun onTextDelta(text: String) = Unit
            override fun onCompleted(statistics: GenerationStatistics?) = Unit
            override fun onFailed(error: Throwable, statistics: GenerationStatistics?) =
                throw AssertionError(error)
        }
        val initial = GenerationRequest(
            history = listOf(GenerationMessage(GenerationRole.SYSTEM, listOf("Be concise"))),
            prompt = GenerationMessage(GenerationRole.USER, listOf("First")),
            maximumOutputTokens = 64,
            samplingOptions = GenerationSamplingOptions(0.5, 8, 0.9),
            reportUsage = true,
        )
        val next = initial.copy(
            history = emptyList(),
            prompt = GenerationMessage(GenerationRole.USER, listOf("Second only")),
        )

        backend.start(initial, listener)
        backend.continueGeneration(next, listener)

        assertEquals(initial, backend.initial)
        assertEquals(next, backend.next)
        assertTrue(requireNotNull(backend.next).history.isEmpty())
        assertEquals(listOf("Second only"), requireNotNull(backend.next).prompt.textParts)
    }

    private class FakeBackend(
        private val deltas: List<String>,
        private val statistics: GenerationStatistics,
    ) : GenerationBackend {
        var closed = false
            private set

        override fun start(request: GenerationRequest, listener: GenerationListener) {
            deltas.forEach(listener::onTextDelta)
            listener.onCompleted(statistics)
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }

    private class RecordingPersistentBackend : GenerationBackend {
        var initial: GenerationRequest? = null
        var next: GenerationRequest? = null

        override fun start(request: GenerationRequest, listener: GenerationListener) {
            initial = request
            listener.onCompleted(null)
        }

        override fun continueGeneration(request: GenerationRequest, listener: GenerationListener) {
            next = request
            listener.onCompleted(null)
        }

        override fun cancel() = Unit
        override fun close() = Unit
    }
}
