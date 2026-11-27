package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechOptionKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap P6-2 (D204, D209): session requests, the event gate and what each failure leads to. */
class LiveDictationSessionTest {
    private class Recorder : LiveDictationListener {
        val events = mutableListOf<String>()
        override fun onReady() {
            events += "ready"
        }

        override fun onPartial(text: String) {
            events += "partial:$text"
        }

        override fun onFinal(text: String) {
            events += "final:$text"
        }

        override fun onEnd(end: LiveDictationEnd) {
            events += "end:$end"
        }
    }

    @Test
    fun `continuous dictation listens 15 seconds, a single utterance 8`() {
        val continuous = LiveDictationRequest.options(LiveDictationMode.CONTINUOUS)
        val single = LiveDictationRequest.options(LiveDictationMode.SINGLE_UTTERANCE)

        assertEquals(
            mapOf(
                SpeechContract.KEY_CONTRACT_VERSION to 1,
                SpeechOptionKeys.SOURCE to "mic",
                SpeechOptionKeys.VAD to true,
                SpeechOptionKeys.SILENCE_END_MILLIS to 1_200L,
                SpeechOptionKeys.TIMEOUT_MILLIS to 15_000L,
                SpeechOptionKeys.MAX_DURATION_MILLIS to 300_000L,
                SpeechOptionKeys.PUNCTUATION to true,
            ),
            continuous,
        )
        assertEquals(8_000L, single[SpeechOptionKeys.TIMEOUT_MILLIS])
        assertEquals(continuous - SpeechOptionKeys.TIMEOUT_MILLIS, single - SpeechOptionKeys.TIMEOUT_MILLIS)
        // Version 1 keys only, no language (the model decides) and no version 3 option.
        listOf(SpeechOptionKeys.LANGUAGE, SpeechOptionKeys.BACKGROUND, SpeechOptionKeys.KEEP_LOADED).forEach { key ->
            assertFalse(key, key in continuous)
        }
    }

    @Test
    fun `continuous sessions pass every sentence and end once`() {
        val recorder = Recorder()
        var finishes = 0
        val gate = LiveDictationEventGate(LiveDictationMode.CONTINUOUS, recorder) { finishes++ }

        gate.ready()
        gate.partial("hel")
        gate.final("hello")
        gate.partial("wor")
        gate.final("world")
        assertTrue(gate.requestFinish())
        assertFalse("A second press is ignored", gate.requestFinish())
        gate.final("again")
        gate.end(SpeechContract.REASON_STOPPED)
        gate.end(SpeechContract.REASON_ERROR)
        gate.partial("late")

        assertEquals(1, finishes)
        assertEquals(
            listOf(
                "ready", "partial:hel", "final:hello", "partial:wor", "final:world", "final:again",
                "end:${LiveDictationEnd.Stopped}",
            ),
            recorder.events,
        )
        assertTrue(gate.ended)
    }

    @Test
    fun `a single utterance finishes at its first final and drops later text`() {
        val recorder = Recorder()
        var finishes = 0
        val gate = LiveDictationEventGate(LiveDictationMode.SINGLE_UTTERANCE, recorder) { finishes++ }

        gate.partial("what")
        gate.final("what time is it")
        gate.partial("and")
        gate.final("and tomorrow")
        gate.end(SpeechContract.REASON_STOPPED)

        assertEquals(1, finishes)
        assertEquals(
            listOf("partial:what", "final:what time is it", "end:${LiveDictationEnd.Stopped}"),
            recorder.events,
        )
    }

