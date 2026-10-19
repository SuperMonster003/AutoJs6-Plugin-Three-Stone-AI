package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** What happened during a voice conversation (roadmap D13). */
internal sealed interface VoiceEvent {
    /** Long press on the microphone. */
    data object Start : VoiceEvent

    data class Recognized(val text: String) : VoiceEvent

    data object RecognitionCanceled : VoiceEvent

    data object RecognitionNoMatch : VoiceEvent

    data class RecognitionFailed(val kind: DictationFailure) : VoiceEvent

    /** The recognized text could not be sent, for example because the target is unavailable. */
    data object SendRejected : VoiceEvent

    /** The reply's first sentence started to sound. */
    data object ReadingStarted : VoiceEvent

    /** The reply finished and so did its reading, or there was nothing or no engine to read it. */
    data object ReplyDone : VoiceEvent

    data object GenerationFailed : VoiceEvent

    /** The user pressed stop while the reply was generating. */
    data object GenerationStopped : VoiceEvent

    /** Another app took the audio focus or the engine failed. */
    data object ReadingInterrupted : VoiceEvent

    /** Tap on the status bar. */
    data object Interrupt : VoiceEvent

    /** The status bar's exit button. */
    data object Exit : VoiceEvent

    /** The chat went to the background for anything but the recognizer. */
    data object LeftChat : VoiceEvent

    /** A typed message was sent, or the conversation was changed, cleared or edited. */
    data object ConversationChanged : VoiceEvent
}

internal sealed interface VoiceEffect {
    data object CancelEditing : VoiceEffect

    /** Keeps what was generated as a stopped turn. */
    data object StopGeneration : VoiceEffect

    data object StopReading : VoiceEffect

    /** Opens the recognizer with the "say your next message" prompt. */
    data object Listen : VoiceEffect

    data class Send(val text: String) : VoiceEffect

    data class ReportFailure(val kind: DictationFailure) : VoiceEffect
}

/** The chat state the machine needs when a conversation starts or is interrupted. */
internal data class VoiceSituation(
    val editing: Boolean,
    val generating: Boolean,
)

internal data class VoiceTransition(
    val phase: VoicePhase,
    val effects: List<VoiceEffect> = emptyList(),
)

/**
 * Voice conversation mode (roadmap D13): listen, send the result, read the reply while it
 * streams, listen again when the reading is over. Canceling or silence in the recognizer, any
 * recognizer error, a failed or stopped reply, an interrupted reading, leaving the chat and the
 * exit button end it. While a reply is read, a tap on the status bar stops the reply and listens at
 * once; the next recognizer only opens after the reading finished, so it never hears the reply.
 */
internal object VoiceConversationMachine {
    fun next(phase: VoicePhase, event: VoiceEvent, situation: VoiceSituation): VoiceTransition {
        val unchanged = VoiceTransition(phase)
        if (phase == VoicePhase.IDLE) {
            if (event != VoiceEvent.Start) return unchanged
            return VoiceTransition(
                VoicePhase.LISTENING,
                buildList {
                    if (situation.editing) add(VoiceEffect.CancelEditing)
                    if (situation.generating) add(VoiceEffect.StopGeneration)
                    add(VoiceEffect.StopReading)
                    add(VoiceEffect.Listen)
                },
            )
        }
        return when (event) {
            VoiceEvent.Start -> unchanged
            VoiceEvent.Exit,
            VoiceEvent.LeftChat,
            VoiceEvent.ConversationChanged,
            -> VoiceTransition(VoicePhase.IDLE, listOf(VoiceEffect.StopReading))
            is VoiceEvent.Recognized -> if (phase == VoicePhase.LISTENING) {
                VoiceTransition(VoicePhase.THINKING, listOf(VoiceEffect.Send(event.text)))
            } else {
                unchanged
            }
            VoiceEvent.RecognitionCanceled,
            VoiceEvent.RecognitionNoMatch,
            -> if (phase == VoicePhase.LISTENING) VoiceTransition(VoicePhase.IDLE) else unchanged
            is VoiceEvent.RecognitionFailed -> if (phase == VoicePhase.LISTENING) {
                VoiceTransition(VoicePhase.IDLE, listOf(VoiceEffect.ReportFailure(event.kind)))
            } else {
                unchanged
            }
            VoiceEvent.SendRejected -> if (phase == VoicePhase.THINKING) VoiceTransition(VoicePhase.IDLE) else unchanged
            VoiceEvent.ReadingStarted -> if (phase == VoicePhase.THINKING) VoiceTransition(VoicePhase.SPEAKING) else unchanged
            VoiceEvent.ReplyDone -> if (phase.answering()) {
                VoiceTransition(VoicePhase.LISTENING, listOf(VoiceEffect.Listen))
            } else {
                unchanged
            }
            VoiceEvent.GenerationFailed,
            VoiceEvent.GenerationStopped,
            -> if (phase.answering()) {
                VoiceTransition(VoicePhase.IDLE, listOf(VoiceEffect.StopReading))
            } else {
                unchanged
            }
            VoiceEvent.ReadingInterrupted -> if (phase.answering()) VoiceTransition(VoicePhase.IDLE) else unchanged
            VoiceEvent.Interrupt -> if (phase == VoicePhase.SPEAKING) {
                VoiceTransition(
                    VoicePhase.LISTENING,
                    buildList {
                        if (situation.generating) add(VoiceEffect.StopGeneration)
                        add(VoiceEffect.StopReading)
                        add(VoiceEffect.Listen)
                    },
                )
            } else {
                unchanged
            }
        }
    }

    private fun VoicePhase.answering() = this == VoicePhase.THINKING || this == VoicePhase.SPEAKING
}
