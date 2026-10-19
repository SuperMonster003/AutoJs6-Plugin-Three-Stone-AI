package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** What a `RECOGNIZE_SPEECH` activity returned (roadmap D23). */
internal sealed interface DictationOutcome {
    data class Recognized(val text: String) : DictationOutcome

    /** The user closed the recognizer (`RESULT_CANCELED`). */
    data object Canceled : DictationOutcome

    /** No speech was heard (`RESULT_NO_MATCH`, or success without any text). */
    data object NoMatch : DictationOutcome

    data class Failed(val kind: DictationFailure) : DictationOutcome
}

internal enum class DictationFailure {
    NETWORK,
    AUDIO,
    OTHER,
}

/**
 * Maps recognizer result codes without Android types. The codes are the public constants of
 * `android.app.Activity` and `android.speech.RecognizerIntent`.
 */
internal object DictationOutcomePolicy {
    const val RESULT_OK = -1
    const val RESULT_CANCELED = 0
    const val RESULT_NO_MATCH = 1
    const val RESULT_CLIENT_ERROR = 2
    const val RESULT_SERVER_ERROR = 3
    const val RESULT_NETWORK_ERROR = 4
    const val RESULT_AUDIO_ERROR = 5

    fun classify(resultCode: Int, results: List<String?>?): DictationOutcome = when (resultCode) {
        RESULT_OK -> results?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
            ?.let(DictationOutcome::Recognized)
            ?: DictationOutcome.NoMatch
        RESULT_CANCELED -> DictationOutcome.Canceled
        RESULT_NO_MATCH -> DictationOutcome.NoMatch
        RESULT_NETWORK_ERROR -> DictationOutcome.Failed(DictationFailure.NETWORK)
        RESULT_AUDIO_ERROR -> DictationOutcome.Failed(DictationFailure.AUDIO)
        else -> DictationOutcome.Failed(DictationFailure.OTHER)
    }

    /** Single dictation stays quiet when the user cancels or says nothing; failures are reported once. */
    fun reportsSingleDictation(outcome: DictationOutcome): Boolean = outcome is DictationOutcome.Failed
}
