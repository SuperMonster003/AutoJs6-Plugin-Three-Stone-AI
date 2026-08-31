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

internal data class CompletedChatTurn(
    val user: ChatMessage,
    val assistant: ChatMessage,
)

internal data class ContextCompilationPolicy(
    val estimator: ContextTokenEstimator,
    val maximumInputTokens: Long,
    val minimumRecentTurns: Int = ContextPolicy.MINIMUM_RECENT_TURNS,
    val maximumHistoryBytes: Long = ChatConversationPolicy.MAXIMUM_RETAINED_HISTORY_BYTES.toLong(),
    val contextState: ConversationContextState = ConversationContextState.EMPTY,
    val includeDerivedContext: Boolean = true,
    val maximumWorkingMemoryTokens: Long = ContextPolicy.WORKING_MEMORY_MAXIMUM_TOKENS.toLong(),
    val maximumSummaryTokens: Long = ContextPolicy.SUMMARY_LAYER_MAXIMUM_TOKENS.toLong(),
) {
    init {
        require(maximumInputTokens > 0L)
        require(minimumRecentTurns >= 0)
        require(maximumHistoryBytes > 0L)
        require(maximumWorkingMemoryTokens > 0L)
        require(maximumSummaryTokens > 0L)
    }
}

internal data class CompiledContext(
    /** Derived SYSTEM layers followed by complete successful raw history. */
    val messages: List<GenerationMessage>,
    val estimatedInputTokens: Long,
    /** IDs for raw user/assistant messages only; derived layers cite IDs inside their text. */
    val coveredMessageIds: List<Long>,
    val requiresSessionRebuild: Boolean,
    val inputTokenLimit: Long,
    val workingMemoryItemsIncluded: Int = 0,
    val summarySegmentsIncluded: Int = 0,
    val contextFingerprint: String = ConversationContextPolicy.fingerprint(
        ConversationContextState.EMPTY,
    ),
) {
    init {
        require(estimatedInputTokens >= 0L)
        require(coveredMessageIds.distinct().size == coveredMessageIds.size)
        require(inputTokenLimit > 0L)
        require(workingMemoryItemsIncluded >= 0)
        require(summarySegmentsIncluded >= 0)
        require(CONTEXT_FINGERPRINT.matches(contextFingerprint))
    }

    val exceedsInputBudget: Boolean
        get() = estimatedInputTokens > inputTokenLimit

    private companion object {
        val CONTEXT_FINGERPRINT = Regex("^[0-9a-f]{64}$")
    }
}

/** Pure transcript rules shared by the launcher UI and its local unit tests. */
internal object ChatConversationPolicy {
    const val MAXIMUM_INPUT_CHARACTERS = 16_384
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
        val allCompletedTurns = completedTurns(transcript)
        val coveredThrough = policy.contextState.coveredThroughMessageId ?: 0L
        val completedTurns = allCompletedTurns.filter { turn -> turn.assistant.id > coveredThrough }
        val retainedReversed = ArrayList<CompletedChatTurn>()
        var retainedUtf8Bytes = 0L
        for (turn in completedTurns.asReversed().take(policy.minimumRecentTurns)) {
            val turnBytes = turn.utf8Bytes()
            if (saturatedAdd(retainedUtf8Bytes, turnBytes) > policy.maximumHistoryBytes) break
            retainedReversed += turn
            retainedUtf8Bytes = saturatedAdd(retainedUtf8Bytes, turnBytes)
        }

