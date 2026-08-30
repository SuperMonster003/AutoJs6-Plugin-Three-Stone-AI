package io.github.supermonster003.autojs6.plugin.threestoneai

import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationMessage
import io.github.supermonster003.autojs6.plugin.threestoneai.backend.GenerationRole

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
    val target: ConversationTargetSnapshot? = null,
) {
    init {
        require(id > 0L)
        require(role == ChatMessageRole.ASSISTANT || status == ChatMessageStatus.COMPLETE) {
            "Only assistant messages may have a non-complete status"
        }
        require(status == ChatMessageStatus.COMPLETE || usage == null) {
            "Only complete messages may carry generation usage"
        }
        require((role == ChatMessageRole.ASSISTANT) == (target != null)) {
            "Every assistant message, and only an assistant message, must carry its actual target"
        }
    }
}

internal data class ContextCompilationPolicy(
    val estimator: ContextTokenEstimator,
    val maximumInputTokens: Long,
    val minimumRecentTurns: Int = ContextPolicy.MINIMUM_RECENT_TURNS,
    val maximumHistoryBytes: Long = ChatConversationPolicy.MAXIMUM_RETAINED_HISTORY_BYTES.toLong(),
) {
    init {
        require(maximumInputTokens > 0L)
        require(minimumRecentTurns >= 0)
        require(maximumHistoryBytes > 0L)
    }
}

internal data class CompiledContext(
    /** Complete successful history only. The current prompt remains a separate request field. */
    val messages: List<GenerationMessage>,
    val estimatedInputTokens: Long,
    val coveredMessageIds: List<Long>,
    val requiresSessionRebuild: Boolean,
    val inputTokenLimit: Long,
) {
    init {
        require(estimatedInputTokens >= 0L)
        require(coveredMessageIds.size == messages.size)
        require(coveredMessageIds.distinct().size == coveredMessageIds.size)
        require(inputTokenLimit > 0L)
    }

    val exceedsInputBudget: Boolean
        get() = estimatedInputTokens > inputTokenLimit
}

/** Pure transcript rules shared by the launcher UI and its local unit tests. */
internal object ChatConversationPolicy {
    const val MAXIMUM_INPUT_CHARACTERS = 16_384
    const val MAXIMUM_BACKEND_TURNS = 32
    const val MAXIMUM_RETAINED_TURNS = 24
    const val MAXIMUM_RETAINED_HISTORY_BYTES = 192 * 1_024

    /**
     * Packs a contiguous suffix of complete user/assistant turns without splitting a pair.
     *
     * The token limit is a context policy, while [ContextCompilationPolicy.maximumHistoryBytes]
     * remains a transport guard. The current prompt is always counted in full and the newest
     * minimum turn floor may deliberately exceed the token limit; callers surface that as a
     * warning instead of truncating user text.
     */
    fun compileContext(
        transcript: List<ChatMessage>,
        prompt: GenerationMessage,
        policy: ContextCompilationPolicy,
    ): CompiledContext {
        require(prompt.role == GenerationRole.USER) {
            "The compiled context prompt must be a user message"
        }
        val completedTurns = completedTurns(transcript)
        val promptEstimate = policy.estimator.estimateMessage(prompt)
        val retainedReversed = ArrayList<CompletedTurn>()
        var retainedUtf8Bytes = 0L
        var retainedMessageCount = 0
        for (turn in completedTurns.asReversed()) {
            val turnBytes = saturatedAdd(
                turn.user.text.toByteArray(Charsets.UTF_8).size.toLong(),
                turn.assistant.text.toByteArray(Charsets.UTF_8).size.toLong(),
            )
            if (saturatedAdd(retainedUtf8Bytes, turnBytes) > policy.maximumHistoryBytes) break
            val candidateBytes = saturatedAdd(
                promptEstimate.utf8Bytes,
                saturatedAdd(retainedUtf8Bytes, turnBytes),
            )
            val candidateMessages = promptEstimate.messageCount + retainedMessageCount + 2
            val candidateTokens = policy.estimator
                .estimatePayload(candidateBytes, candidateMessages)
                .estimatedTokens
            if (
                retainedReversed.size >= policy.minimumRecentTurns &&
                candidateTokens > policy.maximumInputTokens
            ) {
                break
            }
            retainedReversed += turn
            retainedUtf8Bytes = saturatedAdd(retainedUtf8Bytes, turnBytes)
            retainedMessageCount += 2
        }
        val retained = retainedReversed.asReversed()
        val messages = retained.flatMap { turn ->
            listOf(
                GenerationMessage(GenerationRole.USER, listOf(turn.user.text)),
                GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistant.text)),
            )
        }
        val coveredMessageIds = retained.flatMap { turn ->
            listOf(turn.user.id, turn.assistant.id)
        }
        val estimate = policy.estimator.estimateMessages(messages + prompt)
        return CompiledContext(
            messages = messages,
            estimatedInputTokens = estimate.estimatedTokens,
            coveredMessageIds = coveredMessageIds,
            requiresSessionRebuild = retained.size != completedTurns.size,
            inputTokenLimit = policy.maximumInputTokens,
        )
    }

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

    /** Cumulative provider input through one visible message, saturated for hostile counters. */
    fun cumulativeInputTokensThrough(messages: List<ChatMessage>, messageId: Long): Long {
        var total = 0L
        messages.forEach { message ->
            message.usage?.let { usage -> total = saturatedAdd(total, usage.inputTokens) }
            if (message.id == messageId) return total
        }
        return 0L
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
