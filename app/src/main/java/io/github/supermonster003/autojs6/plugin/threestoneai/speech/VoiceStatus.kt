package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** Where a voice conversation stands (roadmap D13). */
internal enum class VoicePhase {
    IDLE,

    /** The recognizer is open. */
    LISTENING,

    /** The reply is generating and nothing sounds yet. */
    THINKING,

    /** The reply is being read. */
    SPEAKING,
}

internal enum class VoiceStatusLabel {
    READING,

    /** The round's session is open but the microphone has not started yet (A4, D225). */
    PREPARING,
    LISTENING,
    THINKING,
    SPEAKING,
}

internal enum class VoiceStatusAction {
    STOP_READING,
    EXIT_CONVERSATION,
}

/** The status bar above the composer (D13, D18). */
internal data class VoiceStatus(
    val label: VoiceStatusLabel,
    val action: VoiceStatusAction,
    /** Tapping the bar stops the reading and listens for the next sentence at once. */
    val tapInterrupts: Boolean,
)

internal object VoiceStatusPolicy {
    /** [preparing]: the round listens through a session that has not reported ready yet (A4, D225). */
    fun status(phase: VoicePhase, reading: Boolean, preparing: Boolean = false): VoiceStatus? = when (phase) {
        VoicePhase.IDLE -> if (reading) {
            VoiceStatus(VoiceStatusLabel.READING, VoiceStatusAction.STOP_READING, tapInterrupts = false)
        } else {
            null
        }
        VoicePhase.LISTENING -> VoiceStatus(
            if (preparing) VoiceStatusLabel.PREPARING else VoiceStatusLabel.LISTENING,
            VoiceStatusAction.EXIT_CONVERSATION,
            tapInterrupts = false,
        )
        VoicePhase.THINKING ->
            VoiceStatus(VoiceStatusLabel.THINKING, VoiceStatusAction.EXIT_CONVERSATION, tapInterrupts = false)
        VoicePhase.SPEAKING ->
            VoiceStatus(VoiceStatusLabel.SPEAKING, VoiceStatusAction.EXIT_CONVERSATION, tapInterrupts = true)
    }
}
