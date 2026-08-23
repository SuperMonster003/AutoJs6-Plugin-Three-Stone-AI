package io.github.supermonster003.autojs6.plugin.ondeviceai

import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.ondeviceai.backend.GenerationRole

internal enum class ChatMessageRole {
    USER,
    ASSISTANT,
    NOTICE,
}

internal enum class ChatMessageStatus {
    COMPLETE,
    GENERATING,
    STOPPED,
    FAILED,
}

internal data class ChatMessageUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val durationMillis: Long,
) {
    init {
        require(inputTokens >= 0L)
        require(outputTokens >= 0L)
        require(durationMillis >= 0L)
    }
}

internal data class ChatMessage(
    val id: Long,
    val role: ChatMessageRole,
    val text: String,
    val status: ChatMessageStatus = ChatMessageStatus.COMPLETE,
    val usage: ChatMessageUsage? = null,
) {
    init {
        require(id > 0L)
        require(role == ChatMessageRole.ASSISTANT || status == ChatMessageStatus.COMPLETE) {
            "Only assistant messages may have a non-complete status"
        }
        require(status == ChatMessageStatus.COMPLETE || usage == null) {
            "Only complete messages may carry generation usage"
        }
    }
}

/** Pure transcript rules shared by the launcher UI and its local unit tests. */
internal object ChatConversationPolicy {
    const val MAXIMUM_INPUT_CHARACTERS = 16_384
    const val MAXIMUM_BACKEND_TURNS = 32
    const val MAXIMUM_RETAINED_TURNS = 24
    const val MAXIMUM_RETAINED_HISTORY_BYTES = 192 * 1_024

    /**
     * Builds a contiguous suffix of successful user/assistant turns for a new native Conversation.
     * Interrupted and failed turns stay visible in the UI but never contaminate later model context.
     */
    fun historyForFreshBackend(messages: List<ChatMessage>): List<GenerationMessage> {
        val completedTurns = completedTurns(messages)
        val retainedReversed = ArrayList<CompletedTurn>(MAXIMUM_RETAINED_TURNS)
        var retainedBytes = 0
        for (turn in completedTurns.asReversed()) {
            if (retainedReversed.size >= MAXIMUM_RETAINED_TURNS) break
            val turnBytes = turn.user.text.toByteArray(Charsets.UTF_8).size +
                turn.assistant.text.toByteArray(Charsets.UTF_8).size
            if (retainedBytes + turnBytes > MAXIMUM_RETAINED_HISTORY_BYTES) break
            retainedReversed += turn
            retainedBytes += turnBytes
        }
        return retainedReversed.asReversed().flatMap { turn ->
            listOf(
                GenerationMessage(GenerationRole.USER, listOf(turn.user.text)),
                GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistant.text)),
            )
        }
    }

    fun shouldRotateBackend(completedTurnsOnBackend: Int): Boolean {
        require(completedTurnsOnBackend >= 0)
        return completedTurnsOnBackend >= MAXIMUM_BACKEND_TURNS
    }

    /** A recreated Activity cannot retain a native generation callback, so make that state honest. */
    fun restore(messages: List<ChatMessage>): List<ChatMessage> = messages.map { message ->
        if (message.status == ChatMessageStatus.GENERATING) {
            message.copy(status = ChatMessageStatus.STOPPED)
        } else {
            message
        }
    }

    private fun completedTurns(messages: List<ChatMessage>): List<CompletedTurn> {
        val turns = ArrayList<CompletedTurn>()
        var pendingUser: ChatMessage? = null
        messages.forEach { message ->
            when (message.role) {
                ChatMessageRole.USER -> pendingUser = message
                ChatMessageRole.ASSISTANT -> {
                    val user = pendingUser
                    if (
                        user != null && message.status == ChatMessageStatus.COMPLETE &&
                        message.text.isNotBlank()
                    ) {
                        turns += CompletedTurn(user, message)
                    }
                    pendingUser = null
                }
                ChatMessageRole.NOTICE -> Unit
            }
        }
        return turns
    }

    private data class CompletedTurn(
        val user: ChatMessage,
        val assistant: ChatMessage,
    )
}
