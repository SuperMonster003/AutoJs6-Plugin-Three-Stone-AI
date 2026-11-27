package io.github.supermonster003.autojs6.plugin.threestoneai.speech

import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechOptionKeys

/** How a live dictation session ends its listening (roadmap D204, D209). */
internal enum class LiveDictationMode {
    /** The microphone button: several sentences in a row, until the button is pressed again or 15 s pass in silence. */
    CONTINUOUS,

    /** A voice conversation round (P6-3): the first `final` finishes the session; 8 s without speech end it. */
    SINGLE_UTTERANCE,
}

/** The `openSession` request of a live dictation session (roadmap D209); plain values, no Android types. */
internal object LiveDictationRequest {
    const val SILENCE_END_MILLIS = 1_200L
    const val CONTINUOUS_TIMEOUT_MILLIS = 15_000L
    const val SINGLE_UTTERANCE_TIMEOUT_MILLIS = 8_000L
    const val MAX_DURATION_MILLIS = 300_000L

    /**
     * Version 1 options only, so 3-Macaw Speech 1.0.0 answers them too; no language: the recognition model decides
     * (SenseVoice detects it, the bilingual models ignore it).
     */
    fun options(mode: LiveDictationMode): Map<String, Any> = linkedMapOf(
        SpeechContract.KEY_CONTRACT_VERSION to SpeechContract.CALL_VERSION_1,
        SpeechOptionKeys.SOURCE to SpeechContract.SOURCE_MIC,
        SpeechOptionKeys.VAD to true,
        SpeechOptionKeys.SILENCE_END_MILLIS to SILENCE_END_MILLIS,
        SpeechOptionKeys.TIMEOUT_MILLIS to when (mode) {
            LiveDictationMode.CONTINUOUS -> CONTINUOUS_TIMEOUT_MILLIS
            LiveDictationMode.SINGLE_UTTERANCE -> SINGLE_UTTERANCE_TIMEOUT_MILLIS
        },
        SpeechOptionKeys.MAX_DURATION_MILLIS to MAX_DURATION_MILLIS,
        SpeechOptionKeys.PUNCTUATION to true,
    )
}

/** How a live dictation session ended; exactly one per session. */
internal sealed interface LiveDictationEnd {
    /** `finish` (the button pressed again, or the first `final` of a single utterance) flushed and ended it. */
    data object Stopped : LiveDictationEnd

    /** No speech or new text for the timeout. [heard] tells whether anything was recognized before. */
    data class Timeout(val heard: Boolean) : LiveDictationEnd

    /** Five minutes of audio. */
    data object MaxDuration : LiveDictationEnd

    /** `cancel` (the chat left the screen, a send, another conversation) or 3-Macaw Speech cancelled it. */
    data object Cancelled : LiveDictationEnd

    /**
     * [code] is a `SpeechErrorCodes` value ([SpeechErrorCodes.INTERNAL] when unknown); [heard] tells whether a
     * partial or final arrived before.
     */
    data class Failed(val code: String, val heard: Boolean) : LiveDictationEnd
}

/** Events of one live dictation session, on the main thread. */
internal interface LiveDictationListener {
    fun onReady() = Unit

    /** The current utterance so far; it replaces the previous partial. */
    fun onPartial(text: String)

    /** The utterance is final. */
    fun onFinal(text: String)

    fun onEnd(end: LiveDictationEnd)
}

/** A running live dictation session. Both calls are idempotent and may come after the end. */
internal interface LiveDictationSession {
    /** Ends listening: the pending utterance becomes a last `final`, then [LiveDictationEnd.Stopped]. */
    fun finish()

    /** Ends at once with [LiveDictationEnd.Cancelled]; nothing else is delivered. */
    fun cancel()
}

/** Opens live dictation sessions; 3-Macaw Speech in the app, a scripted fake in instrumentation tests. */
internal interface LiveDictationSource {
    fun open(mode: LiveDictationMode, listener: LiveDictationListener): LiveDictationSession
}

