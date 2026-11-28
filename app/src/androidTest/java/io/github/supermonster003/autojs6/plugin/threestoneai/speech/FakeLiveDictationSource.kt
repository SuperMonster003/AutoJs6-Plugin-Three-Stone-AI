package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A scripted live dictation source for instrumentation (P6-2): it never records. Each session runs the same
 * [LiveDictationEventGate] as 3-Macaw Speech's, so ends, single utterances and late events behave alike; the test
 * drives the events, all of which reach the chat on the main thread.
 */
internal class FakeLiveDictationSource : LiveDictationSource {
    private val main = Handler(Looper.getMainLooper())
    val sessions = CopyOnWriteArrayList<Session>()

    /** The next open fails with this error code, as `openSession` would throw it. */
    @Volatile
    var openFailure: String? = null

    /** What a finish flushes as the last final; null flushes nothing. */
    @Volatile
    var finishFinal: String? = null

    val last: Session get() = sessions.last()

    @Volatile
    private var rounds: List<Heard> = emptyList()

    @Volatile
    private var nextRound = 0

    /**
     * Answers each voice conversation round (a [LiveDictationMode.SINGLE_UTTERANCE] session) by itself with the next of
     * [steps], the last one repeating (P6-3): ready, a partial and the final; 8 s of silence; a cancel by 3-Macaw Speech;
     * or an engine failure. An [openFailure] answers its round instead. Empty: the test drives the sessions.
     */
    fun scriptRounds(vararg steps: Heard) {
        rounds = steps.toList()
        nextRound = 0
    }

    override fun open(mode: LiveDictationMode, listener: LiveDictationListener): LiveDictationSession {
        val session = Session(mode, listener)
        sessions += session
        val failure = openFailure
        val script = rounds
        when {
            failure != null -> {
                openFailure = null
                main.post { session.gate.fail(failure) }
            }
            mode == LiveDictationMode.SINGLE_UTTERANCE && script.isNotEmpty() -> {
                session.play(script[minOf(nextRound, script.lastIndex)])
                nextRound++
            }
        }
        return session
    }

    /** Delivers [step] on the main thread; whatever arrives after the end (a local cancel) is dropped by the gate. */
    private fun Session.play(step: Heard) {
        main.postDelayed({ gate.ready() }, STEP_MILLIS)
        when (step) {
            is Heard.Said -> {
                main.postDelayed({ gate.partial(step.text.substringBefore(' ')) }, 2 * STEP_MILLIS)
                // The gate finishes the session at this final, and the finish ends it (flushOnFinish).
                main.postDelayed({ gate.final(step.text) }, 3 * STEP_MILLIS)
            }
            Heard.NoSpeech -> main.postDelayed({ gate.end(SpeechContract.REASON_TIMEOUT) }, 2 * STEP_MILLIS)
            Heard.Canceled -> main.postDelayed({ gate.end(SpeechContract.REASON_CANCELLED) }, 2 * STEP_MILLIS)
            Heard.Failure -> main.postDelayed(
                {
                    gate.error(SpeechErrorCodes.ENGINE_FAILED)
                    gate.end(SpeechContract.REASON_ERROR)
                },
                2 * STEP_MILLIS,
            )
        }
    }

    inner class Session(val mode: LiveDictationMode, listener: LiveDictationListener) : LiveDictationSession {
        @Volatile
        var finishes = 0
            private set

        @Volatile
        var cancels = 0
            private set

        val gate: LiveDictationEventGate = LiveDictationEventGate(mode, listener) { flushOnFinish() }

        private fun flushOnFinish() {
            finishes++
            val flushed = finishFinal
            main.post {
                if (flushed != null) gate.final(flushed)
                gate.end(SpeechContract.REASON_STOPPED)
            }
        }

        val ended: Boolean get() = gate.ended

        fun ready() = onMain { gate.ready() }
        fun partial(text: String) = onMain { gate.partial(text) }
        fun final(text: String) = onMain { gate.final(text) }
        fun error(code: String) = onMain { gate.error(code) }
        fun end(reason: String) = onMain { gate.end(reason) }
        fun fail(code: String) = onMain { gate.fail(code) }

        override fun finish() {
            gate.requestFinish()
        }

        override fun cancel() {
            cancels++
            gate.cancel()
        }

        private fun onMain(block: () -> Unit) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        }
    }

    private companion object {
        const val STEP_MILLIS = 20L
    }
}
