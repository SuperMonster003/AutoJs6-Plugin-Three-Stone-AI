package io.github.supermonster003.autojs6.plugin.ondeviceai.backend

import io.github.supermonster003.autojs6.plugin.ondeviceai.provider.StreamingOutputBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationBackendAbstractionTest {
    @Test
    fun fakeBackendExercisesStreamingWithoutLoadingNativeModel() {
        val output = StreamingOutputBuffer(streaming = true, maximumOutputBytes = 64)
        val backend: GenerationBackend = FakeBackend(listOf("hello ", "world"))
        backend.start(
            GenerationRequest(
                history = emptyList(),
                prompt = GenerationMessage(GenerationRole.USER, listOf("prompt")),
                maximumOutputTokens = null,
                samplingOptions = null,
            ),
            object : GenerationListener {
                override fun onTextDelta(text: String) {
                    assertFalse(output.append(text))
                }

                override fun onCompleted() {
                    output.markBackendDone()
                }

                override fun onFailed(error: Throwable) {
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

    private class FakeBackend(private val deltas: List<String>) : GenerationBackend {
        var closed = false
            private set

        override fun start(request: GenerationRequest, listener: GenerationListener) {
            deltas.forEach(listener::onTextDelta)
            listener.onCompleted()
        }

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }
}
