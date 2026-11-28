package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** What one voice conversation round on 3-Macaw Speech's session leads to (roadmap P6-3, D209, D220). */
internal sealed interface LiveVoiceRound {
    /**
     * [event] goes to [VoiceConversationMachine]. [keepText] keeps the round's text shown in the input box as ordinary
     * text, because nothing was sent (as D215); otherwise the text leaves the input box and is sent as the voice message.
     */
    data class Ended(val event: VoiceEvent, val keepText: Boolean) : LiveVoiceRound

    /** Nothing was heard and the recognizer sheet can answer (busy microphone, missing model): this round opens it (D209). */
    data object Sheet : LiveVoiceRound

    /** 3-Macaw Speech may not use the microphone: the conversation ends and its permission screen opens (D209). */
    data object MicrophonePermission : LiveVoiceRound
}

/**
 * Maps the end of a [LiveDictationMode.SINGLE_UTTERANCE] session to the voice conversation (roadmap P6-3, D209, D220).
 * The round's text is sent whenever it has one, unless the session was cancelled; without text, silence and a finish
 * end the conversation as "no speech", a cancel as "canceled", and a failure follows [LiveDictationRecoveryPolicy].
 * Pure, for unit tests.
 */
internal object LiveVoiceRoundPolicy {
    /** [text] is the round's last `final` that was not blank, or null. */
    fun outcome(end: LiveDictationEnd, text: String?): LiveVoiceRound {
        val said = text?.trim()?.takeIf(String::isNotEmpty)
        return when (end) {
            LiveDictationEnd.Cancelled -> LiveVoiceRound.Ended(VoiceEvent.RecognitionCanceled, keepText = true)
            // A final that came before the failure is a whole sentence: it is sent like any other.
            is LiveDictationEnd.Failed -> if (said != null) {
                sent(said)
            } else {
                when (LiveDictationRecoveryPolicy.recovery(end)) {
                    LiveDictationRecovery.SHEET -> LiveVoiceRound.Sheet
                    LiveDictationRecovery.MICROPHONE_PERMISSION -> LiveVoiceRound.MicrophonePermission
                    LiveDictationRecovery.REPORT ->
                        LiveVoiceRound.Ended(VoiceEvent.RecognitionFailed(DictationFailure.OTHER), keepText = true)
                }
            }
            LiveDictationEnd.Stopped,
            is LiveDictationEnd.Timeout,
            LiveDictationEnd.MaxDuration,
            -> if (said != null) sent(said) else LiveVoiceRound.Ended(VoiceEvent.RecognitionNoMatch, keepText = true)
        }
    }

    private fun sent(text: String) = LiveVoiceRound.Ended(VoiceEvent.Recognized(text), keepText = false)
}
