package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** The read-aloud entry in a reply's message menu (roadmap D10, D18). */
internal enum class ReadAloudMenuItem {
    READ,
    STOP,
}

/** What a reply looks like to the read-aloud rules, without the chat's message types. */
internal data class ReadableReply(
    val messageId: Long,
    val isAssistant: Boolean,
    val complete: Boolean,
    val text: String,
)

internal object ReadAloudPolicy {
    /**
     * "Read aloud" for a finished, non-empty reply; "Stop reading" for the reply being read
     * (also while it still streams); nothing when no engine is installed (D8).
     */
    fun menuItem(reply: ReadableReply, readingMessageId: Long?, engineAvailable: Boolean): ReadAloudMenuItem? = when {
        !engineAvailable || !reply.isAssistant -> null
        readingMessageId == reply.messageId -> ReadAloudMenuItem.STOP
        reply.complete && reply.text.isNotBlank() -> ReadAloudMenuItem.READ
        else -> null
    }

    /**
     * Whether a reply that starts now is read while it streams: the automatic setting (D10), every
     * turn of a voice conversation (D13) and the turn answering a voice command (D36).
     */
    fun readsNewReply(
        autoRead: Boolean,
        voiceConversation: Boolean,
        voiceCommandTurn: Boolean,
        engineAvailable: Boolean,
    ): Boolean = engineAvailable && (autoRead || voiceConversation || voiceCommandTurn)

    /**
     * Engine languages to try in order (D19, D36): a valid voice-command language first, then the
     * interface language; when none is supported the engine keeps its default voice.
     */
    fun languageTags(interfaceTag: String, voiceCommandTag: String?): List<String> =
        listOfNotNull(voiceCommandTag, interfaceTag).distinct()
}
