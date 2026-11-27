package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.speech.api.SpeechContract
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

    override fun open(mode: LiveDictationMode, listener: LiveDictationListener): LiveDictationSession {
        val session = Session(mode, listener)
        sessions += session
        openFailure?.let { code ->
            openFailure = null
            main.post { session.gate.fail(code) }
        }
        return session
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
}