        var retainedMemory = emptyList<MemoryItem>()
        var retainedSummaries = emptyList<SummarySegment>()
        if (policy.includeDerivedContext) {
            ConversationContextFormatter.memoryPriority(policy.contextState.workingMemory)
                .forEach { item ->
                    val candidate = retainedMemory + item
                    val layerMessage = ConversationContextFormatter.workingMemoryMessage(candidate)
                    val layerEstimate = policy.estimator.estimateMessage(layerMessage)
                    if (layerEstimate.estimatedTokens > policy.maximumWorkingMemoryTokens) {
                        return@forEach
                    }
                    if (fits(policy, prompt, listOf(layerMessage), retainedReversed)) {
                        retainedMemory = candidate
                    }
                }
            val selectedReversed = ArrayList<SummarySegment>()
            policy.contextState.summarySegments.asReversed().forEach { segment ->
                val candidateReversed = selectedReversed + segment
                val candidate = candidateReversed.asReversed()
                val layerMessage = ConversationContextFormatter.summaryMessage(candidate)
                val layerEstimate = policy.estimator.estimateMessage(layerMessage)
                if (layerEstimate.estimatedTokens > policy.maximumSummaryTokens) {
                    return@forEach
                }
                val derived = retainedMemory.takeIf { memory -> memory.isNotEmpty() }
                    ?.let { memory ->
                        listOf(ConversationContextFormatter.workingMemoryMessage(memory))
                    }.orEmpty() + layerMessage
                if (fits(policy, prompt, derived, retainedReversed)) {
                    selectedReversed += segment
                    retainedSummaries = candidate
                }
            }
        }
        val derivedMessages = buildList {
            if (retainedMemory.isNotEmpty()) {
                add(ConversationContextFormatter.workingMemoryMessage(retainedMemory))
            }
            if (retainedSummaries.isNotEmpty()) {
                add(ConversationContextFormatter.summaryMessage(retainedSummaries))
            }
        }
        for (turn in completedTurns.asReversed().drop(retainedReversed.size)) {
            val turnBytes = turn.utf8Bytes()
            if (saturatedAdd(retainedUtf8Bytes, turnBytes) > policy.maximumHistoryBytes) break
            val candidate = retainedReversed + turn
            if (!fits(policy, prompt, derivedMessages, candidate)) break
            retainedReversed += turn
            retainedUtf8Bytes = saturatedAdd(retainedUtf8Bytes, turnBytes)
        }
        val retained = retainedReversed.asReversed()
        val rawMessages = retained.flatMap { turn ->
            listOf(
                GenerationMessage(GenerationRole.USER, listOf(turn.user.text)),
                GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistant.text)),
            )
        }
        val messages = derivedMessages + rawMessages
        val coveredMessageIds = retained.flatMap { turn ->
            listOf(turn.user.id, turn.assistant.id)
        }
        val estimate = policy.estimator.estimateMessages(messages + prompt)
        return CompiledContext(
            messages = messages,
            estimatedInputTokens = estimate.estimatedTokens,
            coveredMessageIds = coveredMessageIds,
            requiresSessionRebuild = retained.size != allCompletedTurns.size,
            inputTokenLimit = policy.maximumInputTokens,
            workingMemoryItemsIncluded = retainedMemory.size,
            summarySegmentsIncluded = retainedSummaries.size,
            contextFingerprint = ConversationContextPolicy.fingerprint(policy.contextState),
        )
    }

    private fun fits(
        policy: ContextCompilationPolicy,
        prompt: GenerationMessage,
        derivedMessages: List<GenerationMessage>,
        retainedReversed: List<CompletedChatTurn>,
    ): Boolean {
        val rawMessages = retainedReversed.asReversed().flatMap { turn ->
            listOf(
                GenerationMessage(GenerationRole.USER, listOf(turn.user.text)),
                GenerationMessage(GenerationRole.ASSISTANT, listOf(turn.assistant.text)),
            )
        }
        val history = derivedMessages + rawMessages
        val historyEstimate = policy.estimator.estimateMessages(history)
        if (historyEstimate.utf8Bytes > policy.maximumHistoryBytes) return false
        return policy.estimator.estimateMessages(history + prompt).estimatedTokens <=
            policy.maximumInputTokens
    }

    private fun CompletedChatTurn.utf8Bytes(): Long = saturatedAdd(
        user.text.toByteArray(Charsets.UTF_8).size.toLong(),
        assistant.text.toByteArray(Charsets.UTF_8).size.toLong(),
    )

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

    fun completedTurns(messages: List<ChatMessage>): List<CompletedChatTurn> {
        val turns = ArrayList<CompletedChatTurn>()
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
                        turns += CompletedChatTurn(user, message)
                    }
                    pendingUser = null
                }
                ChatMessageRole.NOTICE -> Unit
            }
        }
        return turns
    }

}
