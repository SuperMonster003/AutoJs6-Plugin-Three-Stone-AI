package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** Why the chat started a recognizer; the result is handled accordingly. */
internal enum class DictationMode {
    /** Short press on the microphone: insert at the cursor, never send (roadmap D9). */
    SINGLE,

    /** A voice conversation round: the result is sent at once (roadmap D13). */
    CONVERSATION,
}