    @Test
    fun `end reasons map to ends`() {
        fun endOf(mode: LiveDictationMode = LiveDictationMode.CONTINUOUS, block: LiveDictationEventGate.() -> Unit): String {
            val recorder = Recorder()
            LiveDictationEventGate(mode, recorder) {}.block()
            return recorder.events.last()
        }

        assertEquals("end:${LiveDictationEnd.Timeout(heard = false)}", endOf { end(SpeechContract.REASON_TIMEOUT) })
        assertEquals(
            "end:${LiveDictationEnd.Timeout(heard = true)}",
            endOf { final("x"); end(SpeechContract.REASON_TIMEOUT) },
        )
        // 8 s without speech in a voice conversation round is "no speech" for P6-3.
        assertEquals(
            "end:${LiveDictationEnd.Timeout(heard = false)}",
            endOf(LiveDictationMode.SINGLE_UTTERANCE) { ready(); end(SpeechContract.REASON_TIMEOUT) },
        )
        assertEquals("end:${LiveDictationEnd.MaxDuration}", endOf { end(SpeechContract.REASON_MAX_DURATION) })
        assertEquals("end:${LiveDictationEnd.Cancelled}", endOf { end(SpeechContract.REASON_CANCELLED) })
        assertEquals(
            "end:${LiveDictationEnd.Failed(SpeechErrorCodes.ENGINE_FAILED, heard = false)}",
            endOf { error(SpeechErrorCodes.ENGINE_FAILED); end(SpeechContract.REASON_ERROR) },
        )
        // The first error code wins; an error without a code is INTERNAL, as is an unknown reason.
        assertEquals(
            "end:${LiveDictationEnd.Failed(SpeechErrorCodes.MODEL_MISSING, heard = false)}",
            endOf { error(SpeechErrorCodes.MODEL_MISSING); error(SpeechErrorCodes.IO_FAILED); end(SpeechContract.REASON_ERROR) },
        )
        assertEquals(
            "end:${LiveDictationEnd.Failed(SpeechErrorCodes.INTERNAL, heard = false)}",
            endOf { error(null); end("something") },
        )
        assertEquals(
            "end:${LiveDictationEnd.Failed(SpeechErrorCodes.SESSION_CLOSED, heard = true)}",
            endOf { partial("a"); fail(SpeechErrorCodes.SESSION_CLOSED) },
        )
    }

    @Test
    fun `a local cancel ends at once and silences the plugin`() {
        val recorder = Recorder()
        val gate = LiveDictationEventGate(LiveDictationMode.CONTINUOUS, recorder) {}

        gate.partial("half")
        gate.cancel()
        gate.final("half a sentence")
        gate.end(SpeechContract.REASON_CANCELLED)
        gate.cancel()

        assertEquals(listOf("partial:half", "end:${LiveDictationEnd.Cancelled}"), recorder.events)
        assertFalse("No finish after the end", gate.requestFinish())
    }

    @Test
    fun `busy and missing models fall back to the sheet, a denied microphone to the permission screen`() {
        fun recovery(code: String, heard: Boolean = false) =
            LiveDictationRecoveryPolicy.recovery(LiveDictationEnd.Failed(code, heard))

        assertEquals(LiveDictationRecovery.SHEET, recovery(SpeechErrorCodes.SESSION_BUSY))
        assertEquals(LiveDictationRecovery.SHEET, recovery(SpeechErrorCodes.MODEL_MISSING))
        // An unusable service before anything was heard (D208).
        listOf(
            SpeechErrorCodes.SESSION_CLOSED,
            SpeechErrorCodes.PLUGIN_UNAVAILABLE,
            SpeechErrorCodes.PLUGIN_DISABLED,
            SpeechErrorCodes.PLUGIN_INCOMPATIBLE,
            SpeechErrorCodes.FEATURE_UNAVAILABLE,
        ).forEach { code -> assertEquals(code, LiveDictationRecovery.SHEET, recovery(code)) }
        assertEquals(LiveDictationRecovery.MICROPHONE_PERMISSION, recovery(SpeechErrorCodes.PERMISSION_DENIED))
        listOf(
            SpeechErrorCodes.ENGINE_FAILED,
            SpeechErrorCodes.IO_FAILED,
            SpeechErrorCodes.INVALID_OPTIONS,
            SpeechErrorCodes.INTERNAL,
            SpeechErrorCodes.TIMEOUT,
        ).forEach { code -> assertEquals(code, LiveDictationRecovery.REPORT, recovery(code)) }
        // Once something was written, it stays and the failure is only reported.
        SpeechErrorCodes.ALL.forEach { code -> assertEquals(code, LiveDictationRecovery.REPORT, recovery(code, heard = true)) }
    }
}