/**
 * Normalizes the callbacks of one session for [listener] (roadmap D209): nothing after the end, exactly one end,
 * a local [cancel] ends at once, and in [LiveDictationMode.SINGLE_UTTERANCE] the first `final` calls [finishSession]
 * and later text is dropped. Error codes reported before `end` decide the [LiveDictationEnd.Failed] code. Pure, so the
 * rules have unit tests; the caller delivers on one thread.
 */
internal class LiveDictationEventGate(
    val mode: LiveDictationMode,
    private val listener: LiveDictationListener,
    private val finishSession: () -> Unit,
) {
    var ended = false
        private set
    private var finishing = false
    private var heard = false
    private var finals = 0
    private var errorCode: String? = null

    fun ready() {
        if (!ended) listener.onReady()
    }

    fun partial(text: String) {
        if (ended || closedForText()) return
        heard = true
        listener.onPartial(text)
    }

    fun final(text: String) {
        if (ended || closedForText()) return
        heard = true
        finals++
        listener.onFinal(text)
        if (mode == LiveDictationMode.SINGLE_UTTERANCE) requestFinish()
    }

    /** An `error` event or a failed call; the following [end] reports it. */
    fun error(code: String?) {
        if (errorCode == null) errorCode = code?.takeIf(String::isNotEmpty) ?: SpeechErrorCodes.INTERNAL
    }

    /** The session's `end` with its `reason`, or the death of the plugin (`error`). */
    fun end(reason: String?) {
        if (ended) return
        ended = true
        listener.onEnd(
            when (reason) {
                SpeechContract.REASON_STOPPED -> LiveDictationEnd.Stopped
                SpeechContract.REASON_TIMEOUT -> LiveDictationEnd.Timeout(heard)
                SpeechContract.REASON_MAX_DURATION -> LiveDictationEnd.MaxDuration
                SpeechContract.REASON_CANCELLED -> errorCode?.let { code -> LiveDictationEnd.Failed(code, heard) }
                    ?: LiveDictationEnd.Cancelled
                else -> LiveDictationEnd.Failed(errorCode ?: SpeechErrorCodes.INTERNAL, heard)
            },
        )
    }

    /** Fails the session at once, for instance when it cannot be opened or the plugin died. */
    fun fail(code: String?) {
        error(code)
        end(SpeechContract.REASON_ERROR)
    }

    /** A local cancel: the end is reported now, whatever the plugin still sends. */
    fun cancel() {
        if (ended) return
        ended = true
        listener.onEnd(LiveDictationEnd.Cancelled)
    }

    /** A local finish; returns whether the plugin should be asked to finish. */
    fun requestFinish(): Boolean {
        if (ended || finishing) return false
        finishing = true
        finishSession()
        return true
    }

    private fun closedForText() = mode == LiveDictationMode.SINGLE_UTTERANCE && finals > 0
}

/** What the chat does after a live dictation failed (roadmap D208, D209). */
internal enum class LiveDictationRecovery {
    /** Open the recognizer sheet of P5 once instead (3-Macaw Speech's sheet takes over a wake session, guides models). */
    SHEET,

    /** Open 3-Macaw Speech's microphone permission screen. */
    MICROPHONE_PERMISSION,

    /** Keep what was inserted and report the failure. */
    REPORT,
}

internal object LiveDictationRecoveryPolicy {
    /** Failures that the sheet can answer, as long as nothing was heard (an unusable service included, D208). */
    val SHEET_CODES = setOf(
        SpeechErrorCodes.SESSION_BUSY,
        SpeechErrorCodes.MODEL_MISSING,
        SpeechErrorCodes.SESSION_CLOSED,
        SpeechErrorCodes.PLUGIN_UNAVAILABLE,
        SpeechErrorCodes.PLUGIN_DISABLED,
        SpeechErrorCodes.PLUGIN_INCOMPATIBLE,
        SpeechErrorCodes.FEATURE_UNAVAILABLE,
    )

    fun recovery(failure: LiveDictationEnd.Failed): LiveDictationRecovery = when {
        failure.heard -> LiveDictationRecovery.REPORT
        failure.code == SpeechErrorCodes.PERMISSION_DENIED -> LiveDictationRecovery.MICROPHONE_PERMISSION
        failure.code in SHEET_CODES -> LiveDictationRecovery.SHEET
        else -> LiveDictationRecovery.REPORT
    }
}
