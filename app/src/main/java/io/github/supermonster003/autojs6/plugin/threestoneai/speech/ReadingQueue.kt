package io.github.supermonster003.autojs6.plugin.threestoneai.speech

/** One sentence handed to the engine. */
internal data class Utterance(val id: String, val text: String)

/**
 * Bookkeeping for the reply being read (roadmap D24). Utterance IDs are
 * `<session>:<messageId>:<sentenceIndex>`; the session number keeps callbacks that arrive after a
 * stop from touching a newer reading of the same message. Reading is over when the input has ended
 * and every queued utterance finished or failed.
 */
internal class ReadingQueue {
    var messageId: Long? = null
        private set

    private var session = 0L
    private var nextIndex = 0
    private val pending = LinkedHashSet<String>()
    private var inputEnded = false

    val active: Boolean get() = messageId != null

    fun begin(messageId: Long) {
        session = if (session == Long.MAX_VALUE) 1L else session + 1L
        this.messageId = messageId
        nextIndex = 0
        pending.clear()
        inputEnded = false
    }

    fun enqueue(sentences: List<String>): List<Utterance> {
        val message = messageId ?: return emptyList()
        if (inputEnded) return emptyList()
        return sentences.filter(String::isNotBlank).map { text ->
            Utterance("$session:$message:${nextIndex++}", text).also { pending += it.id }
        }
    }

    /** Ends the input; true when nothing is left to read, so reading finished now. */
    fun endInput(): Boolean {
        if (!active) return false
        inputEnded = true
        return finishIfDone()
    }

    /** An utterance finished or failed; true when this completed the reading. */
    fun completed(utteranceId: String): Boolean {
        if (!pending.remove(utteranceId)) return false
        return finishIfDone()
    }

    fun owns(utteranceId: String): Boolean = utteranceId in pending

    /** Forgets the current reading; returns the message that was being read. */
    fun clear(): Long? = messageId.also {
        messageId = null
        pending.clear()
        inputEnded = false
    }

    private fun finishIfDone(): Boolean {
        if (!inputEnded || pending.isNotEmpty()) return false
        messageId = null
        return true
    }
}
