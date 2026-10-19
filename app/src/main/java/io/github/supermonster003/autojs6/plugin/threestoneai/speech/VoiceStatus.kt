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
    fun status(phase: VoicePhase, reading: Boolean): VoiceStatus? = when (phase) {
        VoicePhase.IDLE -> if (reading) {
            VoiceStatus(VoiceStatusLabel.READING, VoiceStatusAction.STOP_READING, tapInterrupts = false)
        } else {
            null
        }
        VoicePhase.LISTENING ->
            VoiceStatus(VoiceStatusLabel.LISTENING, VoiceStatusAction.EXIT_CONVERSATION, tapInterrupts = false)
        VoicePhase.THINKING ->
            VoiceStatus(VoiceStatusLabel.THINKING, VoiceStatusAction.EXIT_CONVERSATION, tapInterrupts = false)
        VoicePhase.SPEAKING ->
            VoiceStatus(VoiceStatusLabel.SPEAKING, VoiceStatusAction.EXIT_CONVERSATION, tapInterrupts = true)
    }
}
